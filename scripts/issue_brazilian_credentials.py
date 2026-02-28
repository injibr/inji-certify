#!/usr/bin/env python3
"""
Brazilian Credentials Issuance via Gov.br SSO + Inji Certify
=============================================================

This script automates the full OpenID for Verifiable Credential Issuance
(OID4VCI) flow against a local Inji Certify instance, using Gov.br SSO
as the Authorization Server. It requests multiple Brazilian government
credentials (ECA, CAR, CAF) in a single session, simulating what a wallet
app (e.g., Inji Wallet) does to obtain Verifiable Credentials.

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

Credentials Available:
    - ECACredential  : Estatuto da Criança e do Adolescente (age verification)
                       Uses real Dataprev API - RECOMMENDED for testing
    - CARReceipt     : Cadastro Ambiental Rural receipt
                       Uses WireMock server (may be unavailable)
    - CARDocument    : Cadastro Ambiental Rural document
                       Uses WireMock server (may be unavailable)
    - CAFCredential  : Cadastro da Agricultura Familiar
                       Uses WireMock server (may be unavailable)

Note: CAR and CAF credentials require a WireMock server at 43.204.212.203:8086
      which may not be accessible. ECA credential uses the real Dataprev API
      and is the most reliable option for testing the OID4VCI flow.

Usage:
    python3 scripts/issue_brazilian_credentials.py [OPTIONS]

Options:
    --skip-login       Skip the SSO login flow and reuse an existing token
                       from the ACCESS_TOKEN environment variable.
    --no-cache         Ignore cached tokens and force a fresh SSO login
    --credentials TYPE Request specific credential types (comma-separated)
                       Available: eca, car-receipt, car-doc, caf, all
                       Default: eca (recommended - uses real Dataprev API)
                       Example: --credentials eca,caf
                       Note: car-* and caf require WireMock server access
    --timeout SECONDS  Timeout for each credential request (default: 30)

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


def generate_rsa_key_and_proof(certify_identifier, client_id, nonce=None):
    """
    Generate an ephemeral RSA-2048 key pair and build a signed OID4VCI proof JWT.
    Uses the `cryptography` library (preferred method).

    In a real wallet, this key pair would be persistent and stored securely.
    For testing, we generate a throwaway key each time.

    Args:
        certify_identifier: The credential issuer URL (used as JWT `aud`).
        client_id: OAuth2 client_id; if non-empty, added as JWT `iss`.
        nonce: Optional c_nonce from Certify's challenge response. When
               provided it is included in the JWT payload as `nonce`,
               which satisfies the OID4VCI §7.2.1.1 proof-of-possession
               requirement.
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
    # iss is ONLY included when a registered client_id is known (OID4VCI §7.2.1.1).
    # Anonymous wallets (no client registration, as in the Gov.br flow) must omit iss.
    now = int(time.time())
    payload = {
        "aud": certify_identifier,    # must match mosip.certify.identifier
        "iat": now,
        "exp": now + 300,             # 5 minute validity
    }
    if client_id:
        payload["iss"] = client_id
    if nonce:
        payload["nonce"] = nonce

    # Sign: encode header and payload, then sign with the private key
    signing_input = f"{b64url(json.dumps(header))}.{b64url(json.dumps(payload))}"
    signature = private_key.sign(
        signing_input.encode(),
        padding.PKCS1v15(),
        hashes.SHA256(),
    )

    proof_jwt = f"{signing_input}.{b64url(signature)}"
    return proof_jwt, jwk


def generate_rsa_key_and_proof_stdlib(certify_identifier, client_id, nonce=None):
    """
    Fallback proof JWT generation using the openssl CLI.
    Used when the `cryptography` Python library is not installed.
    Functionally identical to generate_rsa_key_and_proof().

    Args:
        certify_identifier: The credential issuer URL (used as JWT `aud`).
        client_id: OAuth2 client_id; if non-empty, added as JWT `iss`.
        nonce: Optional c_nonce from Certify's challenge response. When
               provided it is included in the JWT payload as `nonce`.
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

    # iss is only included when a registered client_id is known (OID4VCI §7.2.1.1).
    now = int(time.time())
    payload = {
        "aud": certify_identifier,
        "iat": now,
        "exp": now + 300,
    }
    if client_id:
        payload["iss"] = client_id
    if nonce:
        payload["nonce"] = nonce

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


def make_proof_jwt(certify_identifier, client_id, nonce=None):
    """
    Try the cryptography library first, fall back to openssl CLI.

    Args:
        certify_identifier: The credential issuer URL (used as JWT `aud`).
        client_id: OAuth2 client_id; if non-empty, added as JWT `iss`.
        nonce: Optional c_nonce to embed in the proof JWT payload.
    """
    try:
        from cryptography.hazmat.primitives.asymmetric import rsa  # noqa: F401
        return generate_rsa_key_and_proof(certify_identifier, client_id, nonce=nonce)
    except ImportError:
        print("  (cryptography library not found, using openssl CLI)")
        return generate_rsa_key_and_proof_stdlib(certify_identifier, client_id, nonce=nonce)


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

def _post_credential(certify_url, access_token, proof_jwt, doc_type):
    """
    Send a single POST /issuance/credential request and return (response_json, status).

    This is an internal helper used by request_credential() to avoid duplicating
    the HTTP POST logic across the initial attempt and the c_nonce retry.
    """
    url = f"{certify_url}/issuance/credential"
    body = json.dumps({
        "format": "ldp_vc",
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
        raw = e.read().decode()
        if raw:
            try:
                return json.loads(raw), e.code
            except json.JSONDecodeError:
                return {"error": f"HTTP {e.code}", "detail": e.reason, "body": raw}, e.code
        return {"error": f"HTTP {e.code}", "detail": e.reason, "body": ""}, e.code


def request_credential(certify_url, access_token, certify_identifier, client_id,
                       doc_type="ECACredential", timeout=30):
    """
    Request a Verifiable Credential from the Certify issuance endpoint,
    automatically handling the OID4VCI c_nonce challenge-response flow.

    OID4VCI §7.2.1.1 defines a two-round proof-of-possession protocol:
      Round 1 — Send a credential request with a proof JWT that has no nonce.
      Certify rejects it with HTTP 400, error="invalid_proof", and returns a
      fresh c_nonce (a server-generated challenge).
      Round 2 — Re-generate the proof JWT including "nonce": c_nonce, then
      re-submit the credential request. Certify validates the nonce, verifies
      the JWT signature, and issues the credential.

    Args:
        certify_url: Base URL of the Certify service.
        access_token: Bearer token from Gov.br SSO (proves citizen identity).
        certify_identifier: credential_issuer URL from the well-known endpoint
                            (used as the `aud` claim in the proof JWT).
        client_id: OAuth2 client_id from the access token; empty string for
                   anonymous wallets (Gov.br flow omits it, so proof JWT also
                   omits the `iss` claim per spec).
        doc_type: The credential type to request (e.g. "ECACredential").
        timeout: Timeout in seconds for each HTTP request (default: 30).

    Returns:
        A tuple of (response_json, http_status_code). On success (200),
        response_json contains a "credential" key with the signed VC.
    """
    import socket

    # Set socket timeout for urllib
    default_timeout = socket.getdefaulttimeout()
    socket.setdefaulttimeout(timeout)

    try:
        # --- Round 1: initial request, no nonce ---
        print("  Round 1: sending credential request (no nonce)...")
        proof_jwt, _ = make_proof_jwt(certify_identifier, client_id)
        result, status = _post_credential(certify_url, access_token, proof_jwt, doc_type)

        if status == 400 and "c_nonce" in result:
            # Certify issued a challenge — extract the nonce and retry
            c_nonce = result["c_nonce"]
            c_nonce_expires_in = result.get("c_nonce_expires_in", "?")
            print(f"  Got c_nonce challenge: {c_nonce!r} (expires in {c_nonce_expires_in}s)")

            # --- Round 2: re-sign proof JWT with the nonce, re-submit ---
            print("  Round 2: re-submitting with nonce-bound proof JWT...")
            proof_jwt, _ = make_proof_jwt(certify_identifier, client_id, nonce=c_nonce)
            result, status = _post_credential(certify_url, access_token, proof_jwt, doc_type)

        return result, status
    except Exception as e:
        print(f"  ✗ Request failed: {str(e)}")
        return {"error": "timeout", "error_description": str(e)}, 0
    finally:
        socket.setdefaulttimeout(default_timeout)


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
# Token Cache
# =============================================================================
#
# To avoid opening the browser for SSO login on every run, tokens are cached
# in a .token_cache.json file next to this script. The cache includes the
# access_token and its expiration time. On subsequent runs, if the cached
# token is still valid (with a 60-second safety margin), it is reused.
#
# Use --no-cache to force a fresh login.
# =============================================================================

TOKEN_CACHE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".token_cache.json")


def load_cached_tokens():
    """Load tokens from cache file if it exists and the access_token is not expired."""
    try:
        with open(TOKEN_CACHE_FILE, "r") as f:
            cache = json.load(f)
        access_token = cache.get("access_token", "")
        if not access_token:
            return None
        claims = decode_jwt_payload(access_token)
        exp = claims.get("exp", 0)
        # 60-second safety margin to avoid using a token that's about to expire
        if time.time() < exp - 60:
            return cache
        print("Cached token expired, will re-authenticate.")
        return None
    except (FileNotFoundError, json.JSONDecodeError, Exception):
        return None


def save_tokens_to_cache(tokens):
    """Save tokens to cache file."""
    with open(TOKEN_CACHE_FILE, "w") as f:
        json.dump(tokens, f, indent=2)
    print(f"  Tokens cached to {TOKEN_CACHE_FILE}")


# =============================================================================
# Main: Orchestrates all three phases
# =============================================================================

def main():
    # Parse command-line arguments
    skip_login = "--skip-login" in sys.argv
    no_cache = "--no-cache" in sys.argv

    # Parse credential selection
    requested_creds = ["eca"]  # Default to ECA only (fastest)
    timeout = 30

    for i, arg in enumerate(sys.argv):
        if arg == "--credentials" and i + 1 < len(sys.argv):
            cred_arg = sys.argv[i + 1].lower()
            if cred_arg == "all":
                requested_creds = ["eca", "car-receipt", "car-doc", "caf"]
            else:
                requested_creds = [c.strip() for c in cred_arg.split(",")]
        elif arg == "--timeout" and i + 1 < len(sys.argv):
            timeout = int(sys.argv[i + 1])

    certify_url = os.environ.get("CERTIFY_URL", "http://localhost:8090/v1/certify")
    client_id = os.environ.get("SSO_CLIENT_ID")
    client_secret = os.environ.get("SSO_CLIENT_SECRET")

    if not skip_login and (not client_id or not client_secret):
        print("ERROR: Set SSO_CLIENT_ID and SSO_CLIENT_SECRET environment variables")
        sys.exit(1)

    # --- Phase 1: Authenticate the citizen via Gov.br SSO ---
    access_token = None
    id_token = ""

    if skip_login:
        access_token = os.environ.get("ACCESS_TOKEN")
        if not access_token:
            print("ERROR: Set ACCESS_TOKEN environment variable when using --skip-login")
            sys.exit(1)
        print("Using ACCESS_TOKEN from environment")

    # Try loading from cache (unless --skip-login or --no-cache)
    if not access_token and not no_cache:
        cached = load_cached_tokens()
        if cached:
            access_token = cached["access_token"]
            id_token = cached.get("id_token", "")
            print("Using cached tokens (still valid)")
            print("\n" + "=" * 60)
            print("TOKENS (cached)")
            print("=" * 60)
            if id_token:
                claims = decode_jwt_payload(id_token)
                print(f"  CPF (sub): {claims.get('sub')}")
                print(f"  Name:      {claims.get('name')}")
                print(f"  Email:     {claims.get('email')}")

    # Fall back to SSO login if we still don't have a token
    if not access_token:
        tokens = do_sso_login(client_id, client_secret)
        access_token = tokens["access_token"]
        id_token = tokens.get("id_token", "")
        save_tokens_to_cache(tokens)

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
    # No client_id in Gov.br tokens — pass empty string so iss is omitted from proof JWT
    wallet_client_id = at_claims.get("client_id", "")
    print(f"  client_id for proof JWT: {wallet_client_id!r} (empty → iss omitted per OID4VCI §7.2.1.1)")

    # --- Phase 3: Request Verifiable Credentials from Certify ---
    print(f"\n{'=' * 60}")
    print("REQUESTING CREDENTIALS")
    print(f"{'=' * 60}")
    print(f"  Certify URL: {certify_url}")

    # Define all available credentials
    all_credentials = {
        "eca": ("ECACredential", "MGI", "Estatuto da Criança e do Adolescente"),
        "car-receipt": ("CARReceipt", "MGI", "CAR Receipt (Cadastro Ambiental Rural)"),
        "car-doc": ("CARDocument", "MGI", "CAR Document"),
        "caf": ("CAFCredential", "MDA", "CAF (Cadastro da Agricultura Familiar)"),
    }

    # Build list of credentials to request based on user selection
    credentials_to_request = []
    for cred_key in requested_creds:
        if cred_key in all_credentials:
            credentials_to_request.append(all_credentials[cred_key])
        else:
            print(f"WARNING: Unknown credential type '{cred_key}', skipping")

    if not credentials_to_request:
        print("ERROR: No valid credentials selected")
        sys.exit(1)

    print(f"  Selected credentials: {', '.join([c[2] for c in credentials_to_request])}")
    print(f"  Request timeout: {timeout}s per credential")

    results = []
    for doc_type, issuer, description in credentials_to_request:
        print(f"\n{'─' * 60}")
        print(f"Requesting: {description}")
        print(f"  Issuer: {issuer}")
        print(f"  Credential Type: {doc_type}")

        result, status = request_credential(
            certify_url,
            access_token,
            certify_identifier,
            wallet_client_id,
            doc_type=doc_type,
            timeout=timeout,
        )

        print(f"  HTTP Status: {status}")

        if status == 200 and "credential" in result:
            print(f"  ✓ SUCCESS")
            results.append((doc_type, description, result, status))
        else:
            print(f"  ✗ FAILED")
            print(f"  Error: {result.get('error', 'unknown')}")
            print(f"  Details: {result.get('error_description', result.get('detail', 'N/A'))}")

            # Provide helpful hint for CAR/CAF failures
            if doc_type in ["CARReceipt", "CARDocument", "CAFCredential"] and (status == 0 or status >= 500):
                print(f"  Note: {doc_type} requires WireMock server at 43.204.212.203:8086")
                print(f"        This server may be down or inaccessible from your network.")
                print(f"        Consider using --credentials eca for testing (uses real Dataprev API)")

    # --- Display Summary ---
    print(f"\n{'=' * 60}")
    print("ISSUANCE SUMMARY")
    print(f"{'=' * 60}")
    print(f"Successfully issued: {len(results)}/{len(credentials_to_request)} credentials\n")

    for doc_type, description, result, status in results:
        credential = result.get("credential", {})
        cred_subject = credential.get("credentialSubject", {})

        print(f"✓ {description} ({doc_type})")
        print(f"  Credential ID: {credential.get('id', 'N/A')}")
        print(f"  Issuance Date: {credential.get('issuanceDate', 'N/A')}")
        print(f"  Expiration Date: {credential.get('expirationDate', 'N/A')}")

        # Show key claims from credentialSubject
        if cred_subject:
            print(f"  Claims:")
            for key, value in cred_subject.items():
                if key != "id":  # Skip the DID
                    print(f"    - {key}: {value}")
        print()

    if len(results) == len(credentials_to_request):
        print(f"{'=' * 60}")
        print("ALL CREDENTIALS ISSUED SUCCESSFULLY!")
        print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
