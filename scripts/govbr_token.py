#!/usr/bin/env python3
"""
Gov.br SSO Login + Inji Certify Credential Issuance Test Script
================================================================

This script automates the full OpenID for Verifiable Credential Issuance
(OID4VCI) flow against a local Inji Certify instance, using Gov.br SSO
as the Authorization Server. It simulates what a wallet app (e.g., Inji
Wallet) does to obtain a Verifiable Credential.

The flow has three main phases:

  Phase 1 - Citizen Authentication (OAuth2 Authorization Code + PKCE)
  Phase 2 - Wallet Proof of Possession (self-signed JWT with ephemeral key)
  Phase 3 - Credential Issuance (POST to Certify with Bearer token + proof)

Sequence diagram:

  Citizen        Browser       Gov.br SSO     This Script     Certify
     |               |              |              |              |
     |  login page   |              |              |              |
     |<--------------|<-------------|<-------------|              |
     |  credentials  |              |              |              |
     |-------------->|------------->|              |              |
     |               |  redirect    |              |              |
     |               |  ?code=XXX   |              |              |
     |               |---------------------------->|              |
     |               |              |  exchange    |              |
     |               |              |  code+PKCE   |              |
     |               |              |<-------------|              |
     |               |              |  access_token|              |
     |               |              |------------->|              |
     |               |              |              | POST         |
     |               |              |              | /credential  |
     |               |              |              |------------->|
     |               |              |              |    VC (JSON) |
     |               |              |              |<-------------|

Usage:
    python3 scripts/govbr_token.py [--skip-login]

    --skip-login  Skip the SSO login flow and reuse an existing token
                  from the ACCESS_TOKEN environment variable.

Environment variables:
    SSO_CLIENT_ID      (required) OAuth2 client ID registered with Gov.br SSO
    SSO_CLIENT_SECRET  (required) OAuth2 client secret
    ACCESS_TOKEN       (optional) Pre-existing access token (with --skip-login)
    CERTIFY_URL        (optional) Certify base URL (default: http://localhost:8090/v1/certify)

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


# =============================================================================
# Phase 1: Citizen Authentication (OAuth2 Authorization Code + PKCE)
# =============================================================================
#
# Gov.br SSO requires PKCE (Proof Key for Code Exchange, RFC 7636) on top of
# the standard OAuth2 Authorization Code flow. PKCE prevents authorization code
# interception attacks, which is especially important for mobile/public clients.
#
# The flow works as follows:
#
# 1. Generate a random `code_verifier` (a secret known only to this client)
# 2. Derive `code_challenge = base64url(sha256(code_verifier))`
# 3. Send `code_challenge` in the /authorize request (browser login)
# 4. Send `code_verifier` in the /token request (code exchange)
# 5. The SSO server verifies that sha256(code_verifier) == code_challenge
#
# This ensures that only the party that initiated the login can exchange the
# authorization code, even if the code is intercepted during the redirect.
# =============================================================================

def generate_pkce():
    """
    Generate a PKCE code_verifier and code_challenge pair.

    The code_verifier is a cryptographically random string. The code_challenge
    is its SHA-256 hash, base64url-encoded (S256 method as per RFC 7636).
    """
    code_verifier = secrets.token_urlsafe(32)[:43]
    digest = hashlib.sha256(code_verifier.encode("ascii")).digest()
    code_challenge = base64.urlsafe_b64encode(digest).rstrip(b"=").decode("ascii")
    return code_verifier, code_challenge


def build_authorize_url(client_id, code_challenge):
    """
    Build the Gov.br SSO authorization URL that the citizen will visit in
    their browser to authenticate.

    Parameters:
    - response_type=code  : We want an authorization code back
    - client_id           : Identifies our application to Gov.br
    - scope               : What data we're requesting (openid = authentication,
                            email/profile = basic user info)
    - redirect_uri        : Where Gov.br sends the citizen after login. Must match
                            exactly what's registered for this client_id.
    - nonce               : Random value to prevent replay attacks (tied to the
                            id_token for validation)
    - state               : Random value to prevent CSRF. We verify it matches
                            when the redirect comes back.
    - code_challenge      : The PKCE challenge (see generate_pkce)
    - code_challenge_method=S256 : Indicates we used SHA-256 for the challenge
    """
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
    """
    Exchange the authorization code for tokens (access_token + id_token).

    This is the second leg of the Authorization Code flow. After the citizen
    logs in and Gov.br redirects back with a `code`, we POST to the /token
    endpoint with:
    - grant_type=authorization_code : Standard OAuth2 grant
    - code                          : The authorization code from the redirect
    - redirect_uri                  : Must match the one used in /authorize
    - code_verifier                 : The PKCE secret (server verifies it
                                      matches the code_challenge from step 1)

    Authentication is via HTTP Basic (base64(client_id:client_secret)) as
    required by the Gov.br SSO implementation.

    Returns a JSON dict with:
    - access_token : JWT signed by Gov.br, contains the citizen's CPF in `sub`
    - id_token     : JWT with user profile info (name, email, etc.)
    """
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
    """
    Decode a JWT payload without signature verification.
    Used only for display/debugging purposes.

    A JWT has three base64url-encoded parts separated by dots:
      header.payload.signature

    This function decodes the middle part (payload) to read the claims.
    """
    payload_b64 = token.split(".")[1]
    payload_b64 += "=" * (4 - len(payload_b64) % 4)
    return json.loads(base64.urlsafe_b64decode(payload_b64))


# =============================================================================
# Phase 2: Wallet Proof of Possession (OID4VCI proof JWT)
# =============================================================================
#
# The OID4VCI protocol requires the wallet to prove it controls a private key.
# This is called "holder binding" -- the issued credential will be bound to
# the wallet's key, so only that wallet can present it later.
#
# The proof is a self-signed JWT with:
#   Header:
#     - typ: "openid4vci-proof+jwt"  (identifies this as an OID4VCI proof)
#     - alg: "RS256"                 (signing algorithm)
#     - jwk: { public key }          (the wallet's public key, embedded in the
#                                     header so Certify can extract it)
#   Payload:
#     - iss: <client_id>             (who is requesting the credential)
#     - aud: <certify_identifier>    (the credential issuer's identifier URL)
#     - iat: <timestamp>             (when the proof was created)
#     - exp: <timestamp>             (when the proof expires)
#
# Certify validates this proof by:
#   1. Checking typ == "openid4vci-proof+jwt"
#   2. Checking alg is in the supported list (RS256, PS256, ES256)
#   3. Extracting the public key from the jwk header
#   4. Verifying the JWT signature using that public key
#   5. Checking aud matches Certify's own identifier
#   6. Checking the token is not expired
#
# The public key from the proof is then encoded as a did:jwk and embedded in
# the issued credential as the holder's identifier (credentialSubject.id).
# =============================================================================

def b64url(data):
    """Base64url encode without padding (as required by JWT/JWK specs)."""
    if isinstance(data, str):
        data = data.encode()
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def generate_rsa_key_and_proof(certify_identifier, client_id):
    """
    Generate an ephemeral RSA-2048 key pair and build a signed OID4VCI proof JWT.
    Uses the `cryptography` library (preferred method).

    In a real wallet, this key pair would be persistent and stored securely.
    For testing, we generate a throwaway key each time.
    """
    from cryptography.hazmat.primitives.asymmetric import rsa, padding
    from cryptography.hazmat.primitives import hashes

    # Generate a fresh RSA-2048 key pair (simulates the wallet's key)
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    public_key = private_key.public_key()
    pub_numbers = public_key.public_numbers()

    def int_to_b64url(n, length=None):
        b = n.to_bytes((n.bit_length() + 7) // 8, byteorder="big")
        if length and len(b) < length:
            b = b"\x00" * (length - len(b)) + b
        return b64url(b)

    # Build a JWK (JSON Web Key) representing the public key.
    # This gets embedded in the proof JWT header so Certify can extract it.
    jwk = {
        "kty": "RSA",
        "n": int_to_b64url(pub_numbers.n),  # modulus
        "e": int_to_b64url(pub_numbers.e),  # exponent
    }

    # JWT header: identifies this as an OID4VCI proof and includes the public key
    header = {
        "typ": "openid4vci-proof+jwt",
        "alg": "RS256",
        "jwk": jwk,
    }

    # JWT payload: standard claims for audience, issuer, and timestamps
    now = int(time.time())
    payload = {
        "iss": client_id,             # the OAuth2 client requesting the credential
        "aud": certify_identifier,    # must match mosip.certify.identifier
        "iat": now,
        "exp": now + 300,             # 5 minute validity
    }

    # Sign: encode header and payload, then sign with the private key
    signing_input = f"{b64url(json.dumps(header))}.{b64url(json.dumps(payload))}"
    signature = private_key.sign(
        signing_input.encode(),
        padding.PKCS1v15(),
        hashes.SHA256(),
    )

    proof_jwt = f"{signing_input}.{b64url(signature)}"
    return proof_jwt, jwk


def generate_rsa_key_and_proof_stdlib(certify_identifier, client_id):
    """
    Fallback proof JWT generation using the openssl CLI.
    Used when the `cryptography` Python library is not installed.
    Functionally identical to generate_rsa_key_and_proof().
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

    # Parse the RSA modulus and exponent from openssl text output
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
    return proof_jwt, jwk


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
#
# With the access_token (proves citizen identity) and the proof JWT (proves
# wallet key ownership), we can now request a Verifiable Credential.
#
# The request body follows the OID4VCI credential request format:
# {
#   "format": "ldp_vc",                  -- JSON-LD Verifiable Credential
#   "issuerId": "MGI",                   -- Which issuer (MGI, INCRA, MDA)
#   "doctype": "ECACredential",          -- Which credential type to issue
#   "credential_definition": {
#     "@context": ["https://..."],        -- JSON-LD context
#     "type": ["VerifiableCredential", "ECACredential"]
#   },
#   "proof": {
#     "proof_type": "jwt",
#     "jwt": "<the proof JWT from Phase 2>"
#   }
# }
#
# Certify processes this by:
#   1. Validating the access_token (signature check against Gov.br JWK set)
#   2. Looking up the issuer metadata (MGI -> ECACredential)
#   3. Validating the proof JWT (signature, typ, aud, expiry)
#   4. Extracting the citizen's CPF from access_token.sub
#   5. Calling EcaDataProvider.getData(cpf) which:
#      a. Gets an OAuth2 token from Dataprev (EcaTokenClient)
#      b. Calls the Dataprev API to get the citizen's birth date
#      c. Computes isOver12/14/16/18 from the birth date
#   6. Rendering the VC template (Velocity) with the data
#   7. Signing the VC with the Ed25519 key from the PKCS12 keystore
#   8. Returning the signed Verifiable Credential
# =============================================================================

def request_credential(certify_url, access_token, proof_jwt, doc_type="ECACredential", issuer_id="MGI"):
    """
    POST a credential request to the Certify issuance endpoint.

    Returns a tuple of (response_json, http_status_code).
    On success (200), response_json contains a "credential" key with the
    signed Verifiable Credential in JSON-LD format.
    """
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
        return json.loads(e.read().decode()), e.code


# =============================================================================
# SSO Login Helper
# =============================================================================
#
# This function orchestrates Phase 1 by:
#   1. Generating PKCE values
#   2. Building the authorize URL
#   3. Starting a temporary HTTP server on localhost:3004/redirect
#   4. Opening the browser to the Gov.br login page
#   5. Waiting for Gov.br to redirect back with the authorization code
#   6. Exchanging the code for tokens
#
# The local HTTP server acts as the OAuth2 redirect_uri endpoint. When the
# citizen logs in, Gov.br redirects their browser to:
#   http://localhost:3004/redirect?code=XXXX&state=YYYY
#
# The server captures the code, shows "Login successful!" to the citizen,
# and returns the code to the script for the token exchange.
# =============================================================================

def do_sso_login(client_id, client_secret):
    """Run the full OAuth2 Authorization Code + PKCE flow with Gov.br SSO."""
    code_verifier, code_challenge = generate_pkce()
    authorize_url, expected_state = build_authorize_url(client_id, code_challenge)

    captured_code = None

    class RedirectHandler(http.server.BaseHTTPRequestHandler):
        """Minimal HTTP handler that captures the authorization code from the
        OAuth2 redirect and shows a success page to the user."""

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
    # Open the logout URL first to clear any existing session cookie,
    # so the user gets a fresh login page and can use a different account.
    webbrowser.open(f"{SSO_URL}/logout?post_logout_redirect_uri={urllib.parse.quote(authorize_url)}")
    time.sleep(2)  # Give the browser time to process the logout
    print(f"Opening browser for Gov.br login...\n")
    webbrowser.open(authorize_url)

    print("Waiting for login redirect... (Ctrl+C to cancel)")
    server.handle_request()  # Handles exactly one request, then returns
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
# Main: Orchestrates all three phases
# =============================================================================

def main():
    skip_login = "--skip-login" in sys.argv
    certify_url = os.environ.get("CERTIFY_URL", "http://localhost:8090/v1/certify")

    client_id = os.environ.get("SSO_CLIENT_ID")
    client_secret = os.environ.get("SSO_CLIENT_SECRET")

    if not skip_login and (not client_id or not client_secret):
        print("ERROR: Set SSO_CLIENT_ID and SSO_CLIENT_SECRET environment variables")
        sys.exit(1)

    # --- Phase 1: Authenticate the citizen via Gov.br SSO ---
    if skip_login:
        access_token = os.environ.get("ACCESS_TOKEN")
        if not access_token:
            print("ERROR: Set ACCESS_TOKEN environment variable when using --skip-login")
            sys.exit(1)
        print("Using ACCESS_TOKEN from environment")
    else:
        tokens = do_sso_login(client_id, client_secret)
        access_token = tokens["access_token"]
        id_token = tokens.get("id_token", "")

        print("\n" + "=" * 60)
        print("TOKENS RECEIVED")
        print("=" * 60)

        if id_token:
            claims = decode_jwt_payload(id_token)
            print(f"  CPF (sub): {claims.get('sub')}")
            print(f"  Name:      {claims.get('name')}")
            print(f"  Email:     {claims.get('email')}")

    at_claims = decode_jwt_payload(access_token)
    print(f"\nAccess Token:")
    print(f"  sub (CPF): {at_claims.get('sub')}")
    print(f"  aud:       {at_claims.get('aud')}")
    print(f"  exp:       {at_claims.get('exp')}")

    # --- Phase 2: Generate the wallet's proof of key possession ---
    # The certify_identifier must match `mosip.certify.identifier` in
    # the server's properties. It's the audience (aud) of the proof JWT.
    # Fetch it from the well-known endpoint, or fall back to env var / default.
    certify_identifier = os.environ.get("CERTIFY_IDENTIFIER", "")
    if not certify_identifier:
        try:
            wk_url = f"{certify_url}/issuance/.well-known/openid-credential-issuer?issuer_id=MGI"
            wk_req = urllib.request.Request(wk_url, headers={"Accept": "application/json"})
            with urllib.request.urlopen(wk_req) as wk_resp:
                wk_data = json.loads(wk_resp.read())
                certify_identifier = wk_data.get("credential_issuer", "")
                print(f"\n  Fetched certify identifier from well-known: {certify_identifier}")
        except Exception as e:
            print(f"\n  WARNING: Could not fetch well-known metadata: {e}")
            certify_identifier = "https://vcdemo.crabdance.com/certify"
            print(f"  Falling back to default: {certify_identifier}")
    print(f"\nGenerating wallet proof JWT...")
    print(f"  aud (certify identifier): {certify_identifier}")
    proof_jwt, wallet_jwk = make_proof_jwt(certify_identifier, at_claims.get("aud", ""))
    print(f"  Proof JWT generated (alg=RS256)")

    # --- Phase 3: Request the Verifiable Credential from Certify ---
    print(f"\n{'=' * 60}")
    print("REQUESTING CREDENTIAL")
    print(f"{'=' * 60}")
    print(f"  Certify URL: {certify_url}")
    print(f"  Issuer: MGI")
    print(f"  Credential: ECACredential")

    result, status = request_credential(certify_url, access_token, proof_jwt)

    print(f"\n  HTTP Status: {status}")
    print(f"\nResponse:")
    print(json.dumps(result, indent=2, ensure_ascii=False))

    if "credential" in result:
        print(f"\n{'=' * 60}")
        print("CREDENTIAL ISSUED SUCCESSFULLY!")
        print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
