# INJIBR — Customizações do inji-certify

Este documento descreve todas as customizações feitas pelo time INJIBR sobre o upstream
`inji-certify` da MOSIP. Serve como referência para futuras atualizações de versão e como
contexto para ferramentas de IA assistindo no processo.

---

## Visão Geral

O inji-certify upstream foi adaptado para o fluxo govbr (Gov.br SSO) com múltiplos emissores
(MGI, INCRA, MDA). As principais diferenças em relação ao upstream são:

- Autenticação via Gov.br SSO em vez de eSignet
- Múltiplos emissores lógicos por instância (multi-issuer)
- Dispatch de DataProvider por `docType` em vez de scope
- Suporte a `mso_mdoc` além de `ldp_vc`
- Bypass de cNonce (Gov.br não usa)
- Expiry diferenciado por tipo de credencial (ECA = 90 dias)
- Build sem sudo / filesystem readonly (Dockerfile adaptado)

---

## Arquivos Modificados

### `certify-service/src/main/java/io/mosip/certify/services/CertifyIssuanceServiceImpl.java`

**Customizações:**

1. **Scope lookup com fallback para `List<String>`**
   - O token govbr pode retornar `scope` como lista em vez de string
   - Trata ambos os casos antes do lookup por scope

2. **`resolveCredentialMetadata` — lookup multi-issuer**
   - Quando o scope-based lookup falha, faz fallback por `issuerId + doctype`
   - Para `ldp_vc`: compara `doctype` contra `credentialConfigKeyId`
   - Para `mso_mdoc`: compara `doctype` contra `dto.getDocType()` (ex: `br.gov.mgi.eca`)
   - Método: `resolveCredentialMetadata(CredentialRequest)`

3. **`docType` injetado nos claims ANTES do `fetchData`**
   - **CRÍTICO**: o `DataProviderPluginImpl` despacha por `docType` nos claims
   - Usa `credentialMetadata.getId()` (credentialConfigKeyId) como `docType`
   - Fallback para `credentialRequest.getDoctype()` e depois `credential_definition.type`
   - **NUNCA mover este bloco para depois do `fetchData`**

4. **CARReceipt AST/PCT switch**
   - Após o `fetchData`, verifica `tipoImovel` no JSON retornado
   - Se `AST` → muda o type para `CARReceiptAST`; se `PCT` → `CARReceiptPCT`
   - Aplica apenas quando `docType == "CARReceipt"`

5. **`didUrl` por credential_config**
   - Usa `vcFormatter.getDidUrl(templateName)` em vez do `didUrl` global
   - Suporta DID diferente por emissor (MGI, INCRA, MDA)

6. **Expiry diferenciado para ECA**
   - ECA (`ECACredential` ou `br.gov.mgi.eca`) usa `ecaExpiryDuration` (padrão: P90D)
   - Demais credenciais usam `defaultExpiryDuration` (padrão: P730D)
   - Property: `mosip.certify.data-provider-plugin.eca.vc-expiry-duration`

7. **cNonce bypass**
   - `getValidClientNonce` é chamado dentro de try/catch — falha silenciosa
   - O bypass real é no `JwtProofValidator` via property

---

### `certify-service/src/main/java/io/mosip/certify/services/CredentialConfigurationServiceImpl.java`

**Customizações:**

1. **`fetchCredentialIssuerMetadataByIssuerId`**
   - Método novo: retorna metadata filtrado por `issuerId`
   - Usado pelo `resolveCredentialMetadata` no fluxo govbr

---

### `certify-service/src/main/java/io/mosip/certify/utils/CredentialUtils.java`

**Customizações:**

1. **`toJsonMap` — suporte a `BigDecimal` e `Boolean`**
   - APIs govbr retornam campos numéricos como `BigDecimal` e booleanos como `Boolean`
   - Sem esse fix, a serialização falha ou perde precisão

---

### `certify-integration-api/src/main/java/io/mosip/certify/api/dataprovider/impl/DataProviderPluginImpl.java`

**Customizações:**

1. **Dispatch por `docType` com normalização de sufixo**
   - Recebe `docType` dos claims (ex: `ECACredential`, `ECACredential-mdoc`)
   - Se não encontrar no `instanceMap`, normaliza removendo sufixos `-mdoc`, `-sd-jwt`, `-ldp`
   - Permite que `ECACredential-mdoc` resolva para o mesmo provider que `ECACredential`

---

### `certify-integration-api/src/main/java/io/mosip/certify/api/dataprovider/impl/DataProviderService.java` (interface)

Sem modificações — a normalização de sufixo no `DataProviderPluginImpl` eliminou a necessidade
de aliases na interface.

---

### `certify-integration-api/src/main/java/io/mosip/certify/api/dataprovider/impl/` (providers)

Providers INJIBR (não existem no upstream):

| Classe | `getDocumentType()` | Emissor | API |
|---|---|---|---
| `EcaDataProvider` | `ECACredential` | MGI | govbr ECA |
| `CARDataProvider` | `CARDocument` | MGI | SICAR |
| `CARReceiptDataProvider` | `CARReceipt` | MGI | SICAR |
| `CCIRDataProvider` | `CCIRCredential` | INCRA | SERPRO/Dataprev |
| `CAFDataProvider` | `CAFCredential` | MDA | MDA |

Cada provider tem um `TokenClient` associado para autenticação na API govbr.

---

### `certify-service/src/main/java/io/mosip/certify/proof/JwtProofValidator.java`

**Customizações:**

1. **Bypass de cNonce**
   - Property: `mosip.certify.govbr.cnonce-bypass-enabled=true`
   - Quando ativo, loga WARN e retorna `true` sem validar o nonce
   - Necessário porque o Gov.br não implementa cNonce no fluxo OID4VCI

---

### `certify-service/src/main/java/io/mosip/certify/filter/AccessTokenValidationFilter.java`

**Customizações:**

1. **Comentar `JwtClaimValidator` do `CLIENT_ID`**
   - Gov.br não inclui `client_id` no access token
   - Linha comentada: `// new JwtClaimValidator<String>(Constants.CLIENT_ID, Objects::nonNull),`

---

### `certify-service/Dockerfile`

**Customizações:**

1. **Usuário `inji`** (mudou de `mosip` na v0.14.0)
2. **ENTRYPOINT relativo** (`./configure_start.sh` em vez de `/home/mosip/configure_start.sh`)
3. **HSM instalado em build time** via artifactory, sem sudo
4. **Sem `apt-get` em runtime** — filesystem readonly compatível

---

### `certify-service/configure_start.sh`

**Customizações:**

1. **Download de plugins via artifactory** sem sudo
2. **Sem necessidade de filesystem writable** em runtime

---

### `pom.xml` / `certify-service/pom.xml` / `certify-core/pom.xml` / `certify-integration-api/pom.xml`

- Versão: seguir convenção INJIBR (`MAJOR.MINOR.PATCH`)
- `certify-integration-api`: dependências WebFlux adicionadas (`reactor-netty`, `netty-handler`, `spring-webflux`) para os providers reativos

---

## Banco de Dados

Alterações INJIBR no DDL (`db_scripts/inji_certify/ddl/certify-credential_config.sql`):

```sql
-- coluna
issuer_id VARCHAR(255),

-- index
CREATE INDEX idx_credential_config_issuer_id
    ON credential_config(issuer_id)
    WHERE issuer_id IS NOT NULL;

-- comment
COMMENT ON COLUMN credential_config.issuer_id IS
    'Issuer ID: INJIBR logical issuer identifier (MGI, INCRA, MDA) for multi-issuer govbr flow.';
```


---

## Fluxo de Emissão govbr

```
InjWeb → mimoto → Gov.br SSO (access token)
       → certify /issuance/credential
           1. Scope lookup → falha (scope=openid, genérico)
           2. resolveCredentialMetadata(issuerId + doctype) → CredentialMetadata
           3. JwtProofValidator → bypass cNonce
           4. docType = credentialMetadata.getId() → injetado nos claims
           5. DataProviderPluginImpl.fetchData(claims) → dispatch por docType
           6. VelocityTemplatingEngine → unsigned VC
           7. addProof → VC assinada
```

---

## Configurações Relevantes (certify-mgi.properties)

```properties
mosip.certify.plugin-mode=DataProvider
mosip.certify.govbr.cnonce-bypass-enabled=true
mosip.certify.data-provider-plugin.eca.vc-expiry-duration=P90D
mosip.certify.credential-config.as-mapping={\
    'MGI': 'https://sso.acesso.gov.br',\
    'INCRA': 'https://sso.acesso.gov.br',\
    'MDA': 'https://sso.acesso.gov.br'\
}
```

---

## Checklist de Migração (após rebase para nova versão upstream)

Arquivos que **sempre** precisam revisão manual após rebase:

- [ ] `CertifyIssuanceServiceImpl.java`
- [ ] `docType` injetado ANTES do `fetchData`
- [ ] `resolveCredentialMetadata` presente e correto
- [ ] CARReceipt AST/PCT switch
- [ ] ECA expiry com `br.gov.mgi.eca` na condição
- [ ] `didUrl` por template (`vcFormatter.getDidUrl`)
- [ ] `CredentialConfigurationServiceImpl.java`
- [ ] `fetchCredentialIssuerMetadataByIssuerId` presente
- [ ] `CredentialUtils.java` — `BigDecimal`/`Boolean` no `toJsonMap`
- [ ] `DataProviderPluginImpl.java` — normalização de sufixo no dispatch
- [ ] `JwtProofValidator.java` — bypass cNonce
- [ ] `AccessTokenValidationFilter.java` — bypass CLIENT_ID
- [ ] `Dockerfile` — ENTRYPOINT relativo, usuário `inji`
- [ ] `pom.xml` — versão INJIBR correta, dependências WebFlux no `certify-integration-api`

---

## Regras para IA (Amazon Q / Copilot)

Ao assistir em tarefas neste repositório, seguir estas regras:

### Regras gerais

1. **Nunca remover customizações INJIBR** marcadas com comentário `// INJIBR-CUSTOM`
2. **Nunca mover o bloco de injeção do `docType`** para depois do `fetchData`

### Regras de rebase

3. Ao fazer rebase, aplicar o checklist acima em ordem
4. O `credentialConfigKeyId` para mso_mdoc segue a convenção `{baseKey}-mdoc`
5. O `docType` ISO (ex: `br.gov.mgi.eca`) vai no campo `doctype` do JSON, não no `credentialConfigKeyId`

### Regras de código

6. Manter comentários `// INJIBR-CUSTOM` em todas as customizações
7. Não adicionar métodos de alias/extensão em interfaces upstream — preferir normalização no dispatcher
8. Ao adicionar novo provider, seguir o padrão: `{Nome}DataProvider` + `{Nome}TokenClient`
9. Ao adicionar nova credencial em novo formato, o `credentialConfigKeyId` deve ser `{BaseKey}-{formato}`

---

## Scripts Utilitários (`scripts/`)

| Script | Descrição |
|---|---|
| `govbr_token_local_k8s.py` | Emissão de credenciais via Gov.br SSO no ambiente local K8s |
| `govbr_token_dev.py` | Idem para ambiente dev |
| `govbr_token_homolog.py` | Idem para homologação |

### Opções do `govbr_token_local_k8s.py`

```bash
# Emitir todas as credenciais
python scripts/govbr_token_local_k8s.py

# Filtrar por emissor
python scripts/govbr_token_local_k8s.py --issuer MGI

# Filtrar por formato
python scripts/govbr_token_local_k8s.py --format mso_mdoc

# Combinar filtros
python scripts/govbr_token_local_k8s.py --issuer MGI --format mso_mdoc
```

**Dependências:** `pip install cryptography`

---

## Versionamento

O INJIBR usa versionamento semântico próprio (`MAJOR.MINOR.PATCH`), independente do upstream.

- `MAJOR` — incrementado a cada rebase sobre uma nova versão upstream
- `MINOR` — incrementado para novas funcionalidades INJIBR dentro da mesma base upstream
- `PATCH` — incrementado para correções de bugs
