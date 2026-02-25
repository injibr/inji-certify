#!/usr/bin/env python3
"""Fases 2+3 do OID4VCI — requer ACCESS_TOKEN no ambiente."""
import os, base64, json, time, urllib.request
from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.hazmat.primitives import hashes

CERTIFY_URL = os.environ.get("CERTIFY_URL",
    "https://injicertify.credenciaisverificaveis-hml.dataprev.gov.br/v1/certify")
CERTIFY_ID = os.environ.get("CERTIFY_IDENTIFIER",
    "https://injicertify.credenciaisverificaveis-hml.dataprev.gov.br")
access_token = os.environ["ACCESS_TOKEN"]
# client_id = claim "aud" do access_token
at_payload = json.loads(base64.urlsafe_b64decode(
    access_token.split(".")[1] + "=="))
client_id = at_payload["aud"]

b64url = lambda d: base64.urlsafe_b64encode(
    d if isinstance(d, bytes) else d.encode()).rstrip(b"=").decode()
int2b = lambda n: n.to_bytes((n.bit_length() + 7) // 8, "big")

# — Fase 2: Proof JWT —
key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
pub = key.public_key().public_numbers()
jwk = {"kty": "RSA", "n": b64url(int2b(pub.n)), "e": b64url(int2b(pub.e))}
header = {"typ": "openid4vci-proof+jwt", "alg": "RS256", "jwk": jwk}
now = int(time.time())
payload = {"iss": client_id, "aud": CERTIFY_ID, "iat": now, "exp": now + 300}
si = f"{b64url(json.dumps(header))}.{b64url(json.dumps(payload))}"
proof_jwt = f"{si}.{b64url(key.sign(si.encode(), padding.PKCS1v15(), hashes.SHA256()))}"

# — Fase 3: Credential Request —
body = json.dumps({"format": "ldp_vc", "issuerId": "MGI", "doctype": "ECACredential",
    "credential_definition": {"@context": ["https://www.w3.org/2018/credentials/v1"],
        "type": ["VerifiableCredential", "ECACredential"]},
    "proof": {"proof_type": "jwt", "jwt": proof_jwt}})
req = urllib.request.Request(f"{CERTIFY_URL}/issuance/credential", method="POST",
    data=body.encode(), headers={"Content-Type": "application/json",
        "Authorization": f"Bearer {access_token}"})
try:
    with urllib.request.urlopen(req) as resp:
        print(json.dumps(json.loads(resp.read())["credential"], indent=2))
except urllib.error.HTTPError as e:
    print(f"HTTP {e.code}: {e.read().decode()}")
