# Customizations of the injibr/inji-certify Fork

**Base upstream:** [mosip/inji-certify](https://github.com/mosip/inji-certify) v0.13.1
**Branch:** `master-updated`

This document describes all customizations applied by this fork relative to the upstream
repository `mosip/inji-certify`. The goal of the fork is to integrate Inji Certify with
Brazilian government services for issuing Verifiable Credentials (VCs) for documents
such as CAF, CAR, CCIR, and ECA.

---

## Architecture Overview

The upstream provides a generic VC issuance platform based on the OID4VCI protocol
(OpenID for Verifiable Credential Issuance). This fork extends that platform by adding:

1. **Data Providers** — plugins that fetch citizen data from Brazilian government APIs
2. **Velocity Template Engines** — custom formatters for the specific JSON structures of each API
3. **Audit Trail** — recording of all VC issuance attempts in the database
4. **Metadata Storage** — credential configuration via database (table `certify_keys`)
5. **Scripts and tools** — OID4VCI testing, CI pipelines, setup guides

The fork's approach is non-invasive: virtually all upstream files remain unchanged.
Customizations are added via new files using Spring extension points (SPI, AOP,
`@ConditionalOnProperty`).

---

## 1. Data Providers (certify-integration-api)

The core of the Brazilian customization. Implements the upstream `DataProviderPlugin` SPI
to fetch citizen data from government APIs via CPF (Brazilian tax ID).

### Interface and Dispatcher

| File | Description |
|------|-------------|
| `api/dataprovider/DataProviderService.java` | Interface with `getDocumentType()` and `getData(cpfNumber)`. Each Brazilian provider implements this interface. |
| `api/dataprovider/impl/DataProviderPluginImpl.java` | Implementation of the `DataProviderPlugin` SPI. Routes requests to the correct `DataProviderService` based on the `docType` field. Active only when `mosip.certify.dataprovider.enabled=true`. |

### Data Providers

| File | docType | Description |
|------|---------|-------------|
| `impl/CAFDataProvider.java` | `CAFCredential` | Family Farmer Registry (Cadastro de Agricultores Familiares). Fetches data via `CafTokenClient` + CAF API. |
| `impl/CARDataProvider.java` | `CARDocument` | Rural Environmental Registry — document (Cadastro Ambiental Rural). Resolves CPF → property code via `SicarCpfCnpjClient`, then fetches the CAR document. |
| `impl/CARReceiptDataProvider.java` | `CARReceipt` | CAR registration receipt. Same CPF → property resolution, fetches the receipt. |
| `impl/CCIRDataProvider.java` | `CCIRCredential` | Rural Property Registration Certificate (INCRA/SERPRO). **Partially implemented** — uses a hardcoded trial token and URL. |
| `impl/EcaDataProvider.java` | `ECACredential` | Child and Adolescent Statute (Estatuto da Criança e do Adolescente). Fetches date of birth via the Dataprev API and computes age brackets (`isOver12`, `isOver14`, `isOver16`, `isOver18`). |

### OAuth2 Clients

| File | Description |
|------|-------------|
| `impl/CafTokenClient.java` | `client_credentials` token for the CAF API. Supports WireMock mode (`wiremock.enabled=true`). |
| `impl/CarTokenClient.java` | `client_credentials` token for the CAR/SICAR APIs. |
| `impl/EcaTokenClient.java` | `client_credentials` token for the Dataprev API (ECA). |

### Utilities

| File | Description |
|------|-------------|
| `impl/SicarCpfCnpjClient.java` | Translates CPF/CNPJ to a SICAR property code (`codigoImovel`). Prerequisite for `CARDataProvider` and `CARReceiptDataProvider`. |
| `api/config/WebClientConfig.java` | `@Configuration` that creates a shared `WebClient` bean. Active when `mosip.certify.dataprovider.enabled=true`. |

### Required Properties

All data providers require `mosip.certify.dataprovider.enabled=true` to be activated.
Each provider also needs its own specific properties:

```properties
# CAF
caf.token.url=...
caf.client.id=...
caf.client.secret=...
caf.api.url=...

# CAR
car.token.url=...
car.client.id=...
car.client.secret=...
car.registration.number.url=...
car.document.api.url=...
car.receipt.api.url=...

# ECA
eca.token.url=...
eca.client.id=...
eca.client.secret=...
eca.api.url=...
```

---

## 2. Velocity Template Engines (certify-service)

The upstream has a single `VelocityTemplatingEngineImpl`. This fork adds four specialized
subclasses to handle the varied JSON formats returned by the Brazilian APIs.

| File | Bean Name | Description |
|------|-----------|-------------|
| `VelocityTemplatingEngineImpl.java` | `velocityTemplatingEngineImpl` | **Modified**: added `@Primary` to resolve bean conflict. The only upstream Java file altered. |
| `VelocityTemplatingEngineFactory.java` | — | Factory that dispatches to the correct engine by bean name. |
| `CafVelocityTemplatingEngineImpl.java` | `velocityEngineCaf` | Flattens nested JSONObjects (2 levels) and JSONArrays into a flat map for Velocity. Handles `BigDecimal`. |
| `CarVelocityTemplatingEngineImpl.java` | `velocityEngineCar` | Similar to CAF, adapted for the flatter JSON structure of the CAR Receipt. |
| `CarDocumentVelocityTemplatingEngineImpl.java` | `velocityEngineCarDocument` | Single-level flattening for the CAR Document. |
| `EcaVelocityTemplatingEngineImpl.java` | `velocityEngineEca` | Similar to CAF, adds support for `Boolean` values (for `isOver12`, etc.). |

All custom engines extend `VelocityTemplatingEngineImpl` and override only the
`format(JSONObject, Map)` method, inheriting all template lookup, caching, and delegated
method logic (`getProofAlgorithm`, `getDidUrl`, `getCredentialStatusPurpose`, etc.).

---

## 3. Audit Trail (certify-service)

A complete audit trail system that records every VC issuance attempt (success or failure)
in the database. The upstream has no audit mechanism.

| File | Description |
|------|-------------|
| `config/AuditConfig.java` | `@Configuration` exposing `audit.enabled` (default: `false`). |
| `aspect/ControllerAuditAspect.java` | AOP `@Aspect` wrapping `VCIssuanceController.getCredential()`. Extracts `doctype` and caller identity. |
| `entity/CertifyAudit.java` | JPA entity: `id` (UUID), `vc_type`, `vc_issued` (boolean), `issued_by`, `created_date`, `issued_date`. |
| `repository/CertifyAuditRepository.java` | Repository with queries by type, issuer, success/failure, and time period. |
| `services/CertifyAuditService.java` | Audit service interface. |
| `services/CertifyAuditServiceImpl.java` | Transactional implementation. |

---

## 4. Database Metadata Storage (certify-service)

| File | Description |
|------|-------------|
| `entity/CertifyKeys.java` | JPA entity for the `certify_keys` table. Stores credential configurations as key-value pairs (JSON). |
| `repository/ConfigurationRepository.java` | Repository with `findByKey(String)`. |
| `services/CertifyKeysService.java` | Loads all `CertifyKeys`, resolves Spring `${...}` placeholders, and returns an issuer metadata map. |

### Legacy Entities from the 0.11.x Fork

| File | Description |
|------|-------------|
| `entity/CredentialTemplate.java` | JPA entity for `credential_template`. Composite PK: `(context, credentialType)`. **Possibly legacy code** — upstream 0.13.x uses `CredentialConfig`. |
| `entity/TemplateId.java` | Composite PK class for `CredentialTemplate`. |
| `repository/CredentialTemplateRepository.java` | Repository with `findByCredentialTypeAndContext()`. |

---

## 5. Database Scripts

| File | Description |
|------|-------------|
| `db_scripts/mosip_certify/ddl.sql` | Master DDL script (PostgreSQL). Includes all DDLs, including `certify-credential_template.sql`. |
| `db_scripts/mosip_certify/ddl/certify-credential_template.sql` | DDL for the `credential_template` table: `context`, `credential_type`, `template` (TEXT), `cr_dtimes`, `upd_dtimes`. |

---

## 6. Build Changes (POMs)

| File | Change |
|------|--------|
| `pom.xml` (root) | Lombok `1.18.42`, `maven-compiler-plugin` `3.11.0`, `annotationProcessorPaths` for Lombok (JDK 21 compatibility). |
| `certify-integration-api/pom.xml` | Added `spring-webflux` and `lombok` (provided) dependencies. `maven-compiler-plugin` with annotation processor. |
| `certify-service/pom.xml` | Lombok version in `annotationProcessorPaths` updated to `1.18.42`. |

---

## 7. Development Scripts and Tools

| File | Description |
|------|-------------|
| `scripts/govbr_token.py` | Python script for OID4VCI end-to-end testing with Gov.br. Authenticates via OAuth2 PKCE, generates a JWT proof with an ephemeral RSA key, and requests a VC from Certify. |
| `scripts/.gitignore` | Ignores `.token_cache.json` (token cache file used by the script). |
| `update_script.sh` | Bash script for syncing with upstream and publishing to the internal Dataprev SCM. |
| `.envrc` | Environment variables for `direnv` (ECA and Gov.br SSO credentials). **Contains credentials in plain text.** |
| `Jenkinsfile` | Dataprev CI pipeline: Maven 3.9 + JDK 21, builds the JAR and Docker image. |

---

## 8. Documentation

| File | Description |
|------|-------------|
| `docs/plano_merge.md` | Fork merge strategy with upstream (rebase history). |
| `docs/setup_environment.md` | Local setup guide: database, keystore, Spring Boot, endpoints, troubleshooting. |
| `docs/customizations.md` | This document. |
| `README_update_script_bash.md` | Documentation for `update_script.sh`. |

---

## Impact Summary

- **Upstream files modified:** 3 (POMs) + 1 Java (`VelocityTemplatingEngineImpl` — `@Primary` only)
- **New files added:** ~43
- **Design principle:** non-invasive extension via SPI, AOP, and `@ConditionalOnProperty`
- **All data providers are conditional:** they only load when `mosip.certify.dataprovider.enabled=true`

### Notes

1. `CCIRDataProvider` is partially implemented (stub with hardcoded token)
2. `CredentialTemplate`/`CredentialTemplateRepository` may be legacy code — upstream uses `CredentialConfig`
3. `.envrc` contains real credentials and should be added to `.gitignore`
