#!/usr/bin/env python3
"""
Gov.br SSO Login + Inji Certify Credential Issuance — Local Container
=======================================================================

Same OID4VCI flow as govbr_token_homolog.py, but targets the local
podman-compose stack running at localhost:8090.

Credential types issued:
  - MDA   → CAFCredential   (CAF family farming registry)
  - INCRA → CCIRCredential  (SERPRO/Dataprev land certificate)
  - MGI   → ECACredential   (Dataprev ECA)

Each credential requires a separate SSO login (different CPF per issuer).
Tokens are cached per issuer so re-running can skip logins.

Usage:
    python3 scripts/govbr_token_local.py [--issuer ISSUER_ID]

    --issuer ISSUER_ID  Only issue credential for the specified issuer
                        (MGI, INCRA, or MDA). Can be repeated.

Environment variables:
    SSO_CLIENT_ID       (required) OAuth2 client ID registered with Gov.br SSO
    SSO_CLIENT_SECRET   (required) OAuth2 client secret
    CERTIFY_URL         (optional) Certify base URL
                        default: http://localhost:8090/v1/certify
    CERTIFY_IDENTIFIER  (optional) Certify identifier used as proof JWT audience
                        default: http://localhost:8090

Dependencies:
    - Python 3.8+ (standard library only for the SSO flow)
    - `cryptography` library (optional, for proof JWT generation)
    - Falls back to `openssl` CLI if `cryptography` is not installed
"""

import argparse
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

ECA_TOKEN_URL = "https://hisrj.dataprev.gov.br/oauth2/token"
ECA_DATA_URL = "https://hapirj.dataprev.gov.br/cpfrfb/1.0.0/v1/cpf/{cpf}"

TOKEN_CACHE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".token_cache_local.json")

DEFAULT_CERTIFY_URL = "http://localhost:8090/v1/certify"
DEFAULT_CERTIFY_IDENTIFIER = "http://localhost:8090"

# Credential types to issue, one per issuer
CREDENTIALS = [
    # {"issuer_id": "MDA",   "doc_type": "CAFCredential"},
    # {"issuer_id": "INCRA", "doc_type": "CCIRCredential"},
    {"issuer_id": "MGI",   "doc_type": "ECACredential"},
]


# =============================================================================
# ECA Dataprev Preflight Check
# =============================================================================

def check_eca_connectivity(cpf, eca_client_id, eca_client_secret):
    """Verify ECA token and data endpoints are reachable and credentials are valid.

    Uses HTTP Basic Auth — Dataprev's WSO2 token endpoint rejects form-body credentials
    (returns 'Unsupported client authentication mechanism').
    """
    print("  [preflight] Testing ECA Dataprev connectivity...")

    credentials = base64.b64encode(f"{eca_client_id}:{eca_client_secret}".encode()).decode()
    req = urllib.request.Request(
        ECA_TOKEN_URL,
        method="POST",
        data=b"grant_type=client_credentials&scope=default",
        headers={
            "Content-Type": "application/x-www-form-urlencoded",
            "Authorization": f"Basic {credentials}",
        },
    )
    try:
        with urllib.request.urlopen(req) as resp:
            token_data = json.loads(resp.read())
    except urllib.error.HTTPError as e:
        print(f"  [preflight] FAIL — token endpoint HTTP {e.code}: {e.read().decode()}")
        return False

    eca_token = token_data.get("access_token")
    if not eca_token:
        print(f"  [preflight] FAIL — token endpoint: no access_token in response")
        return False
    print(f"  [preflight] OK   — ECA token obtained from Dataprev")

    data_url = ECA_DATA_URL.format(cpf=cpf)
    req = urllib.request.Request(data_url, headers={"Authorization": f"Bearer {eca_token}"})
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read())
    except urllib.error.HTTPError as e:
        print(f"  [preflight] FAIL — data endpoint HTTP {e.code}: {e.read().decode()}")
        return False

    dob = data.get("dataNascimento", "N/A")
    situation = data.get("situacaoCpf", {}).get("descricao", "N/A")
    print(f"  [preflight] OK   — data endpoint: dataNascimento={dob}, CPF situation={situation}")
    return True


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


def generate_rsa_key_and_proof(certify_identifier, client_id, c_nonce=None):
    """
    Generate an ephemeral RSA-2048 key pair and build a signed OID4VCI proof JWT.
    Uses the `cryptography` library (preferred method).

    Matches mimoto's JoseUtil.generateJwt() implementation:
    - Includes 'sub' claim (same as 'iss')
    - Includes 'nonce' claim if c_nonce is provided (extracted from access_token)

    Parameters:
    - certify_identifier: The credential issuer identifier (aud claim)
    - client_id: The OAuth2 client_id (iss and sub claims)
    - c_nonce: Optional nonce from the access_token (for replay protection)
    """
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
        "sub": client_id,
        "aud": certify_identifier,
        "iat": now,
        "exp": now + 300,
    }
    if c_nonce:
        payload["nonce"] = c_nonce

    signing_input = f"{b64url(json.dumps(header))}.{b64url(json.dumps(payload))}"
    signature = private_key.sign(
        signing_input.encode(),
        padding.PKCS1v15(),
        hashes.SHA256(),
    )

    proof_jwt = f"{signing_input}.{b64url(signature)}"
    return proof_jwt


def generate_rsa_key_and_proof_stdlib(certify_identifier, client_id, c_nonce=None):
    """
    Fallback proof JWT generation using the openssl CLI.
    Used when the `cryptography` Python library is not installed.
    """
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
        "sub": client_id,
        "aud": certify_identifier,
        "iat": now,
        "exp": now + 300,
    }
    if c_nonce:
        payload["nonce"] = c_nonce

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


def make_proof_jwt(certify_identifier, client_id, c_nonce=None):
    try:
        from cryptography.hazmat.primitives.asymmetric import rsa  # noqa: F401
        return generate_rsa_key_and_proof(certify_identifier, client_id, c_nonce)
    except ImportError:
        print("    (cryptography library not found, using openssl CLI)")
        return generate_rsa_key_and_proof_stdlib(certify_identifier, client_id, c_nonce)


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

def do_sso_login(client_id, client_secret, issuer_id):
    """Perform SSO login for a specific issuer. Returns tokens dict."""
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

    print(f"\n  Starting local server on port {REDIRECT_PORT}...")
    print(f"  Logging out of previous Gov.br session...")
    webbrowser.open(f"{SSO_URL}/logout?post_logout_redirect_uri={urllib.parse.quote(authorize_url)}")
    time.sleep(2)
    print(f"  Opening browser for Gov.br login ({issuer_id})...\n")
    webbrowser.open(authorize_url)

    print(f"  Waiting for login redirect... (Ctrl+C to cancel)")
    server.handle_request()
    server.server_close()

    if not captured_code:
        print("  ERROR: No authorization code received")
        sys.exit(1)

    print("  Authorization code received!")
    print("  Exchanging code for tokens...")

    try:
        tokens = exchange_code(captured_code, code_verifier, client_id, client_secret)
    except urllib.error.HTTPError as e:
        print(f"  ERROR: {e.code} - {e.read().decode()}")
        sys.exit(1)

    return tokens


# =============================================================================
# Token Cache (per-issuer)
# =============================================================================

def load_token_cache():
    try:
        with open(TOKEN_CACHE_FILE) as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return {}


def save_token_cache(cache):
    with open(TOKEN_CACHE_FILE, "w") as f:
        json.dump(cache, f, indent=2)


def get_cached_token(cache, issuer_id):
    entry = cache.get(issuer_id, {})
    token = entry.get("access_token")
    exp = entry.get("exp", 0)
    if token and time.time() < exp:
        return token
    return None


def cache_token(cache, issuer_id, access_token):
    claims = decode_jwt_payload(access_token)
    exp = claims.get("exp", 0)
    cache[issuer_id] = {
        "access_token": access_token,
        "exp": exp,
        "cpf": claims.get("sub", "unknown"),
    }
    save_token_cache(cache)


# =============================================================================
# Main
# =============================================================================

def main():
    parser = argparse.ArgumentParser(description="Issue credentials via Gov.br SSO + local Inji Certify container")
    parser.add_argument(
        "--issuer", "-i",
        action="append",
        dest="issuers",
        choices=["MGI", "INCRA", "MDA"],
        help="Only issue credential for specified issuer (can repeat)",
    )
    args = parser.parse_args()

    certify_url = os.environ.get("CERTIFY_URL", DEFAULT_CERTIFY_URL)
    certify_identifier = os.environ.get("CERTIFY_IDENTIFIER", DEFAULT_CERTIFY_IDENTIFIER)

    client_id = os.environ.get("SSO_CLIENT_ID")
    client_secret = os.environ.get("SSO_CLIENT_SECRET")

    if not client_id or not client_secret:
        print("ERROR: Set SSO_CLIENT_ID and SSO_CLIENT_SECRET environment variables")
        sys.exit(1)

    credentials = CREDENTIALS
    if args.issuers:
        credentials = [c for c in CREDENTIALS if c["issuer_id"] in args.issuers]
        if not credentials:
            print(f"ERROR: No matching issuers for: {args.issuers}")
            sys.exit(1)

    print("=" * 60)
    print("INJI CERTIFY CREDENTIAL ISSUANCE (Local)")
    print("=" * 60)
    print(f"Environment:")
    print(f"  Certify URL:        {certify_url}")
    print(f"  Certify identifier: {certify_identifier}")
    print(f"Issuers to process: {', '.join(c['issuer_id'] for c in credentials)}")

    token_cache = load_token_cache()

    results = {}
    for cred in credentials:
        issuer_id = cred["issuer_id"]
        doc_type = cred["doc_type"]

        print(f"\n{'=' * 60}")
        print(f"ISSUER: {issuer_id} — {doc_type}")
        print("=" * 60)

        access_token = get_cached_token(token_cache, issuer_id)

        if access_token:
            at_claims = decode_jwt_payload(access_token)
            cpf = at_claims.get("sub", "unknown")
            print(f"  Using cached token (CPF: {cpf})")
        else:
            print(f"  No valid cached token for {issuer_id}, initiating SSO login...")
            tokens = do_sso_login(client_id, client_secret, issuer_id)
            access_token = tokens["access_token"]
            id_token = tokens.get("id_token", "")

            cache_token(token_cache, issuer_id, access_token)

            at_claims = decode_jwt_payload(access_token)
            cpf = at_claims.get("sub", "unknown")
            print(f"\n  Login successful!")
            print(f"    CPF:  {cpf}")
            if id_token:
                id_claims = decode_jwt_payload(id_token)
                print(f"    Name: {id_claims.get('name', 'N/A')}")

        proof_client_id = at_claims.get("aud", "")
        c_nonce = at_claims.get("c_nonce")

        if doc_type == "ECACredential":
            eca_client_id = os.environ.get("ECA_CLIENT_ID")
            eca_client_secret = os.environ.get("ECA_CLIENT_SECRET")
            if eca_client_id and eca_client_secret:
                ok = check_eca_connectivity(cpf, eca_client_id, eca_client_secret)
                if not ok:
                    print("  WARNING: ECA preflight failed — issuance will likely return HTTP 424")
            else:
                print("  [preflight] SKIP — ECA_CLIENT_ID / ECA_CLIENT_SECRET not set")

        print(f"  Generating wallet proof JWT (alg=RS256)...")
        if c_nonce:
            print(f"    Including c_nonce in proof JWT")
        proof_jwt = make_proof_jwt(certify_identifier, proof_client_id, c_nonce)

        print(f"  Requesting credential...")
        result, status = request_credential(
            certify_url, access_token, proof_jwt, doc_type, issuer_id
        )
        results[issuer_id] = {"status": status, "response": result, "doc_type": doc_type}

        print(f"  HTTP Status: {status}")
        if "credential" in result:
            print(f"  Result: ISSUED")
            print(f"\n  Credential:")
            print(json.dumps(result["credential"], indent=4, ensure_ascii=False))
        else:
            print(f"  Result: FAILED")
            print(f"  Response: {json.dumps(result, indent=4, ensure_ascii=False)}")

    print(f"\n{'=' * 60}")
    print("SUMMARY")
    print("=" * 60)
    all_ok = True
    for issuer_id, info in results.items():
        status_str = "OK" if "credential" in info["response"] else "FAILED"
        if status_str == "FAILED":
            all_ok = False
        print(f"  {issuer_id:8s} {info['doc_type']:20s} HTTP {info['status']}  {status_str}")

    if all_ok:
        print("\nAll credentials issued successfully!")
    else:
        print("\nSome credentials failed — see responses above for details.")
        sys.exit(1)


if __name__ == "__main__":
    main()
