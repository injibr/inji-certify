# Local Development Environment Setup

This guide walks you through setting up Inji Certify for local development and testing the credential issuance flow with the Brazilian data providers (CAR, CAF, CCIR, ECA).

## Prerequisites

- **Java 21** (e.g., Eclipse Temurin or OpenJDK)
- **Maven** (3.8+)
- **PostgreSQL** running locally on port 5432
- **Git**

Verify your setup:

```bash
java -version    # Should show 21.x
mvn -version     # Should show 3.8+
psql --version   # Should show 14+
```

## Part A: Development Environment Setup

### 1. Clone and Build

```bash
git clone <repo-url> inji-certify
cd inji-certify
git checkout entrega

# Build all modules (skip GPG signing and javadoc for local dev)
mvn clean install -Dgpg.skip=true -Dmaven.javadoc.skip=true -DskipTests
```

Expected output: `BUILD SUCCESS` for all 4 modules (certify, certify-integration-api, certify-core, certify-service).

### 2. Initialize the Database

The project ships DB init scripts in `db_scripts/mosip_certify/`, but they are **incomplete** — they don't create the `certify_keys` table required by the application. Use the Docker init SQL instead, which has the full schema and seed data.

#### Option A: Fresh database (recommended)

If you don't have an `inji_certify` database yet:

```bash
# Create the database and schema
psql -U postgres -h localhost -p 5432 -c "CREATE DATABASE inji_certify;"
psql -U postgres -h localhost -p 5432 -d inji_certify -c "CREATE SCHEMA IF NOT EXISTS certify;"
psql -U postgres -h localhost -p 5432 -d inji_certify -c "ALTER DATABASE inji_certify SET search_path TO certify,public;"

# Run the full init script (creates tables, templates, and well-known metadata)
psql -U postgres -h localhost -p 5432 -d inji_certify \
  -f docker-compose/docker-compose-injistack/certify_init.sql
```

#### Option B: Existing database missing `certify_keys`

If you already ran `db_scripts/mosip_certify/deploy.sh` but the app fails with `relation "certify_keys" does not exist`:

```bash
# Create the missing table and populate it
psql -U postgres -h localhost -p 5432 -d inji_certify \
  -f docker-compose/docker-compose-injistack/certify_init.sql
```

This is safe to re-run — it uses `CREATE TABLE` (will error on existing tables but continue) and `INSERT` for the seed data.

#### Verify the database

Run all verification queries at once:

```bash
psql -U postgres -h localhost -p 5432 -d inji_certify <<'EOF'
-- 1. Tables (expect 7+)
SELECT table_name
FROM information_schema.tables
WHERE table_schema='certify'
ORDER BY table_name;

-- 2. Issuer metadata (expect MGI, INCRA, MDA)
-- Note: ECACredential is inside MGI, not a separate issuer
SELECT config_key AS issuer FROM certify.certify_keys;

-- 3. Credential templates (expect 7+ rows)
SELECT credential_type, LEFT(context, 40) AS context
FROM certify.credential_template
ORDER BY credential_type;

-- 4. Key policies (expect CERTIFY_SERVICE, ROOT)
SELECT app_id, key_validity_duration AS validity_days
FROM certify.key_policy_def;
EOF
```

Expected output summary:

| Check | Expected |
|-------|----------|
| Tables | `ca_cert_store`, `certify_keys`, `credential_template`, `key_alias`, `key_policy_def`, `key_store`, `rendering_template` |
| Issuers | `MGI`, `INCRA`, `MDA` |
| Credential templates | `CARDocument`, `CARReceipt`, `CARReceiptAST`, `CARReceiptPCT`, `CAFCredential`, `CCIRCredential`, `ECACredential`, `FarmerCredential` (each with `VerifiableCredential`) |
| Key policies | `CERTIFY_SERVICE` (1095 days), `ROOT` (2920 days) |

### 3. PKCS12 Keystore

The application needs a PKCS12 keystore containing the private key used to sign Verifiable Credentials. The MOSIP keymanager **auto-generates** the p12 file on first startup — you only need to create the directory:

```bash
cd certify-service
mkdir -p CERTIFY_PKCS12
```

> **Important:** Do NOT create the p12 file yourself (e.g., with `keytool`). The keymanager uses its own internal format and will fail with `keystore password was incorrect` if it finds a file it didn't create.

Verify that `application-local.properties` has:

```properties
mosip.kernel.keymanager.hsm.config-path=CERTIFY_PKCS12/local.p12
mosip.kernel.keymanager.hsm.keystore-type=PKCS12
mosip.kernel.keymanager.hsm.keystore-pass=local
```

> **Security note:** PFX/P12 files contain private keys and must **never** be committed to git. They are excluded via `.gitignore`. Anyone with this file and its password can forge signed credentials.

### 4. Start the Service

```bash
cd certify-service
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The service starts on **port 8090**. Wait for the Spring Boot banner and log messages to settle (~5 seconds).

> **Note:** You'll see a warning about Spring Cloud Config Server connection refused — this is expected for local development. The `application-local.properties` file provides all configuration locally.

### 5. Verify the Service

Quick smoke test — checks all issuers respond and lists their credential types:

```bash
BASE=http://localhost:8090/v1/certify

# Health check (may report DOWN due to HSM health check — this is normal for local PKCS12 setup)
curl -s $BASE/actuator/health

# Verify each issuer returns its credential types
for ISSUER in MGI INCRA MDA; do
  echo -n "$ISSUER: "
  curl -s "$BASE/issuance/.well-known/openid-credential-issuer?issuer_id=$ISSUER" \
    | python3 -c "import sys,json; print(', '.join(json.load(sys.stdin).get('credential_configurations_supported',{}).keys()))"
done

# Verify DID document exists
curl -s $BASE/issuance/.well-known/did.json | python3 -c "import sys,json; d=json.load(sys.stdin); print('DID:', d.get('id','ERROR'))"
```

Expected output:

```
{"status":"DOWN"}
MGI: CARReceipt, CARDocument, ECACredential
INCRA: CCIRCredential
MDA: CAFCredential
DID: did:web:...
```

To inspect the full metadata for any issuer:

```bash
curl -s "http://localhost:8090/v1/certify/issuance/.well-known/openid-credential-issuer?issuer_id=MGI" | python3 -m json.tool
```

### Available Issuers and Credential Types

| Issuer ID | Credential Types | Backend API |
|-----------|-----------------|-------------|
| **MGI** | `CARDocument`, `CARReceipt`, `ECACredential` | SICAR (CAR registry), Dataprev (ECA age verification) |
| **INCRA** | `CCIRCredential` | SERPRO/Dataprev (CCIR land certificate) |
| **MDA** | `CAFCredential` | CAF (family farming registry) |

### Key Configuration (application-local.properties)

The local profile points the CAR/CAF data provider APIs to a **WireMock server** at `http://43.204.212.203:8086`. ECA connects to the real Dataprev APIs using credentials from environment variables.

```properties
# WireMock (used by CAR/CAF token clients)
wiremock.enabled=false

# CAR APIs
car.token.url=http://43.204.212.203:8086/oauth2/token
car.document.api.url=http://43.204.212.203:8086/sicar/demonstrativo/1.0/%s
car.receipt.api.url=http://43.204.212.203:8086/sicar/recibo/1.0/%s
car.registration.number.url=http://43.204.212.203:8086/sicar/cpfcnpj/1.0/%s

# CAF APIs
caf.token.url=http://43.204.212.203:8086/oauth2/token
caf.api.url=http://43.204.212.203:8086/prod-api-caf-mir/api/consulta-externa/mir/pessoa-fisica/%s

# ECA APIs (real Dataprev — credentials via environment variables)
eca.token.url=https://hisrj.dataprev.gov.br/oauth2/token
eca.api.url=https://hapirj.dataprev.gov.br/cpfrfb/1.0.0/v1/cpf/%s
eca.client.id=${ECA_CLIENT_ID}
eca.client.secret=${ECA_CLIENT_SECRET}

# Authorization service
mosip.certify.authorization.url=https://sso.staging.acesso.gov.br
mosip.certify.authn.jwk-set-uri=https://sso.staging.acesso.gov.br/jwk.json
```

Before starting the service, export the ECA credentials:
```bash
export ECA_CLIENT_ID=your_client_id
export ECA_CLIENT_SECRET=your_client_secret
```

Make sure the WireMock server (`43.204.212.203:8086`) is reachable for CAR/CAF testing. If not, those credential types will fail at the token/data fetch step.

---

## Part B: Testing the Issuance Flow

The credential issuance follows the **OpenID for Verifiable Credential Issuance (OID4VCI)** protocol. The full flow requires an Authorization Server (eSignet / Gov.br SSO), but you can test individual parts locally.

### API Endpoints

| Endpoint | Method | Auth Required | Description |
|----------|--------|---------------|-------------|
| `/v1/certify/issuance/.well-known/openid-credential-issuer?issuer_id={id}` | GET | No | Issuer metadata |
| `/v1/certify/issuance/.well-known/did.json` | GET | No | DID document |
| `/v1/certify/issuance/credential` | POST | Yes (Bearer) | Issue credential (latest) |
| `/v1/certify/issuance/vd11/credential` | POST | Yes (Bearer) | Issue credential (OID4VCI draft 11) |
| `/v1/certify/issuance/vd12/credential` | POST | Yes (Bearer) | Issue credential (OID4VCI draft 12) |
| `/v1/certify/system-info/certificate?applicationId=CERTIFY_VC_SIGN_ED25519&referenceId=ED25519_SIGN` | GET | No | Signing certificate |
| `/v1/certify/actuator/health` | GET | No | Health check |

### Step 1: Verify Open Endpoints (no auth required)

```bash
BASE=http://localhost:8090/v1/certify

# 1. Issuer metadata (pick any: MGI, INCRA, MDA)
curl -s "$BASE/issuance/.well-known/openid-credential-issuer?issuer_id=MGI" | python3 -m json.tool

# 2. DID document
curl -s "$BASE/issuance/.well-known/did.json" | python3 -m json.tool

# 3. Signing certificate (Ed25519)
curl -s "$BASE/system-info/certificate?applicationId=CERTIFY_VC_SIGN_ED25519&referenceId=ED25519_SIGN" | python3 -m json.tool
```

### Step 2: Full Issuance Flow (requires Authorization Server)

The credential endpoint (`/issuance/credential`) is protected. It requires a valid **Bearer token** issued by the configured Authorization Server (Gov.br SSO at `https://sso.staging.acesso.gov.br`).

The full OID4VCI flow is:

```
1. Client → AuthZ Server: Authenticate user (OAuth2 Authorization Code flow)
2. AuthZ Server → Client: Returns access_token with user's CPF in `sub` claim
3. Client → Certify: POST /issuance/credential with Bearer token + proof
4. Certify → validates token using AuthZ server's JWK set
5. Certify → DataProviderPlugin: fetchData({sub: "<cpf>", docType: "<type>"})
6. DataProviderPlugin → WireMock/SICAR API: Gets OAuth2 token, fetches data
7. Certify → Signs VC with Ed25519 key
8. Certify → Client: Returns signed Verifiable Credential
```

#### Credential Request Format

```bash
curl -X POST http://localhost:8090/v1/certify/issuance/credential \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token_from_authz_server>" \
  -d '{
    "format": "ldp_vc",
    "credential_definition": {
      "type": ["VerifiableCredential", "CARDocument"]
    },
    "proof": {
      "proof_type": "jwt",
      "jwt": "<signed_jwt_proof>"
    }
  }'
```

The `type` array determines which data provider is called:
- `["VerifiableCredential", "CARDocument"]` → `CARDataProvider`
- `["VerifiableCredential", "CARReceipt"]` → `CARReceiptDataProvider`
- `["VerifiableCredential", "CAFCredential"]` → `CAFDataProvider`
- `["VerifiableCredential", "CCIRCredential"]` → `CCIRDataProvider`
- `["VerifiableCredential", "ECACredential"]` → `EcaDataProvider` (returns `isOver12`, `isOver14`, `isOver16`, `isOver18` booleans)

### Step 3: Using Postman Collections

Pre-built Postman collections are available in `docs/postman-collections/`:

- `inji-certify-with-mock-identity.postman_collection.json` — For mock identity testing
- `inji-certify-with-mock-identity.postman_environment.json` — Environment variables

Import these into Postman and update the environment variables to point to `http://localhost:8090`.

> **Note:** The Postman collections require the [pmlib library](https://joolfe.github.io/postman-util-lib/). Follow the instructions in the collection's pre-request scripts to install it.

---

## Troubleshooting

### `relation "certify_keys" does not exist`

The DB init scripts in `db_scripts/` don't include this table. Run the Docker init script:

```bash
psql -U postgres -h localhost -p 5432 -d inji_certify \
  -f docker-compose/docker-compose-injistack/certify_init.sql
```

### `unsupported_openid4vci_version`

You're calling the well-known endpoint without specifying `issuer_id`. In DataProvider mode, use:

```
?issuer_id=MGI    (not ?version=vd11)
```

### Health endpoint returns `DOWN`

This is expected when using a PKCS12 keystore locally. The HSM health check reports DOWN but the service is fully functional.

### `Connection refused` to Spring Cloud Config Server

Expected in local development. The `application-local.properties` provides all configuration locally, so the config server is not needed.

### WireMock server unreachable

If credential issuance fails with connection errors to `43.204.212.203:8086`, the WireMock server may be down or unreachable from your network. Contact the team to verify its status.

### ECA credential issuance fails with 401/connection error

Verify the environment variables are set before starting the service:
```bash
echo $ECA_CLIENT_ID
echo $ECA_CLIENT_SECRET
```

If empty, export them and restart the service.

### Port 8090 already in use

```bash
# Find and kill the process using port 8090
lsof -i :8090
kill <PID>
```
