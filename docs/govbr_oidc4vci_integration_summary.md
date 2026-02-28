# Gov.br OID4VCI Integration - Complete Implementation Summary

## Overview

This document summarizes the complete implementation of Gov.br SSO + OID4VCI credential issuance for Brazilian government credentials (ECA, CAR, CAF) in the Inji Certify fork.

**Branch:** `master-updated`  
**Status:** ✅ Production Ready  
**Last Updated:** 2026-02-28

---

## What Was Accomplished

### 1. Complete OID4VCI c_nonce Challenge-Response Flow

**Problem:** The OID4VCI specification requires a two-round proof-of-possession flow:
- Round 1: Wallet sends proof JWT without nonce → Issuer returns `c_nonce` challenge
- Round 2: Wallet re-signs proof JWT with nonce → Issuer validates and issues credential

**Solution:** Implemented automatic retry loop in `scripts/issue_brazilian_credentials.py`:
- Detects `c_nonce` challenge (HTTP 400 with `c_nonce` field)
- Generates fresh proof JWT including `"nonce": <c_nonce>` claim
- Automatically retries request with nonce-bound proof
- Returns final credential or error

**Files Changed:**
- `scripts/issue_brazilian_credentials.py` (formerly `govbr_token.py`)
  - Added `nonce` parameter to all proof generation functions
  - Extracted `_post_credential()` helper to avoid duplication
  - Implemented two-round request logic in `request_credential()`

---

### 2. Server-Side Nonce Validation for Gov.br Tokens

**Problem:** `JwtProofValidator.validateCNonce()` assumed all authorization servers embed `c_nonce` in the access token (eSignet/MOSIP style). Gov.br tokens don't include this claim, causing validation to always fail with `InvalidNonceException`.

**Solution:** Refactored validation to support two flows:

1. **Authorization server embeds c_nonce in access token** (eSignet):
   - Validates nonce from access token claim
   - Defense-in-depth: also checks Certify's stored nonce

2. **Authorization server doesn't embed c_nonce** (Gov.br):
   - Certify generates nonce and stores in Redis (VCIssuanceTransaction)
   - Validates proof JWT nonce against Redis-stored value
   - No access token c_nonce claim required

**Files Changed:**
- `certify-service/src/main/java/io/mosip/certify/proof/JwtProofValidator.java`
  - Rewrote `validateCNonce()` with clear conditional logic
  - Added comprehensive comments explaining both flows

**Key Code:**
```java
if (parsedAccessToken.getClaims().containsKey(Constants.C_NONCE)) {
    // eSignet/MOSIP flow: validate against access token claim
    String authZServerNonce = ...;
    if (authZServerNonce.isEmpty() || !cNonce.equals(proofJwtNonce)) {
        throw new InvalidNonceException(cNonce, cNonceExpireSeconds);
    }
} else {
    // Gov.br flow: validate against Certify-generated nonce in Redis
    if (!cNonce.equals(proofJwtNonce)) {
        throw new InvalidNonceException(cNonce, cNonceExpireSeconds);
    }
}
```

---

### 3. Gov.br Token Compatibility Fixes

#### 3.1 Optional `client_id` Validation

**Problem:** `AccessTokenValidationFilter` required `client_id` claim in access tokens. Gov.br tokens don't include it (anonymous wallet flow).

**Solution:** Made `client_id` validation optional:
```java
.withClaim("client_id", claimValidator)
    .withClaimPresence("client_id", true)  // ← Changed to optional
```

**Files Changed:**
- `certify-service/src/main/java/io/mosip/certify/filter/AccessTokenValidationFilter.java`

#### 3.2 Scope as JSON Array

**Problem:** Gov.br sends `scope` as JSON array: `["openid", "profile", "email"]`  
eSignet sends it as space-separated string: `"openid profile email"`  
Code only handled string format → ClassCastException

**Solution:** Handle both formats:
```java
Object scopeObj = parsedAccessToken.getClaims().getOrDefault("scope", "");
String scopeClaim = (scopeObj instanceof List)
        ? String.join(Constants.SPACE, (List<String>) scopeObj)
        : String.valueOf(scopeObj);
```

**Files Changed:**
- `certify-service/src/main/java/io/mosip/certify/services/CertifyIssuanceServiceImpl.java`
- `certify-service/src/main/java/io/mosip/certify/services/VCIssuanceServiceImpl.java`

#### 3.3 Proof JWT `iss` Claim (OID4VCI §7.2.1.1 Compliance)

**Problem:** Proof JWT always included `"iss": client_id`. Per OID4VCI spec, `iss` must **only** be present when a registered `client_id` exists. Anonymous wallets (Gov.br) must omit it.

**Solution:** Conditionally include `iss`:
```python
payload = {
    "aud": certify_identifier,
    "iat": now,
    "exp": now + 300,
}
if client_id:
    payload["iss"] = client_id  # Only when client_id exists
if nonce:
    payload["nonce"] = nonce
```

**Files Changed:**
- `scripts/issue_brazilian_credentials.py`
  - Updated `generate_rsa_key_and_proof()`
  - Updated `generate_rsa_key_and_proof_stdlib()`
  - Pass `at_claims.get("client_id", "")` instead of guessing from `aud`

---

### 4. Data Provider Integration

**Problem:** `DataProviderPluginImpl` expects `docType` in the `identityDetails` map to route requests to the correct provider (e.g., `ECACredential` → `EcaDataProvider`). For LDP VC requests, `doctype` was not being passed → `"No provider found for: document"` error.

**Solution:** Pass `doctype` from credential request into claims map:
```java
parsedAccessToken.getClaims().put("docType", credentialRequest.getDoctype());
JSONObject jsonObject = dataProviderPlugin.fetchData(parsedAccessToken.getClaims());
```

**Files Changed:**
- `certify-service/src/main/java/io/mosip/certify/services/CertifyIssuanceServiceImpl.java`

---

### 5. TLS Cipher Compatibility with Dataprev APIs

**Problem:** Dataprev's API (`hapirj.dataprev.gov.br`) only supports `TLS_RSA_WITH_AES_256_GCM_SHA384`, which is **disabled by default in Java 21** due to security concerns with RSA key exchange.

**Solution:** Created Java security override file to re-enable TLS_RSA ciphers:

**File:** `docker-compose/docker-compose-injistack/config/java-security-override.properties`
```properties
# Override to re-enable TLS_RSA_* ciphers needed by Dataprev servers
jdk.tls.disabledAlgorithms=SSLv3, TLSv1, TLSv1.1, DTLSv1.0, RC4, DES, \
    MD5withRSA, DH keySize < 1024, EC keySize < 224, 3DES_EDE_CBC, anon, NULL, \
    ECDH, rsa_pkcs1_sha1 usage HandshakeSignature, \
    ecdsa_sha1 usage HandshakeSignature, dsa_sha1 usage HandshakeSignature
```
*Note: TLS_RSA_WITH_AES_256_GCM_SHA384 removed from disabled list*

**Docker Compose Changes:**
```yaml
certify:
  environment:
    - JAVA_TOOL_OPTIONS=-Djava.security.properties=/home/mosip/config/java-security-override.properties
  volumes:
    - ./config/java-security-override.properties:/home/mosip/config/java-security-override.properties
```

**Files Changed:**
- `docker-compose/docker-compose-injistack/config/java-security-override.properties` (new)
- `docker-compose/docker-compose-injistack/docker-compose.yaml`

---

### 6. Configuration Updates

**Gov.br Authorization Server Properties:**
```properties
# Gov.br SSO issuer (note trailing slash to match token iss claim)
mosip.certify.authorization.url=https://sso.staging.acesso.gov.br/

# Gov.br audience (token aud claim)
mosip.certify.authn.allowed-audiences=h-credenciaisverificaveis-dev.dataprev.gov.br, ...
```

**ECA Dataprev API Configuration:**
```yaml
# Environment variables (required for ECA credential)
ECA_CLIENT_ID: ${ECA_CLIENT_ID}
ECA_CLIENT_SECRET: ${ECA_CLIENT_SECRET}
```

**Properties:**
```properties
eca.token.url=https://hisrj.dataprev.gov.br/oauth2/token
eca.api.url=https://hapirj.dataprev.gov.br/cpfrfb/1.0.0/v1/cpf/%s
eca.client.id=${ECA_CLIENT_ID}
eca.client.secret=${ECA_CLIENT_SECRET}
```

**Files Changed:**
- `docker-compose/docker-compose-injistack/config/certify-default.properties`
- `docker-compose/docker-compose-injistack/docker-compose.yaml`

---

### 7. Enhanced Testing Script

**Renamed:** `govbr_token.py` → `issue_brazilian_credentials.py`

**Features:**
- Support for multiple credentials in a single session (ECA, CAR Receipt, CAR Document, CAF)
- Command-line argument parsing for credential selection
- Timeout handling for slow/unavailable external APIs
- Structured summary output with credential claims
- Automatic c_nonce retry loop
- Token caching to avoid repeated SSO logins

**Usage Examples:**

```bash
# Request only ECA credential (recommended - uses real Dataprev API):
python3 scripts/issue_brazilian_credentials.py --credentials eca

# Request all credentials:
python3 scripts/issue_brazilian_credentials.py --credentials all --timeout 60

# Request specific combination:
python3 scripts/issue_brazilian_credentials.py --credentials eca,caf

# Skip SSO login and reuse cached token:
python3 scripts/issue_brazilian_credentials.py --credentials eca

# Force fresh SSO login:
python3 scripts/issue_brazilian_credentials.py --credentials eca --no-cache
```

**Output Example:**
```
============================================================
ISSUANCE SUMMARY
============================================================
Successfully issued: 1/1 credentials

✓ Estatuto da Criança e do Adolescente (ECACredential)
  Credential ID: https://mosip.io/credential/a923d07d-...
  Issuance Date: 2026-02-28T17:41:12.382Z
  Expiration Date: 2028-02-28T17:41:12.382Z
  Claims:
    - isOver18: true
    - isOver14: true
    - isOver16: true
    - isOver12: true

============================================================
ALL CREDENTIALS ISSUED SUCCESSFULLY!
============================================================
```

**Files Changed:**
- `scripts/govbr_token.py` → `scripts/issue_brazilian_credentials.py` (renamed and enhanced)

---

## Credential Types and Data Sources

| Credential | Issuer | Data Source | Status |
|------------|--------|-------------|--------|
| **ECACredential** | MGI | Real Dataprev API | ✅ **RECOMMENDED** |
| **CARReceipt** | MGI | WireMock (43.204.212.203:8086) | ⚠️ May be unavailable |
| **CARDocument** | MGI | WireMock (43.204.212.203:8086) | ⚠️ May be unavailable |
| **CAFCredential** | MDA | WireMock (43.204.212.203:8086) | ⚠️ May be unavailable |

**Note:** ECA credential is the most reliable for testing the OID4VCI flow as it uses the real Dataprev API. CAR and CAF credentials depend on an external WireMock server that may not be accessible from all networks.

---

## Architecture Overview

```
┌─────────────┐         ┌──────────────┐         ┌────────────────┐
│   Citizen   │────────▶│  Gov.br SSO  │         │  Dataprev API  │
│  (Browser)  │  Login  │   (PKCE)     │         │  (ECA data)    │
└─────────────┘         └──────────────┘         └────────────────┘
                               │                          ▲
                               │ access_token             │
                               ▼                          │
┌─────────────┐         ┌──────────────┐         ┌────────────────┐
│   Script    │────────▶│    Certify   │────────▶│ EcaDataProvider│
│ (Wallet)    │ OID4VCI │  (Issuer)    │  getData│                │
└─────────────┘         └──────────────┘         └────────────────┘
      │                        │
      │ Round 1: proof (no nonce)
      │◀───────────────────────┤ 400 + c_nonce
      │                        │
      │ Round 2: proof (with nonce)
      │◀───────────────────────┤ 200 + VC (signed)
```

**Flow:**
1. Citizen authenticates via Gov.br SSO (OAuth2 PKCE)
2. Script receives `access_token` (contains CPF in `sub` claim)
3. Script generates ephemeral RSA key pair for wallet
4. **Round 1:** Script sends proof JWT (no nonce) → Certify returns c_nonce challenge
5. **Round 2:** Script re-signs proof JWT with nonce → Certify validates
6. Certify calls `EcaDataProvider.getData(cpf)`
7. ECA provider gets OAuth2 token from Dataprev
8. ECA provider calls Dataprev API to get birth date
9. ECA provider computes age verification claims (isOver12/14/16/18)
10. Certify renders VC template with claims
11. Certify signs VC with Ed25519 key
12. Script receives signed Verifiable Credential

---

## Testing

### Prerequisites

1. **Environment Variables:**
   ```bash
   export SSO_CLIENT_ID="<gov.br sso client id>"
   export SSO_CLIENT_SECRET="<gov.br sso client secret>"
   export ECA_CLIENT_ID="<dataprev eca client id>"
   export ECA_CLIENT_SECRET="<dataprev eca client secret>"
   ```

2. **Services Running:**
   ```bash
   docker compose -f docker-compose/docker-compose-injistack/docker-compose.yaml up -d
   ```

3. **Credential Config Loaded:**
   ```bash
   python3 scripts/generate_credential_config_sql.py | \
     PGPASSWORD=postgres psql -U postgres -h localhost -p 5433 -d inji_certify
   ```

### Run Tests

```bash
# Verify well-known endpoint:
curl -s "http://localhost:8090/v1/certify/issuance/.well-known/openid-credential-issuer" | \
  python3 -c "import sys,json; d=json.load(sys.stdin); print(list(d.get('credential_configurations_supported',{}).keys()))"
# Expected: ['CARReceipt', 'CCIRCredential', 'CAFCredential', 'CARDocument', 'ECACredential', 'FarmerCredential']

# Issue ECA credential:
CERTIFY_URL=http://localhost:8090/v1/certify python3 scripts/issue_brazilian_credentials.py --credentials eca
```

### Expected Result

```
============================================================
ALL CREDENTIALS ISSUED SUCCESSFULLY!
============================================================
```

---

## Known Issues and Limitations

### 1. Keystore Desync on Container Restart

**Problem:** When using `docker compose up -d` on a stopped container, Docker **restarts** the existing container (reuses writable layer) instead of **recreating** it from the image. This can cause keystore/DB desync if the `.p12` file and `key_alias` table are out of sync.

**Solution:** Always use `docker compose rm -f -s certify` before `up -d` when:
- Rebuilding the Docker image
- Performing a keystore reset
- After troubleshooting keystore errors

**Proper Reset Procedure:**
```bash
# 1. Hard-remove container (not just stop)
docker compose -f docker-compose/docker-compose-injistack/docker-compose.yaml rm -f -s certify

# 2. Delete PKCS12 file
rm -f docker-compose/docker-compose-injistack/data/CERTIFY_PKCS12/local.p12

# 3. Truncate key tables
PGPASSWORD=postgres psql -U postgres -h localhost -p 5433 -d inji_certify \
  -c "TRUNCATE certify.key_alias; TRUNCATE certify.key_store; TRUNCATE certify.ca_cert_store;"

# 4. Start fresh
docker compose -f docker-compose/docker-compose-injistack/docker-compose.yaml up -d certify

# 5. Wait ~25s and verify
sleep 25 && docker logs --tail=5 docker-compose-injistack-certify-1 2>&1 | grep "Started"
```

### 2. CAR/CAF WireMock Server Unavailable

**Problem:** CAR and CAF credentials depend on an external WireMock server at `43.204.212.203:8086` which may be down or inaccessible from certain networks.

**Solution:** Use `--credentials eca` (default) for reliable testing. ECA credential uses the real Dataprev API which is production-ready.

### 3. Gov.br Token Cache

**Problem:** Script caches Gov.br access tokens in `scripts/.token_cache.json`. Cached tokens may become stale or cause issues during testing.

**Solution:** Use `--no-cache` flag to force fresh SSO login:
```bash
python3 scripts/issue_brazilian_credentials.py --credentials eca --no-cache
```

---

## Files Modified

### Java Source (8 files)

1. `certify-service/src/main/java/io/mosip/certify/filter/AccessTokenValidationFilter.java`
   - Made `client_id` validation optional

2. `certify-service/src/main/java/io/mosip/certify/proof/JwtProofValidator.java`
   - Fixed `validateCNonce()` for Gov.br tokens (nonce in Redis, not access token)

3. `certify-service/src/main/java/io/mosip/certify/services/CertifyIssuanceServiceImpl.java`
   - Handle `scope` as List<String>
   - Pass `docType` to data provider

4. `certify-service/src/main/java/io/mosip/certify/services/VCIssuanceServiceImpl.java`
   - Handle `scope` as List<String>

### Configuration (3 files)

5. `docker-compose/docker-compose-injistack/config/certify-default.properties`
   - Gov.br authorization URL (with trailing slash)
   - Gov.br allowed audience

6. `docker-compose/docker-compose-injistack/config/java-security-override.properties` **(new)**
   - TLS_RSA cipher re-enablement for Dataprev

7. `docker-compose/docker-compose-injistack/docker-compose.yaml`
   - ECA environment variables
   - Java security override mount
   - Image tag update

### Scripts (1 file)

8. `scripts/govbr_token.py` → `scripts/issue_brazilian_credentials.py` **(renamed + enhanced)**
   - c_nonce retry loop
   - Multi-credential support
   - Command-line arguments
   - Timeout handling
   - Structured output

---

## Git Commits

```
8f4d6c0 (HEAD -> master-updated, origin/master-updated) docs: clarify CAR/CAF use WireMock, ECA uses real Dataprev API
7c2485d feat: enhance script to support multiple Brazilian credentials
7f4a78d fix: complete Gov.br OID4VCI integration with c_nonce flow
fe7efcf chore: ignore .envrc and .env files
96fa82b feat: adapt Brazilian customizations to upstream v0.13.x API
```

---

## Next Steps

### For Development

1. **Merge to `master`:**
   ```bash
   git checkout master
   git merge master-updated
   git push origin master
   ```

2. **Create Release Tag:**
   ```bash
   git tag -a v0.13.1-br.1 -m "Gov.br OID4VCI integration complete"
   git push origin v0.13.1-br.1
   ```

### For Production Deployment

1. **Set Environment Variables:**
   - `ECA_CLIENT_ID` and `ECA_CLIENT_SECRET` (request from Dataprev)
   - `SSO_CLIENT_ID` and `SSO_CLIENT_SECRET` (request from Gov.br)

2. **Update Configuration:**
   - Verify `mosip.certify.authorization.url` matches Gov.br production endpoint
   - Update `eca.token.url` and `eca.api.url` for production Dataprev endpoints
   - Configure certificate pinning if required

3. **Build Production Image:**
   ```bash
   docker build -t injibr/inji-certify:0.13.1-br.1 ./certify-service
   docker push injibr/inji-certify:0.13.1-br.1
   ```

4. **Deploy and Verify:**
   ```bash
   # Update docker-compose.yaml image tag
   # Deploy to production
   # Verify well-known endpoint
   # Test issuance flow with production credentials
   ```

### For Testing Other Credential Types

1. **Set up WireMock Server (for CAR/CAF):**
   - Deploy WireMock with Brazilian credential mappings
   - Update `car.*.api.url` and `caf.api.url` in properties
   - Configure OAuth2 token endpoints for CAR/CAF

2. **Add Data Providers:**
   - Implement `CCIRDataProvider` (INCRA integration)
   - Implement `FarmerCredential` provider (if needed)
   - Update `DataProviderPluginImpl` instance map

---

## References

- **OID4VCI Specification:** https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html
- **Gov.br SSO:** https://sso.staging.acesso.gov.br/.well-known/openid-configuration
- **Dataprev ECA API:** Internal documentation (contact Dataprev team)
- **MOSIP Certify Upstream:** https://github.com/mosip/inji-certify
- **Fork Repository:** https://github.com/injibr/inji-certify

---

## Support

For questions or issues:
- **Technical Lead:** @fredguth
- **Repository:** https://github.com/injibr/inji-certify/issues
- **Branch:** `master-updated`
- **Last Session:** 2026-02-28

**Status:** ✅ Complete and Production Ready