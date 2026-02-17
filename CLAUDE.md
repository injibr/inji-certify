# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Inji Certify is a Java Spring Boot service for issuing W3C-compliant Verifiable Credentials (VCs) in JSON-LD format. It connects with credential registries and authorization services to issue digital certificates following the OpenID4VCI protocol.

**Tech Stack:** Java 21, Spring Boot 3.2.3, Maven, PostgreSQL

## Build Commands

```bash
# Build (skip GPG signing for local development)
mvn clean install -Dgpg.skip=true -Dmaven.javadoc.skip=true

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Code coverage report
mvn jacoco:report

# Generate OpenAPI documentation
mvn clean install -Popenapi-doc-generate-profile
```

## Local Development Setup

1. **Prerequisites:** Java 21, PostgreSQL, Git Bash (Windows)

2. **Database initialization:**
   ```bash
   cd db_scripts/mosip_certify
   ./deploy.sh deploy.properties
   ```
   Default: `localhost:5432/inji_certify` schema `certify`, user `postgres/postgres`

3. **Run the service:**
   - Use `application-local.properties` profile
   - Configure plugin mode via `mosip.certify.plugin-mode` (DataProvider or VCIssuance)
   - Add VCI plugin JAR to certify-service dependencies

4. **Docker Compose (full stack):** See `docker-compose/docker-compose-injistack/README.md`

## Module Structure

```
certify-service/          # Main Spring Boot application
certify-core/             # Shared business logic and DTOs
certify-integration-api/  # Plugin interfaces (DataProviderPlugin, VCIssuancePlugin)
api-test/                 # REST Assured + TestNG API test suite
```

## Architecture

### Plugin System

Certify has two operating modes configured via `mosip.certify.plugin-mode`:

- **DataProvider mode:** Certify fetches data from plugin, then signs the VC internally using templates and VCSigner
- **VCIssuance mode:** Plugin handles everything (data fetch, VC creation, signing) and returns complete signed VC

Plugin interfaces are in `certify-integration-api`:
- `DataProviderPlugin` - Implement for DataProvider mode
- `VCIssuancePlugin` - Implement for VCIssuance mode

### Key Service Components (certify-service)

- `VCIssuanceController` / `VCIssuanceServiceImpl` - Core credential issuance endpoints
- `RenderingTemplateController` / `RenderingTemplateServiceImpl` - Credential template management
- `CertifyIssuanceServiceImpl` - Orchestrates credential issuance flow
- `VCICacheService` - Transaction caching (Redis or simple cache)
- `CertifyKeysService` - Key management

### VC Processing Pipeline

- `proof/` - Proof generation strategies
- `proofgenerators/` - JWT proof generators
- `vcsigners/` - VC signing implementations (JSON-LD, etc.)
- `vcformatters/` - Format handlers (JSON-LD, mDOC)

## Key Configuration Properties

```properties
# Plugin mode
mosip.certify.plugin-mode=DataProvider|VCIssuance

# Plugin package to scan
mosip.certify.integration.scan-base-package=io.mosip.certify.mock.integration

# Plugin implementations
mosip.certify.integration.data-provider-plugin=MockCSVDataProviderPlugin
mosip.certify.integration.vci-plugin=MockVCIssuancePlugin

# Authorization service
mosip.certify.authorization.url=https://esignet.example.com
mosip.certify.authn.jwk-set-uri=https://esignet.example.com/jwk.json

# For DataProvider mode - issuer identity
mosip.certify.data-provider-plugin.issuer-uri=did:web:example.com
mosip.certify.data-provider-plugin.issuer-public-key-uri=did:web:example.com#key-0
```

## API Test Suite

```bash
cd api-test
mvn clean install -Dgpg.skip=true -Dmaven.gitcommitid.skip=true

# Run tests
java -jar target/apitest-injicertify-*-jar-with-dependencies.jar \
  -Dmodules=injicertify \
  -Denv.user=api-internal.<env> \
  -Denv.endpoint=<base_url> \
  -Denv.testLevel=smokeAndRegression
```

Test categories: `smoke` (positive only), `smokeAndRegression` (all scenarios)

## External Plugin Repository

Custom plugins and reference implementations: https://github.com/mosip/digital-credential-plugins
