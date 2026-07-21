# Plano de Migração INJIBR — v0.10.2 → v0.12.2

## Contexto

A branch atual foi criada a partir da upstream v0.12.2. Este documento descreve
todas as customizações INJIBR que existiam na entrega (baseada na upstream v0.10.2)
e como cada uma deve ser aplicada na v0.12.2, respeitando as mudanças de arquitetura
da nova versão.

Fonte do git diff: `entrega-vs-upstream-0.10.2.patch`
Gerado com: `git diff v0.10.2..entrega`

---

## Convenção de Customização

Toda linha de código alterada ou desativada por motivo de compatibilidade com govbr
ou por customização INJIBR **não deve ser deletada** — deve ser **comentada**,
precedida de um comentário explicativo com a tag `INJIBR-CUSTOM`.

**Padrão obrigatório:**
```java
// INJIBR-CUSTOM: <motivo da mudança>
// linha original comentada
nova linha ou ausência de linha
```

**Exemplos:**
```java
// INJIBR-CUSTOM: govbr token does not include client_id claim
// new JwtClaimValidator<String>(Constants.CLIENT_ID, Objects::nonNull),
```
```java
// INJIBR-CUSTOM: govbr does not send cNonce in proof JWT, bypass enabled via property
if (cNonceBypassEnabled) {
    log.warn("[INJIBR-CUSTOM] cNonce bypass enabled — skipping nonce validation (govbr compatibility)");
    return;
}
```

**Objetivo:** facilitar busca por `INJIBR-CUSTOM` para identificar todos os pontos
customizados ao fazer um novo upgrade de versão upstream.

---

## Mudanças de Arquitetura entre v0.10.2 e v0.12.2 (upstream)

| Aspecto | v0.10.2 | v0.12.2 |
|---|---|---|
| Metadata do issuer | `mosip.certify.key-values` (property) | `credential_config` (tabela no banco) |
| Template da VC | `credential_template` (tabela separada) | `credential_config.vc_template` (coluna, base64) |
| Lookup de credencial | por `scope` do token | por `scope` + `format` + `type/doctype/vct` via `CredentialConfigurationService` |
| VCFormatter | `@Service` único | `@Service` único, lê config do banco via `CredentialConfig` |
| ProofValidator | `validate(clientId, cNonce, proof)` | `validate(clientId, cNonce, proof, proofConfiguration)` + `validateCNonce()` separado |
| cNonce | gerado e validado no service | gerado em `VCIssuanceUtil.getValidClientNonce()`, validado em `validateCNonce()` |
| Well-known endpoint | `VCIssuanceController` (deprecated) | `WellKnownController` (principal) |

---

## Customizações INJIBR a Aplicar

---

### 1. `AccessTokenValidationFilter.java`

**Arquivo:** `certify-service/src/main/java/io/mosip/certify/filter/AccessTokenValidationFilter.java`

**O que muda:** Remover a validação do claim `CLIENT_ID` pois o token do govbr não inclui esse claim.

**Evidência no patch:**
```diff
-new JwtClaimValidator<String>(Constants.CLIENT_ID, Objects::nonNull),
+//Removed Client ID validation as in govbr token ClientId is not present, to integrate with govbr
```

**Como aplicar na v0.12.2:** Comentar (não remover) o `JwtClaimValidator` de `CLIENT_ID`
no método `getNimbusJwtDecoder()`, precedido do comentário `INJIBR-CUSTOM`:

```java
// INJIBR-CUSTOM: govbr token does not include client_id claim
// new JwtClaimValidator<String>(Constants.CLIENT_ID, Objects::nonNull),
```

---

### 2. `ProofValidator.java` (interface)

**Arquivo:** `certify-service/src/main/java/io/mosip/certify/proof/ProofValidator.java`

**O que muda:** Na v0.10.2 INJIBR foi adicionado um overload `validate(clientId, proof)` sem cNonce.

**Evidência no patch:**
```diff
+boolean validate(String clientId, CredentialProof credentialProof);
+// Overloaded method to support legacy clients that do not send cNonce, to integrate with govbr
```

**Como aplicar na v0.12.2:** A assinatura do `validate` mudou — agora recebe também
`Map<String, Object> proofConfiguration`. O cNonce foi separado para `validateCNonce()`.
A estratégia correta para v0.12.2 é:

- **Não** adicionar overload sem cNonce
- Modificar `validateCNonce()` para aceitar bypass via property `mosip.certify.govbr.cnonce-bypass-enabled`
- Quando bypass ativo, `validateCNonce()` retorna sem lançar exceção
- O `validate()` existente já funciona — quando bypass ativo, o cNonce passado pode ser qualquer valor pois `validateCNonce()` não terá lançado exceção antes

**Mudança real:** Adicionar `@Value` e lógica de bypass apenas em `JwtProofValidator.validateCNonce()`.
A interface `ProofValidator` **não muda**.

---

### 3. `JwtProofValidator.java`

**Arquivo:** `certify-service/src/main/java/io/mosip/certify/proof/JwtProofValidator.java`

**O que muda:** Suporte a bypass de cNonce para govbr.

**Evidência no patch:** Adicionou overload `validate(clientId, proof)` que valida o JWT sem verificar nonce no `JWTClaimsSet`.

**Como aplicar na v0.12.2:**

```java
// INJIBR-CUSTOM: govbr does not send cNonce, bypass controlled by property
@Value("${mosip.certify.govbr.cnonce-bypass-enabled:false}")
private boolean cNonceBypassEnabled;

// em validateCNonce() — adicionar no início do método:
// INJIBR-CUSTOM: govbr does not send cNonce in proof JWT, bypass enabled via property
if (cNonceBypassEnabled) {
    log.warn("[INJIBR-CUSTOM] cNonce bypass enabled — skipping nonce validation (govbr compatibility)");
    return;
}
// ... lógica original inalterada abaixo ...

// em validate() — substituir a linha que monta o nonce no JWTClaimsSet:
// INJIBR-CUSTOM: skip nonce claim in JWT verifier when bypass is active (govbr compatibility)
// .claim("nonce", cNonce)  <- original comentada
if (!cNonceBypassEnabled && cNonce != null) {
    proofJwtClaimsBuilder.claim("nonce", cNonce);
}
```

---

### 4. `CredentialRequest.java`

**Arquivo:** `certify-core/src/main/java/io/mosip/certify/core/dto/CredentialRequest.java`

**O que muda:** Adicionar campo `issuerId` para identificar o issuer lógico (MGI, INCRA, MDA).

**Evidência no patch:**
```diff
+//Added issuerId to track the issuer of the credential to integrate with govbr
+private String issuerId;
```

**Como aplicar na v0.12.2:** Idêntico — adicionar campo `issuerId` na classe com comentário:

```java
// INJIBR-CUSTOM: identifies the logical issuer (MGI, INCRA, MDA) for multi-issuer govbr flow
private String issuerId;
```

---

### 5. `CertifyIssuanceServiceImpl.java`

**Arquivo:** `certify-service/src/main/java/io/mosip/certify/services/CertifyIssuanceServiceImpl.java`

**O que muda (3 pontos):**

#### 5a. Lookup de credencial por `doctype` + `issuerId` em vez de `scope do token`

**Evidência no patch:**
```diff
-String scopeClaim = (String) parsedAccessToken.getClaims().getOrDefault("scope", "");
-for(String scope : scopeClaim.split(Constants.SPACE)) {
-    getScopeCredentialMapping(scope, credentialRequest.getFormat())
+String scopeClaim = credentialRequest.getDoctype();
+getScopeCredentialMapping(credentialRequest.getDoctype(), credentialRequest.getIssuerId(), credentialRequest.getFormat())
```
E o filtro interno:
```diff
-filter(cm -> cm.getValue().get("scope").equals(scope))
+filter(cm -> cm.getKey().equals(doctype))
```

**Como aplicar na v0.12.2:** A v0.12.2 usa `CredentialConfigurationService.fetchCredentialIssuerMetadata("latest")`
e `VCIssuanceUtil.getScopeCredentialMapping()`. A adaptação é:

1. Manter o loop por scope do token como **primeiro** caminho (compatibilidade com esignet) — **comentar** o bloco original, não remover
2. Adicionar fallback govbr logo abaixo usando `issuerId` do request

```java
// INJIBR-CUSTOM: govbr token scope does not identify the credential type;
// fallback uses issuerId + doctype from request body instead of token scope
/*
String scopeClaim = (String) parsedAccessToken.getClaims().getOrDefault("scope", "");
for(String scope : scopeClaim.split(Constants.SPACE)) {
    Optional<CredentialMetadata> result = getScopeCredentialMapping(
        scope, credentialRequest.getFormat(),
        credentialConfigurationService.fetchCredentialIssuerMetadata("latest"),
        credentialRequest);
    if(result.isPresent()) { credentialMetadata = result.get(); break; }
}
*/
// INJIBR-CUSTOM: lookup by issuerId + doctype for govbr multi-issuer flow
credentialMetadata = resolveCredentialMetadata(credentialRequest);
```

Ver seção **"Estratégia de lookup multi-issuer"** abaixo.

#### 5b. Injetar `docType` nos claims antes de chamar `fetchData`

**Evidência no patch:**
```diff
+Map<String, Object> identityDetails = parsedAccessToken.getClaims();
+identityDetails.put("docType", credentialRequest.getDoctype());
JSONObject jsonObject = dataProviderPlugin.fetchData(parsedAccessToken.getClaims());
```

**Como aplicar na v0.12.2:** Adicionar antes da chamada `dataProviderPlugin.fetchData()` no case `ldp_vc`:

```java
// INJIBR-CUSTOM: DataProviderPluginImpl dispatches by docType; inject it into claims
parsedAccessToken.getClaims().put("docType", credentialRequest.getDoctype());
```

#### 5c. Lógica de `CARReceiptAST` vs `CARReceiptPCT`

**Evidência no patch:**
```java
case "CARReceipt" -> {
    String tipoImovel = jsonObject.optString("tipoImovel");
    if ("AST".equals(tipoImovel)) {
        vcRequestDto.setType(List.of("CARReceiptAST", "VerifiableCredential"));
    } else if ("PCT".equals(tipoImovel)) {
        vcRequestDto.setType(List.of("CARReceiptPCT", "VerifiableCredential"));
    }
}
```

**Como aplicar na v0.12.2:** Adicionar após o `fetchData()` e antes de montar `templateParams`,
dentro do case `ldp_vc`:

```java
// INJIBR-CUSTOM: CARReceipt has two subtypes (AST/PCT) determined by data returned from provider
if ("CARReceipt".equals(credentialRequest.getDoctype())) {
    String tipoImovel = jsonObject.optString("tipoImovel");
    if ("AST".equals(tipoImovel)) {
        vcRequestDto.setType(List.of("CARReceiptAST", "VerifiableCredential"));
    } else if ("PCT".equals(tipoImovel)) {
        vcRequestDto.setType(List.of("CARReceiptPCT", "VerifiableCredential"));
    }
}
```

---

### 6. Estratégia de lookup multi-issuer na v0.12.2

O patch na v0.10.2 usava `certify_keys` (tabela própria) + `CertifyKeysService` para guardar o metadata por issuer e fazia lookup direto por `issuerId` → `doctype`.

Na v0.12.2 o metadata já está na `credential_config`. A adaptação é:

**Adicionar coluna `issuer_id` na `credential_config`:**
```sql
ALTER TABLE certify.credential_config ADD COLUMN issuer_id VARCHAR(255);
CREATE INDEX idx_credential_config_issuer_id ON certify.credential_config(issuer_id)
    WHERE issuer_id IS NOT NULL;
```

**Adicionar query no `CredentialConfigRepository`:**
```java
List<CredentialConfig> findByIssuerId(String issuerId);
```

**Adicionar método na interface `CredentialConfigurationService`:**
```java
CredentialIssuerMetadataDTO fetchCredentialIssuerMetadataByIssuerId(String issuerId);
```

**Implementar em `CredentialConfigurationServiceImpl`:** filtrar `credential_config` por `issuer_id` e montar o `CredentialIssuerMetadataVD13DTO` igual ao `fetchCredentialIssuerMetadata("latest")` mas só com as configs do issuer.

**Modificar `WellKnownController`:** aceitar `?issuer_id=MGI` e chamar o novo método.

**Modificar `CertifyIssuanceServiceImpl`:** extrair método `resolveCredentialMetadata()` com
comentário `INJIBR-CUSTOM` explicando o fallback govbr. O método tenta primeiro pelo scope
do token (esignet) e depois pelo `issuerId` + `doctype` (govbr).

---

### 7. DataProviders (novos arquivos)

**Módulo:** `certify-integration-api`

**Arquivos novos — portar diretamente do patch (sem conflito com v0.12.2):**

| Arquivo | Descrição |
|---|---|
| `api/config/WebClientConfig.java` | Bean `WebClient` |
| `api/dataprovider/DataProviderService.java` | Interface com `getDocumentType()` e `getData(cpf)` |
| `api/dataprovider/impl/DataProviderPluginImpl.java` | Implementa `DataProviderPlugin`, despacha por `docType` |
| `api/dataprovider/impl/CARDataProvider.java` | Busca dados do CAR (demonstrativo) |
| `api/dataprovider/impl/CARReceiptDataProvider.java` | Busca recibo do CAR |
| `api/dataprovider/impl/CAFDataProvider.java` | Busca dados do CAF |
| `api/dataprovider/impl/CCIRDataProvider.java` | Busca dados do CCIR |
| `api/dataprovider/impl/EcaDataProvider.java` | Busca dados ECA, calcula idade |
| `api/dataprovider/impl/CarTokenClient.java` | OAuth2 token client para CAR/SICAR |
| `api/dataprovider/impl/CafTokenClient.java` | OAuth2 token client para CAF |
| `api/dataprovider/impl/EcaTokenClient.java` | OAuth2 token client para ECA |
| `api/dataprovider/impl/SicarCpfCnpjClient.java` | Busca número de registro por CPF no SICAR |

**Dependências a adicionar no `certify-integration-api/pom.xml`:**
```xml
<dependency>
    <groupId>io.projectreactor.netty</groupId>
    <artifactId>reactor-netty</artifactId>
</dependency>
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-handler</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-webflux</artifactId>
</dependency>
```

---

### 8. Audit (novos arquivos)

**Arquivos novos — portar diretamente do patch:**

| Arquivo | Módulo |
|---|---|
| `certify-service/.../entity/CertifyAudit.java` | Entity JPA para tabela `certify_audit` |
| `certify-service/.../repository/CertifyAuditRepository.java` | Repository JPA |
| `certify-service/.../services/CertifyAuditService.java` | Interface |
| `certify-service/.../services/CertifyAuditServiceImpl.java` | Implementação |
| `certify-service/.../config/AuditConfig.java` | `@Value("${audit.enabled:false}")` |
| `certify-service/.../aspect/ControllerAuditAspect.java` | AOP — intercepta `getCredential()` |

**DDL necessário:**
```sql
CREATE TABLE certify.certify_audit (
    id UUID NOT NULL,
    vc_type VARCHAR NOT NULL,
    vc_issued BOOLEAN NOT NULL,
    issued_by VARCHAR NOT NULL,
    created_date TIMESTAMP,
    issued_date TIMESTAMP,
    CONSTRAINT pk_certify_audit PRIMARY KEY (id)
);
```

---

### 9. VelocityTemplatingEngineImpl.java

**Arquivo:** `certify-service/src/main/java/io/mosip/certify/vcformatters/VelocityTemplatingEngineImpl.java`

**O que muda na v0.12.2:** A v0.12.2 já refatorou completamente esse arquivo — lê template do banco via `CredentialConfig.vc_template` (base64), tem métodos `getProofAlgorithm()`, `getAppID()`, `getRefID()`, `getDidUrl()`, `getSignatureCryptoSuite()`, `getCredentialStatusPurpose()`. A estrutura é completamente diferente da v0.10.2.

**O que o patch adicionou que ainda é relevante:**
- Tratamento de `BigDecimal` no mapeamento de tipos
- Tratamento de `JSONObject` aninhado (flatten de campos)
- Tratamento de `JSONArray` com concatenação de valores repetidos
- Tratamento de `null` → `""`

**Como aplicar na v0.12.2:** Verificar se o método `jsonify()` da v0.12.2 já cobre esses casos. Se não cobrir, adicionar os casos faltantes dentro de `jsonify()`. **Não** portar os engines separados (`CafVelocityTemplatingEngineImpl`, `CarVelocityTemplatingEngineImpl`, etc.) — a v0.12.2 usa um engine único que lê do banco.

---

### 10. Properties

**Arquivo:** `certify-service/src/main/resources/application-local.properties`
e `docker-compose/docker-compose-injistack/config/certify-default.properties`

**Mudanças de configuração govbr:**
```properties
# Authorization server govbr
mosip.certify.authorization.url=https://sso.staging.acesso.gov.br
mosip.certify.authn.issuer-uri=https://sso.staging.acesso.gov.br/
mosip.certify.authn.jwk-set-uri=https://sso.staging.acesso.gov.br/jwk

# Audiences aceitas (inclui client_id do govbr)
mosip.certify.authn.allowed-audiences={ \
  '${mosip.certify.domain.url}${server.servlet.path}/issuance/credential', \
  'h-credenciaisverificaveis-dev.dataprev.gov.br' }

# Flags govbr
mosip.certify.govbr.cnonce-bypass-enabled=true

# Scan dos DataProviders INJIBR
mosip.certify.integration.scan-base-package=\
  io.mosip.certify.mock.integration,\
  io.mosip.certify.api.dataprovider.impl

# Signing
mosip.certify.data-provider-plugin.issuer.vc-sign-algo=Ed25519Signature2020
mosip.certify.data-provider-plugin.issuer-uri=did:web:shubhm-m.github.io:certify:test
mosip.certify.data-provider-plugin.issuer-public-key-uri=did:web:shubhm-m.github.io:certify:test#key-0

# APIs externas
car.token.url=...
car.client.id=...
car.client.secret=...
car.document.api.url=...
car.receipt.api.url=...
car.registration.number.url=...

caf.token.url=...
caf.client.id=...
caf.client.secret=...
caf.api.url=...

eca.token.url=https://hisrj.dataprev.gov.br/oauth2/token
eca.client.id=${ECA_CLIENT_ID}
eca.client.secret=${ECA_CLIENT_SECRET}
eca.api.url=https://hapirj.dataprev.gov.br/cpfrfb/1.0.0/v1/cpf/%s

wiremock.enabled=true
audit.enabled=${AUDIT_ENABLED:false}
```

---

### 11. DB Scripts

**Arquivo:** `docker-compose/docker-compose-injistack/certify_init.sql`
e scripts em `db_scripts/inji_certify/`

**DDL novo:**
```sql
-- coluna issuer_id na credential_config (multi-issuer)
ALTER TABLE certify.credential_config ADD COLUMN issuer_id VARCHAR(255);
CREATE INDEX idx_credential_config_issuer_id
    ON certify.credential_config(issuer_id) WHERE issuer_id IS NOT NULL;

-- tabela de audit
CREATE TABLE certify.certify_audit (
    id UUID NOT NULL,
    vc_type VARCHAR NOT NULL,
    vc_issued BOOLEAN NOT NULL,
    issued_by VARCHAR NOT NULL,
    created_date TIMESTAMP,
    issued_date TIMESTAMP,
    CONSTRAINT pk_certify_audit PRIMARY KEY (id)
);
```

**DML — inserts na `credential_config`:**
Os templates que estavam em `credential_template` no patch precisam virar inserts
na `credential_config` com:
- `vc_template` = template JSON em base64
- `issuer_id` = MGI | INCRA | MDA
- `scope` = `openid` (valor usado no `.well-known`, não no lookup)
- demais campos de display, proofTypes, etc.

Credenciais a inserir:
- `CARReceipt` (issuer_id=MGI)
- `CARReceiptAST` (issuer_id=MGI)
- `CARReceiptPCT` (issuer_id=MGI)
- `CARDocument` (issuer_id=MGI)
- `ECACredential` (issuer_id=MGI)
- `CCIRCredential` (issuer_id=INCRA)
- `CAFCredential` (issuer_id=MDA)

---

### 12. `VCIssuanceController.java` — log e well-known

**Arquivo:** `certify-service/src/main/java/io/mosip/certify/controller/VCIssuanceController.java`

**O que muda:**
```diff
+log.info("VCIssuanceController getCredential() request : {}", credentialRequest);
```
E o endpoint `.well-known` deprecated passa a aceitar `issuer_id` em vez de `version`.

**Como aplicar na v0.12.2:** O `.well-known` principal agora está em `WellKnownController`. Adicionar o param `issuer_id` lá. O log no `getCredential()` é trivial.

---

### 13. `Dockerfile` e `certify-service/configure_start.sh`

**O que muda no patch:**
```diff
-ADD configure_start.sh configure_start.sh
+ADD certify-service/configure_start.sh configure_start.sh
-COPY ./target/certify-service-*.jar certify-service.jar
+COPY ./certify-service/target/certify-service-*.jar certify-service.jar
+RUN sed -i 's/TLS_RSA_\*, //g' /opt/java/openjdk/conf/security/java.security
```

O `sed` remove `TLS_RSA_*` da lista `jdk.tls.disabledAlgorithms` do `java.security` da imagem,
reabilitando as cifras TLS necessárias para `hisrj`/`papirj.dataprev.gov.br`
(servidores Dataprev que só suportam `TLS_RSA_WITH_AES_256_GCM_SHA384`).

**Como aplicar na v0.12.2:** Adicionar o `RUN sed` no Dockerfile após o step de instalação de pacotes.

---

### 14. `Jenkinsfile` e `README.adoc`

Arquivos novos específicos da infraestrutura INJIBR (Dataprev/prevnet). Portar diretamente sem conflito.

---

## Ordem de Aplicação Recomendada

1. **DB** — migration com `issuer_id` na `credential_config` + tabela `certify_audit`
2. **certify-core** — `CredentialRequest` (campo `issuerId`) + `CredentialConfigurationService` (novo método na interface)
3. **certify-service entities/repositories** — `CredentialConfig` (campo `issuerId`) + `CredentialConfigRepository` (novo método)
4. **certify-service filter** — `AccessTokenValidationFilter` (comentar `CLIENT_ID`)
5. **certify-service proof** — `JwtProofValidator` (bypass cNonce)
6. **certify-service services** — `CredentialConfigurationServiceImpl` (novo método por issuerId) + `CertifyIssuanceServiceImpl` (lookup + docType + CARReceipt switch)
7. **certify-service controller** — `WellKnownController` (param `issuer_id`)
8. **certify-service audit** — novos arquivos de audit (entity, repository, service, aspect, config)
9. **certify-integration-api** — DataProviders + TokenClients + WebClientConfig + pom.xml
10. **Properties** — application-local.properties + certify-default.properties
11. **DB DML** — inserts na `credential_config` com templates base64 + issuer_id
12. **Dockerfile** — paths + `RUN sed` TLS_RSA
13. **Jenkinsfile + README.adoc** — infraestrutura INJIBR

---

## O que NÃO portar da v0.10.2

| Item | Motivo |
|---|---|
| `certify_keys` (tabela) | Substituída por `credential_config` com coluna `issuer_id` — não criar a tabela |
| `CertifyKeys.java` (entity) | Idem |
| `ConfigurationRepository.java` | Idem |
| `CertifyKeysService.java` | Idem — `CredentialConfigurationServiceImpl` assume essa responsabilidade |
| `CafVelocityTemplatingEngineImpl.java` | Engine único da v0.12.2 já é genérico o suficiente |
| `CarVelocityTemplatingEngineImpl.java` | Idem |
| `CarDocumentVelocityTemplatingEngineImpl.java` | Idem |
| `EcaVelocityTemplatingEngineImpl.java` | Idem |
| `VelocityTemplatingEngineFactory.java` | Idem — não há mais múltiplos engines |
| `VCIssuanceServiceImpl` sem metadata | v0.12.2 já usa `CredentialConfigurationService` |
| `credential_template` (tabela) | Substituída por `credential_config.vc_template` |
| `CredentialTemplateRepository.java` | Idem |
| Overload `validate(clientId, proof)` na interface | Substituído por bypass em `validateCNonce()` com flag `INJIBR-CUSTOM` |

---

## Pontos de Atenção

1. **Template base64:** Na v0.12.2 o `vc_template` é armazenado em base64. Os templates
   JSON do patch precisam ser convertidos para base64 antes de inserir na `credential_config`.

2. **`credentialConfigKeyId`:** Na v0.12.2 esse campo é a chave de lookup usada pelo
   `getScopeCredentialMapping`. Para o fluxo govbr, o `credentialConfigKeyId` de cada
   credencial deve ser igual ao `doctype` (ex: `CARReceipt`, `CAFCredential`) para que
   o fallback por `issuerId` + `doctype` funcione.

3. **`scope` na `credential_config`:** O campo `scope` é obrigatório (`NOT NULL`).
   Para as credenciais govbr, usar `openid`. Esse valor aparece no `.well-known`
   mas não é usado no lookup de emissão.

4. **`EcaDataProvider`:** Calcula idade a partir de `dataNascimento` e retorna apenas
   `isOver18`. Os campos `isOver12`, `isOver14`, `isOver16` estão no template mas
   o provider atual só preenche `isOver18`. Verificar se isso é intencional.

5. **`CCIRDataProvider`:** No patch está com token hardcoded e URL fixa de trial.
   Precisa ser parametrizado antes de ir para produção.

6. **cNonce bypass:** É uma solução temporária enquanto o govbr não suporta cNonce.
   A property `mosip.certify.govbr.cnonce-bypass-enabled` permite desativar o bypass
   quando o govbr passar a suportar.

7. **`RUN sed` no Dockerfile:** Necessário para conectar com
   `hisrj`/`papirj.dataprev.gov.br` que usam cifras TLS legadas (`TLS_RSA_WITH_AES_256_GCM_SHA384`).
   Verificar se a v0.12.2 já tem algum mecanismo equivalente.
