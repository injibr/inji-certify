#!/usr/bin/env python3
"""
Gov.br SSO Login + Inji Certify Credential Issuance

Credential types are fetched dynamically from the well-known endpoint.
A single SSO login is used for all credentials (same CPF has all).
The token is cached so re-running can skip the login.

Environment variables:
    SSO_CLIENT_ID       (required) OAuth2 client ID registered with Gov.br SSO
    SSO_CLIENT_SECRET   (required) OAuth2 client secret

Dependencies: Python 3.8+, cryptography
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

from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.hazmat.primitives import hashes

REDIRECT_PORT = 3004
REDIRECT_URI = f"http://localhost:{REDIRECT_PORT}/redirect"
TOKEN_CACHE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".token_cache.json")

ENVIRONMENTS = {
    "1": {
        "name": "DEV",
        "sso_url": "https://sso.staging.acesso.gov.br",
        "certify_url": "https://injicertify.credenciaisverificaveis-dev.dataprev.gov.br/v1/certify",
        "certify_identifier": "https://injicertify.credenciaisverificaveis-dev.dataprev.gov.br",
    },
    "2": {
        "name": "HML",
        "sso_url": "https://sso.staging.acesso.gov.br",
        "certify_url": "https://injicertify.credenciaisverificaveis-hml.dataprev.gov.br/v1/certify",
        "certify_identifier": "https://injicertify.credenciaisverificaveis-hml.dataprev.gov.br",
    },
    "3": {
        "name": "LOCAL",
        "sso_url": "https://sso.staging.acesso.gov.br",
        "certify_url": "http://localhost:30090/v1/certify",
        "certify_identifier": "http://inji-certify.inji-local.svc.cluster.local",
    },
}


# =============================================================================
# Utilities
# =============================================================================

def b64url(data):
    if isinstance(data, str):
        data = data.encode()
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def decode_jwt_payload(token):
    payload_b64 = token.split(".")[1]
    payload_b64 += "=" * (4 - len(payload_b64) % 4)
    return json.loads(base64.urlsafe_b64decode(payload_b64))


def fetch_credential_types(certify_url):
    """Fetch available credential types from the well-known endpoint."""
    url = f"{certify_url}/.well-known/openid-credential-issuer"
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read())
    except (urllib.error.URLError, json.JSONDecodeError) as e:
        print(f"ERROR: Failed to fetch well-known: {e}")
        sys.exit(1)
    # Each key is the credential type (e.g. "CAFCredential")
    return list(data.get("credential_configurations_supported", {}).keys())


# =============================================================================
# Phase 1: Citizen Authentication (OAuth2 Authorization Code + PKCE)
# =============================================================================

def generate_pkce():
    code_verifier = secrets.token_urlsafe(32)[:43]
    digest = hashlib.sha256(code_verifier.encode("ascii")).digest()
    code_challenge = base64.urlsafe_b64encode(digest).rstrip(b"=").decode("ascii")
    return code_verifier, code_challenge


def build_authorize_url(sso_url, client_id, code_challenge):
    params = {
        "response_type": "code",
        "client_id": client_id,
        "scope": "openid email profile",
        "redirect_uri": REDIRECT_URI,
        "nonce": secrets.token_hex(16),
        "state": secrets.token_hex(16),
        "code_challenge": code_challenge,
        "code_challenge_method": "S256",
    }
    return f"{sso_url}/authorize?{urllib.parse.urlencode(params)}"


def exchange_code(sso_url, code, code_verifier, client_id, client_secret):
    params = urllib.parse.urlencode({
        "grant_type": "authorization_code",
        "code": code,
        "redirect_uri": REDIRECT_URI,
        "code_verifier": code_verifier,
    })
    credentials = base64.b64encode(f"{client_id}:{client_secret}".encode()).decode()
    req = urllib.request.Request(f"{sso_url}/token?{params}", method="POST", headers={
        "Accept": "application/json",
        "Authorization": f"Basic {credentials}",
    })
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read())


def do_sso_login(sso_url, client_id, client_secret):
    """Perform SSO login via browser redirect. Returns tokens dict."""
    code_verifier, code_challenge = generate_pkce()
    authorize_url = build_authorize_url(sso_url, client_id, code_challenge)
    captured_code = None

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            nonlocal captured_code
            params = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            if "code" in params:
                captured_code = params["code"][0]
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.end_headers()
                self.wfile.write(b"<h1>Login successful! You can close this tab.</h1>")
            else:
                self.send_response(400)
                self.end_headers()

        def log_message(self, *args):
            pass

    server = http.server.HTTPServer(("localhost", REDIRECT_PORT), Handler)
    print(f"  Opening browser for Gov.br login...")
    webbrowser.open(authorize_url)
    print(f"  Waiting for login redirect... (Ctrl+C to cancel)")
    server.handle_request()
    server.server_close()

    if not captured_code:
        print("  ERROR: No authorization code received")
        sys.exit(1)

    print("  Exchanging code for tokens...")
    try:
        return exchange_code(sso_url, captured_code, code_verifier, client_id, client_secret)
    except urllib.error.HTTPError as e:
        print(f"  ERROR: {e.code} - {e.read().decode()}")
        sys.exit(1)


# =============================================================================
# Phase 2: Wallet Proof of Possession (OID4VCI proof JWT)
# =============================================================================

def make_proof_jwt(certify_identifier, client_id, c_nonce=None):
    """Generate ephemeral RSA-2048 key and build signed OID4VCI proof JWT."""
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    pub_numbers = private_key.public_key().public_numbers()

    def int_to_b64url(n):
        b = n.to_bytes((n.bit_length() + 7) // 8, byteorder="big")
        return b64url(b)

    jwk = {"kty": "RSA", "n": int_to_b64url(pub_numbers.n), "e": int_to_b64url(pub_numbers.e)}
    header = {"typ": "openid4vci-proof+jwt", "alg": "RS256", "jwk": jwk}

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
    signature = private_key.sign(signing_input.encode(), padding.PKCS1v15(), hashes.SHA256())
    return f"{signing_input}.{b64url(signature)}"


# =============================================================================
# Phase 3: Credential Issuance (POST /issuance/credential)
# =============================================================================

def request_credential(certify_url, access_token, proof_jwt, doc_type):
    url = f"{certify_url}/issuance/credential"
    body = json.dumps({
        "format": "ldp_vc",
        "issuerId": doc_type,
        "doctype": doc_type,
        "credential_definition": {
            "@context": ["https://www.w3.org/ns/credentials/v2"],
            "type": ["VerifiableCredential", doc_type],
        },
        "proof": {"proof_type": "jwt", "jwt": proof_jwt},
    })
    req = urllib.request.Request(url, method="POST", data=body.encode(), headers={
        "Content-Type": "application/json",
        "Authorization": f"Bearer {access_token}",
    })
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read()), resp.status
    except urllib.error.HTTPError as e:
        err_body = e.read().decode()
        try:
            return json.loads(err_body), e.code
        except json.JSONDecodeError:
            return {"error": err_body or f"HTTP {e.code}"}, e.code


# =============================================================================
# Token Cache
# =============================================================================

def load_cached_token():
    """Return cached access_token if still valid, else None."""
    try:
        with open(TOKEN_CACHE_FILE) as f:
            entry = json.load(f)
        if entry.get("access_token") and time.time() < entry.get("exp", 0):
            return entry["access_token"]
    except (FileNotFoundError, json.JSONDecodeError):
        pass
    return None


def save_cached_token(access_token):
    """Cache the access token to disk."""
    claims = decode_jwt_payload(access_token)
    with open(TOKEN_CACHE_FILE, "w") as f:
        json.dump({
            "access_token": access_token,
            "exp": claims.get("exp", 0),
            "cpf": claims.get("sub", "unknown"),
        }, f, indent=2)


# =============================================================================
# Main
# =============================================================================

def main():
    # Environment selection
    print("Select environment:")
    for key, env in ENVIRONMENTS.items():
        print(f"  {key}) {env['name']}")
    env_choice = input("\nChoose an option (number): ").strip()

    if env_choice not in ENVIRONMENTS:
        print("ERROR: Invalid environment.")
        sys.exit(1)

    env = ENVIRONMENTS[env_choice]
    print(f"\n  Environment: {env['name']}")
    print(f"  Certify URL: {env['certify_url']}")

    # Load credentials
    client_id = os.environ.get("SSO_CLIENT_ID")
    client_secret = os.environ.get("SSO_CLIENT_SECRET")
    if not client_id or not client_secret:
        print("ERROR: Set SSO_CLIENT_ID and SSO_CLIENT_SECRET environment variables")
        sys.exit(1)

    # Fetch credential types from well-known
    print("\nFetching credential types...")
    all_types = fetch_credential_types(env["certify_url"])

    # Credential type selection
    print("\nAvailable credential types:")
    print("  0) ALL")
    for i, t in enumerate(all_types, 1):
        print(f"  {i}) {t}")
    choice = input("\nChoose an option (number): ").strip()

    if choice == "0":
        credential_types = all_types
    elif choice.isdigit() and 1 <= int(choice) <= len(all_types):
        credential_types = [all_types[int(choice) - 1]]
    else:
        print("ERROR: Invalid option.")
        sys.exit(1)

    print(f"\nCredentials to issue: {', '.join(credential_types)}")

    # Single login for all credentials
    access_token = load_cached_token()
    if access_token:
        at_claims = decode_jwt_payload(access_token)
        print(f"  Using cached token (CPF: {at_claims.get('sub', 'unknown')})")
    else:
        print("  No valid cached token, initiating SSO login...")
        tokens = do_sso_login(env["sso_url"], client_id, client_secret)
        access_token = tokens["access_token"]
        save_cached_token(access_token)
        at_claims = decode_jwt_payload(access_token)
        print(f"\n  Login successful!")
        print(f"    CPF:  {at_claims.get('sub', 'unknown')}")
        id_token = tokens.get("id_token", "")
        if id_token:
            id_claims = decode_jwt_payload(id_token)
            print(f"    Name: {id_claims.get('name', 'N/A')}")

    proof_client_id = at_claims.get("aud", "")
    c_nonce = at_claims.get("c_nonce")

    # Issue each credential
    results = {}
    for doc_type in credential_types:
        print(f"\n{'=' * 60}")
        print(f"CREDENTIAL: {doc_type}")
        print("=" * 60)

        print(f"  Generating wallet proof JWT (alg=RS256)...")
        if c_nonce:
            print(f"    Including c_nonce in proof JWT")
        proof_jwt = make_proof_jwt(env["certify_identifier"], proof_client_id, c_nonce)

        print(f"  Requesting credential...")
        result, status = request_credential(env["certify_url"], access_token, proof_jwt, doc_type)
        results[doc_type] = {"status": status, "response": result}

        print(f"  HTTP Status: {status}")
        if "credential" in result:
            print(f"  Result: ISSUED")
            print(f"\n  Credential:")
            print(json.dumps(result["credential"], indent=4, ensure_ascii=False))
        else:
            print(f"  Result: FAILED")
            print(f"  Response: {json.dumps(result, indent=4, ensure_ascii=False)}")

    # Summary
    print(f"\n{'=' * 60}")
    print("SUMMARY")
    print("=" * 60)
    for doc_type, info in results.items():
        status_str = "OK" if "credential" in info["response"] else "FAILED"
        print(f"  {doc_type:25s} HTTP {info['status']}  {status_str}")

    if not all("credential" in info["response"] for info in results.values()):
        print("\nSome credentials failed — see responses above for details.")
        sys.exit(1)
    else:
        print("\nAll credentials issued successfully!")


if __name__ == "__main__":
    main()
