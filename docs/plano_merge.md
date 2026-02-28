# Merge Plan: injibr/inji-certify with upstream mosip/inji-certify

**Date:** 2025-02-28
**Working branch:** `master-updated`
**Backup branch:** `master-backup`

---

## Context

The `injibr/inji-certify` repository was created by copying the code from `mosip/inji-certify`
(not via GitHub Fork). As a result, commits have **different hashes** even when the content is
identical — Git does not recognize a common ancestor between the two repositories
("unrelated histories").

- **Fork (injibr):** 294 commits on master, frozen at version **0.11.x**
- **Upstream (mosip):** 461 commits on master, currently at version **0.13.x**
- **Branch fix_424:** ~125 commits ahead of the fork's master, containing all Brazilian customizations

## Why a direct merge doesn't work

```
$ git merge upstream/master
fatal: refusing to merge unrelated histories
```

Git treats the two repositories as completely independent. Using `--allow-unrelated-histories`
would generate conflicts in virtually every file, making resolution impractical.

---

## Adopted Strategy

### Approach: "New branch from upstream + re-apply customizations"

1. Create branch `master-updated` from `upstream/master` (clean, up-to-date base)
2. Re-apply only the commits/changes exclusive to the Brazilian fork
3. Skip noise commits (CI iterations, Dockerfile tests, superseded version bumps)
4. Validate with a build (`mvn clean install`)

### Why this approach?

- Guarantees the codebase is 100% up-to-date with upstream
- Avoids dragging in old version/release commits that no longer make sense
- Allows each customization to be reviewed before re-applying
- Result: clean history with current upstream + Brazilian customizations

---

## Inventory of Fork Customizations (exclusive commits)

### 1. Brazilian Data Providers (core business)
New files created by the fork for integration with Brazilian government services:

- `certify-integration-api/src/main/java/.../dataprovider/DataProviderService.java`
- `certify-integration-api/src/main/java/.../dataprovider/impl/DataProviderPluginImpl.java`
- `certify-integration-api/src/main/java/.../dataprovider/impl/CAFDataProvider.java`
- `certify-integration-api/src/main/java/.../dataprovider/impl/CafTokenClient.java`
- `certify-integration-api/src/main/java/.../dataprovider/impl/CARDataProvider.java`
- `certify-integration-api/src/main/java/.../dataprovider/impl/CARReceiptDataProvider.java`
- `certify-integration-api/src/main/java/.../dataprovider/impl/CarTokenClient.java`
- `certify-integration-api/src/main/java/.../dataprovider/impl/CCIRDataProvider.java`
- `certify-integration-api/src/main/java/.../dataprovider/impl/SicarCpfCnpjClient.java`
- `certify-integration-api/src/main/java/.../dataprovider/impl/EcaDataProvider.java`
- `certify-integration-api/src/main/java/.../dataprovider/impl/EcaTokenClient.java`
- `certify-integration-api/src/main/java/.../config/WebClientConfig.java`

### 2. Custom Velocity Template Engines
- `certify-service/src/main/java/.../vcformatters/CafVelocityTemplatingEngineImpl.java`
- `certify-service/src/main/java/.../vcformatters/CarDocumentVelocityTemplatingEngineImpl.java`
- `certify-service/src/main/java/.../vcformatters/CarVelocityTemplatingEngineImpl.java`
- `certify-service/src/main/java/.../vcformatters/EcaVelocityTemplatingEngineImpl.java`
- `certify-service/src/main/java/.../vcformatters/VelocityTemplatingEngineFactory.java`

### 3. Audit Trail
- `certify-service/src/main/java/.../aspect/ControllerAuditAspect.java`
- `certify-service/src/main/java/.../config/AuditConfig.java`
- `certify-service/src/main/java/.../entity/CertifyAudit.java`
- `certify-service/src/main/java/.../repository/CertifyAuditRepository.java`
- `certify-service/src/main/java/.../services/CertifyAuditService.java`
- `certify-service/src/main/java/.../services/CertifyAuditServiceImpl.java`

### 4. Configuration and Docker
- `certify-service/Dockerfile` (customizations)
- `certify-service/configure_start.sh`
- `docker-compose/docker-compose-injistack/` (local configurations)
- `certify-service/src/main/resources/application-local.properties`

### 5. Scripts and Documentation
- `scripts/govbr_token.py` — OID4VCI test script with Gov.br
- `scripts/.gitignore`
- `update_script.sh` — version update script for POMs
- `docs/setup_environment.md` — setup guide
- `docs/customizations.md` — customization reference
- `Jenkinsfile` — CI pipeline
- `README_update_script_bash.md`

### 6. Modifications to Existing Upstream Files
These required special attention as they could conflict:

- `certify-service/src/main/java/.../vcformatters/VelocityTemplatingEngineImpl.java` — added `@Primary`
- `pom.xml`, `certify-integration-api/pom.xml`, `certify-service/pom.xml` — Lombok + compiler plugin

---

## Upstream API Changes (0.11.x → 0.13.x)

The upstream 0.13.x restructured the template/VC layer. All custom Velocity engines were
adapted accordingly:

| Fork (0.11.x)                        | Upstream (0.13.x)                      | Status |
|--------------------------------------|----------------------------------------|--------|
| `CredentialTemplateRepository`       | `CredentialConfigRepository`           | ✅ Done |
| `CredentialTemplate`                 | `CredentialConfig`                     | ✅ Done |
| `Constants.ISSUER_URI`               | `Constants.DID_URL`                    | ✅ Done |
| `VCDM2Constants.VALID_UNITL` (typo)  | `VCDM2Constants.VALID_UNTIL`           | ✅ Done |
| `getSvgTemplate()`                   | `getTemplate()`                        | ✅ Done |
| N/A                                  | `getCredentialStatusPurpose()` (new)   | ✅ Done |
| N/A                                  | `getSignatureCryptoSuite()` (new)      | ✅ Done |
| Template read directly from field    | Template decoded from Base64           | ✅ Done |
| Lookup by credentialType + context   | Lookup by format + type + context      | ✅ Done |

---

## Execution Steps

| # | Step | Status |
|---|------|--------|
| 1 | Add upstream remote: `git remote add upstream https://github.com/mosip/inji-certify.git` | ✅ Done |
| 2 | Fetch upstream: `git fetch upstream` | ✅ Done |
| 3 | Create backup: `git branch master-backup master` | ✅ Done |
| 4 | Create branch `master-updated` from `upstream/master` | ✅ Done |
| 5 | Copy new files (data providers, audit, templates, scripts) | ✅ Done |
| 6 | Upgrade Lombok `1.18.30` → `1.18.42` (incompatible with JDK 21) | ✅ Done |
| 7 | Upgrade `maven-compiler-plugin` `3.8.1` → `3.11.0` with `annotationProcessorPaths` | ✅ Done |
| 8 | Adapt Velocity template engines to new upstream API (see table above) | ✅ Done |
| 9 | Adapt `CertifyKeysService` to new upstream | ✅ Done |
| 10 | Verify build: `JAVA_HOME=$(java_home -v 21) mvn clean install -Dgpg.skip=true -Dmaven.javadoc.skip=true` → **BUILD SUCCESS** (341 tests, 0 failures) | ✅ Done |
| 11 | Commit all changes on `master-updated` | ✅ Done |
| 12 | Replace `master` with `master-updated` | ✅ Done |

---

## Skipped Commits (noise)

The following commit types were **not** re-applied:

- ~30 Jenkinsfile iteration commits (`test: jenkinsfile`, `fix jenkinsfile`, etc.)
- ~15 Dockerfile test commits (`test dockerfile`, `ajuste dockerfile p teste`, etc.)
- ~10 superseded version bump commits (`update version`, `new version 3.0.0`, etc.)
- Internal fork merge commits
- Commits mirroring upstream releases (0.10.0, 0.10.1, 0.10.2, 0.11.0)

---

## Rollback

If anything goes wrong, branch `master-backup` holds the original state of the fork's master.
Branch `fix_424` remains intact as a reference for all Brazilian customizations.

---

## Known Remaining Issues

1. `CCIRDataProvider` is partially implemented — stub with a hardcoded trial token and URL.
2. `CredentialTemplate` / `CredentialTemplateRepository` may be legacy code — upstream 0.13.x uses `CredentialConfig`.
3. `.envrc` contains real credentials in plain text and should be added to `.gitignore`.