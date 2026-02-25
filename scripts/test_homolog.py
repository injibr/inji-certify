#!/usr/bin/env python3
"""
Gov.br SSO Login + Inji Certify Credential Issuance — Homolog Environment
==========================================================================

Same OID4VCI flow as govbr_token.py, but targets the homolog deployment
and issues one credential per issuer (3 total):

  - MGI   → ECACredential   (Dataprev age verification)
  - INCRA → CCIRCredential  (SERPRO/Dataprev land certificate)
  - MDA   → CAFCredential   (CAF family farming registry)

Usage:
    python3 scripts/govbr_token_homolog.py [--skip-login]

    --skip-login  Skip the SSO login flow and reuse an existing token
                  from the ACCESS_TOKEN environment variable.

Environment variables:
    SSO_CLIENT_ID       (required) OAuth2 client ID registered with Gov.br SSO
    SSO_CLIENT_SECRET   (required) OAuth2 client secret
    ACCESS_TOKEN        (optional) Pre-existing access token (with --skip-login)
    CERTIFY_URL         (optional) Certify base URL
                        default: https://injicertify.credenciaisverificaveis-hml.dataprev.gov.br/v1/certify
    CERTIFY_IDENTIFIER  (optional) Certify identifier used as proof JWT audience
                        default: https://injicertify.credenciaisverificaveis-hml.dataprev.gov.br

Dependencies:
    - Python 3.8+ (standard library only for the SSO flow)
    - `cryptography` library (optional, for proof JWT generation)
    - Falls back to `openssl` CLI if `cryptography` is not installed
"""

import base64
import hashlib
import http.server
import json
import os
import secrets
import sys
import time
import urllib.parse
import urllib.request
import webbrowser

SSO_URL = "https://sso.staging.acesso.gov.br"
REDIRECT_PORT = 3004
REDIRECT_URI = f"http://localhost:{REDIRECT_PORT}/redirect"

TOKEN_CACHE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".token_cache.json")

DEFAULT_CERTIFY_URL = "https://injicertify.credenciaisverificaveis-hml.dataprev.gov.br/v1/certify"
DEFAULT_CERTIFY_IDENTIFIER = "https://injicertify.credenciaisverificaveis-hml.dataprev.gov.br"

# Credential types to issue, one per issuer
CREDENTIALS = [
    {"issuer_id": "MGI",   "doc_type": "ECACredential"},
    {"issuer_id": "INCRA", "doc_type": "CCIRCredential"},
    {"issuer_id": "MDA",   "doc_type": "CAFCredential"},
]


# =============================================================================
# Phase 1: Citizen Authentication (OAuth2 Authorization Code + PKCE)
# =============================================================================

def generate_pkce():
    code_verifier = secrets.token_urlsafe(32)[:43]
    digest = hashlib.sha256(code_verifier.encode("ascii")).digest()
    code_challenge = base64.urlsafe_b64encode(digest).rstrip(b"=").decode("ascii")
    return code_verifier, code_challenge


def build_authorize_url(client_id, code_challenge):
    nonce = secrets.token_hex(16)
    state = secrets.token_hex(16)
    params = {
        "response_type": "code",
        "client_id": client_id,
        "scope": "openid email profile",
        "redirect_uri": REDIRECT_URI,
        "nonce": nonce,
        "state": state,
        "code_challenge": code_challenge,
        "code_challenge_method": "S256",
    }
    return f"{SSO_URL}/authorize?{urllib.parse.urlencode(params)}", state


def exchange_code(code, code_verifier, client_id, client_secret):
    params = urllib.parse.urlencode({
        "grant_type": "authorization_code",
        "code": code,
        "redirect_uri": REDIRECT_URI,
        "code_verifier": code_verifier,
    })
    url = f"{SSO_URL}/token?{params}"
    credentials = base64.b64encode(f"{client_id}:{client_secret}".encode()).decode()
    req = urllib.request.Request(url, method="POST", headers={
        "Accept": "application/json",
        "Authorization": f"Basic {credentials}",
    })
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read())


def decode_jwt_payload(token):
    payload_b64 = token.split(".")[1]
    payload_b64 += "=" * (4 - len(payload_b64) % 4)
    return json.loads(base64.urlsafe_b64decode(payload_b64))


# =============================================================================
# Phase 2: Wallet Proof of Possession (OID4VCI proof JWT)
# =============================================================================

def b64url(data):
    if isinstance(data, str):
        data = data.encode()
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def generate_rsa_key_and_proof(certify_identifier, client_id):
    from cryptography.hazmat.primitives.asymmetric import rsa, padding
    from cryptography.hazmat.primitives import hashes

    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    public_key = private_key.public_key()
    pub_numbers = public_key.public_numbers()

    def int_to_b64url(n, length=None):
        b = n.to_bytes((n.bit_length() + 7) // 8, byteorder="big")
        if length and len(b) < length:
            b = b"\x00" * (length - len(b)) + b
        return b64url(b)

    jwk = {
        "kty": "RSA",
        "n": int_to_b64url(pub_numbers.n),
        "e": int_to_b64url(pub_numbers.e),
    }

    header = {
        "typ": "openid4vci-proof+jwt",
        "alg": "RS256",
        "jwk": jwk,
    }

    now = int(time.time())
    payload = {
        "iss": client_id,
        "aud": certify_identifier,
        "iat": now,
        "exp": now + 300,
    }

    signing_input = f"{b64url(json.dumps(header))}.{b64url(json.dumps(payload))}"
    signature = private_key.sign(
        signing_input.encode(),
        padding.PKCS1v15(),
        hashes.SHA256(),
    )

    proof_jwt = f"{signing_input}.{b64url(signature)}"
    return proof_jwt


def generate_rsa_key_and_proof_stdlib(certify_identifier, client_id):
    import subprocess
    import tempfile

    with tempfile.NamedTemporaryFile(suffix=".pem", delete=False) as f:
        key_file = f.name
    subprocess.run(["openssl", "genrsa", "-out", key_file, "2048"],
                   capture_output=True, check=True)

    result = subprocess.run(
        ["openssl", "rsa", "-in", key_file, "-text", "-noout"],
        capture_output=True, text=True, check=True
    )

    lines = result.stdout.split("\n")
    in_modulus = False
    modulus_hex = ""
    exponent = 65537

    for line in lines:
        if "modulus:" in line.lower():
            in_modulus = True
            continue
        if "publicexponent" in line.lower().replace(" ", ""):
            in_modulus = False
            parts = line.split("(")
            if len(parts) > 1:
                exponent = int(parts[0].split(":")[1].strip())
            continue
        if in_modulus:
            stripped = line.strip()
            if stripped and all(c in "0123456789abcdef:" for c in stripped):
                modulus_hex += stripped.replace(":", "")
            else:
                in_modulus = False

    modulus_bytes = bytes.fromhex(modulus_hex)
    if modulus_bytes[0] == 0:
        modulus_bytes = modulus_bytes[1:]

    e_bytes = exponent.to_bytes((exponent.bit_length() + 7) // 8, byteorder="big")

    jwk = {
        "kty": "RSA",
        "n": b64url(modulus_bytes),
        "e": b64url(e_bytes),
    }

    header = {
        "typ": "openid4vci-proof+jwt",
        "alg": "RS256",
        "jwk": jwk,
    }

    now = int(time.time())
    payload = {
        "iss": client_id,
        "aud": certify_identifier,
        "iat": now,
        "exp": now + 300,
    }

    signing_input = f"{b64url(json.dumps(header))}.{b64url(json.dumps(payload))}"

    with tempfile.NamedTemporaryFile(suffix=".bin", delete=False) as f:
        input_file = f.name
        f.write(signing_input.encode())

    result = subprocess.run(
        ["openssl", "dgst", "-sha256", "-sign", key_file, input_file],
        capture_output=True, check=True
    )
    signature = result.stdout

    os.unlink(key_file)
    os.unlink(input_file)

    proof_jwt = f"{signing_input}.{b64url(signature)}"
    return proof_jwt


def make_proof_jwt(certify_identifier, client_id):
    """Try the cryptography library first, fall back to openssl CLI."""
    try:
        from cryptography.hazmat.primitives.asymmetric import rsa  # noqa: F401
        return generate_rsa_key_and_proof(certify_identifier, client_id)
    except ImportError:
        print("  (cryptography library not found, using openssl CLI)")
        return generate_rsa_key_and_proof_stdlib(certify_identifier, client_id)


# =============================================================================
# Phase 3: Credential Issuance (POST /issuance/credential)
# =============================================================================

def request_credential(certify_url, access_token, proof_jwt, doc_type, issuer_id):
    url = f"{certify_url}/issuance/credential"
    body = json.dumps({
        "format": "ldp_vc",
        "issuerId": issuer_id,
        "doctype": doc_type,
        "credential_definition": {
            "@context": ["https://www.w3.org/2018/credentials/v1"],
            "type": ["VerifiableCredential", doc_type],
        },
        "proof": {
            "proof_type": "jwt",
            "jwt": proof_jwt,
        },
    })

    req = urllib.request.Request(url, method="POST", data=body.encode(), headers={
        "Content-Type": "application/json",
        "Authorization": f"Bearer {access_token}",
    })

    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read()), resp.status
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        try:
            return json.loads(body), e.code
        except json.JSONDecodeError:
            return {"error": body or f"HTTP {e.code}"}, e.code


# =============================================================================
# SSO Login Helper
# =============================================================================

def do_sso_login(client_id, client_secret):
    code_verifier, code_challenge = generate_pkce()
    authorize_url, expected_state = build_authorize_url(client_id, code_challenge)

    captured_code = None

    class RedirectHandler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            nonlocal captured_code
            query = urllib.parse.urlparse(self.path).query
            params = urllib.parse.parse_qs(query)
            if "code" in params:
                captured_code = params["code"][0]
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.end_headers()
                self.wfile.write(
                    b"<html><body><h1>Login successful!</h1>"
                    b"<p>You can close this tab and return to the terminal.</p>"
                    b"</body></html>"
                )
            else:
                self.send_response(400)
                self.end_headers()
                self.wfile.write(b"No code parameter found")

        def log_message(self, format, *args):
            pass  # Suppress HTTP request logs

    server = http.server.HTTPServer(("localhost", REDIRECT_PORT), RedirectHandler)

    print(f"\nStarting local server on port {REDIRECT_PORT}...")
    print(f"Logging out of previous Gov.br session...")
    webbrowser.open(f"{SSO_URL}/logout?post_logout_redirect_uri={urllib.parse.quote(authorize_url)}")
    time.sleep(2)
    print(f"Opening browser for Gov.br login...\n")
    webbrowser.open(authorize_url)

    print("Waiting for login redirect... (Ctrl+C to cancel)")
    server.handle_request()
    server.server_close()

    if not captured_code:
        print("ERROR: No authorization code received")
        sys.exit(1)

    print("Authorization code received!")
    print("Exchanging code for tokens...")

    try:
        tokens = exchange_code(captured_code, code_verifier, client_id, client_secret)
    except urllib.error.HTTPError as e:
        print(f"ERROR: {e.code} - {e.read().decode()}")
        sys.exit(1)

    return tokens


# =============================================================================
# Token Cache
# =============================================================================

def load_cached_token():
    """Return cached access_token if it exists and is still valid, else None."""
    try:
        with open(TOKEN_CACHE_FILE) as f:
            cache = json.load(f)
        token = cache.get("access_token")
        exp = cache.get("exp", 0)
        if token and time.time() < exp:
            return token
    except (FileNotFoundError, json.JSONDecodeError, KeyError):
        pass
    return None


def save_cached_token(access_token):
    claims = decode_jwt_payload(access_token)
    exp = claims.get("exp", 0)
    with open(TOKEN_CACHE_FILE, "w") as f:
        json.dump({"access_token": access_token, "exp": exp}, f)


# =============================================================================
# Main
# =============================================================================

def main():
    skip_login = "--skip-login" in sys.argv
    certify_url = os.environ.get("CERTIFY_URL", DEFAULT_CERTIFY_URL)
    certify_identifier = os.environ.get("CERTIFY_IDENTIFIER", DEFAULT_CERTIFY_IDENTIFIER)

    client_id = os.environ.get("SSO_CLIENT_ID")
    client_secret = os.environ.get("SSO_CLIENT_SECRET")

    if not skip_login and (not client_id or not client_secret):
        print("ERROR: Set SSO_CLIENT_ID and SSO_CLIENT_SECRET environment variables")
        sys.exit(1)

    # --- Phase 1: Authenticate ---
    if skip_login:
        access_token = os.environ.get("ACCESS_TOKEN")
        if not access_token:
            print("ERROR: Set ACCESS_TOKEN environment variable when using --skip-login")
            sys.exit(1)
        print("Using ACCESS_TOKEN from environment")
    elif cached := load_cached_token():
        access_token = cached
        print("Using cached token (still valid)")
        id_token = ""
    else:
        tokens = do_sso_login(client_id, client_secret)
        access_token = tokens["access_token"]
        id_token = tokens.get("id_token", "")
        save_cached_token(access_token)

        print("\n" + "=" * 60)
        print("TOKENS RECEIVED")
        print("=" * 60)

        if id_token:
            claims = decode_jwt_payload(id_token)
            print(f"  CPF (sub): {claims.get('sub')}")
            print(f"  Name:      {claims.get('name')}")
            print(f"  Email:     {claims.get('email')}")

    at_claims = decode_jwt_payload(access_token)
    proof_client_id = at_claims.get("aud", "")
    print(f"\nAccess Token:")
    print(f"  sub (CPF): {at_claims.get('sub')}")
    print(f"  aud:       {proof_client_id}")
    print(f"  exp:       {at_claims.get('exp')}")

    print(f"\nEnvironment:")
    print(f"  Certify URL:        {certify_url}")
    print(f"  Certify identifier: {certify_identifier}")

    # --- Phases 2+3: Issue all 3 credentials ---
    results = {}
    for cred in CREDENTIALS:
        issuer_id = cred["issuer_id"]
        doc_type = cred["doc_type"]

        print(f"\n{'=' * 60}")
        print(f"REQUESTING {doc_type} (issuer: {issuer_id})")
        print(f"{'=' * 60}")

        # Each request gets a fresh proof JWT (new ephemeral key pair)
        print("  Generating wallet proof JWT (alg=RS256)...")
        proof_jwt = make_proof_jwt(certify_identifier, proof_client_id)

        result, status = request_credential(
            certify_url, access_token, proof_jwt, doc_type, issuer_id
        )
        results[doc_type] = {"status": status, "response": result}

        print(f"  HTTP Status: {status}")
        if "credential" in result:
            print(f"  Result: ISSUED")
        else:
            print(f"  Result: FAILED")
            print(f"  Response: {json.dumps(result, indent=4, ensure_ascii=False)}")

    # --- Summary ---
    print(f"\n{'=' * 60}")
    print("SUMMARY")
    print(f"{'=' * 60}")
    all_ok = True
    for doc_type, info in results.items():
        status_str = "OK" if "credential" in info["response"] else "FAILED"
        if status_str == "FAILED":
            all_ok = False
        print(f"  {doc_type:20s} HTTP {info['status']}  {status_str}")

    if all_ok:
        print("\nAll credentials issued successfully!")
    else:
        print("\nSome credentials failed — see responses above for details.")
        sys.exit(1)


if __name__ == "__main__":
    main()
