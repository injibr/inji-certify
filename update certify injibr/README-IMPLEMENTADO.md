# README — Alterações Implementadas (v0.12.2-injibr)

Este documento descreve todas as alterações efetivamente implementadas na migração
da branch `entrega` (baseada em v0.10.2) para a v0.12.2, nesta sessão de trabalho.

---

## 1. `certify-service` — VelocityTemplatingEngineImpl

**Arquivo:** `certify-service/src/main/java/io/mosip/certify/vcformatters/VelocityTemplatingEngineImpl.java`

### Alterações sobre o upstream v0.12.2:

**a) ECA expiry separado + método `calculateExpiryTime()`**
```java
// INJIBR-CUSTOM: ECA has shorter expiry since age verification may change
@Value("${mosip.certify.data-provider-plugin.eca.vc-expiry-duration:P90d}")
String ecaExpiryDuration;
```
A lógica de seleção de expiry foi extraída para um método privado chamado pelos dois overloads de `format()`:
```java
// INJIBR-CUSTOM: ECA has shorter expiry; centralizes expiry logic for both format() overloads
private String calculateExpiryTime(String templateName) {
    String expiryToUse = templateName.contains("ECACredential") ? ecaExpiryDuration : defaultExpiryDuration;
    Duration duration;
    try {
        duration = Duration.parse(expiryToUse);
    } catch (DateTimeParseException e) {
        duration = Duration.parse("P730D");
    }
    return ZonedDateTime.now(ZoneOffset.UTC)
            .plusSeconds(duration.getSeconds())
            .format(DateTimeFormatter.ofPattern(Constants.UTC_DATETIME_PATTERN));
}
```
**Motivo:** o fluxo DataProvider chama `format(Map<String, Object>)` (segundo overload), que no upstream tinha o expiry hardcoded em `plusYears(2)`. Sem o método extraído, o `ecaExpiryDuration` só seria aplicado no primeiro overload (`format(JSONObject, Map)`), que não é chamado nesse fluxo.

**b) `jsonify()` — 3 ajustes sobre o upstream:**
- Null-safe: `if (value == null) { finalTemplate.put(key, ""); continue; }` — APIs govbr retornam campos nulos
- `BigDecimal` adicionado no branch numérico — campos de área/módulo das APIs govbr
- `Boolean` adicionado no branch numérico — campo `isOver18` do ECA

---

## 2. `certify-service` — CertifyIssuanceServiceImpl

**Arquivo:** `certify-service/src/main/java/io/mosip/certify/services/CertifyIssuanceServiceImpl.java`

### Alterações sobre o upstream v0.12.2:

**a) Multi-issuer: `didUrl` por `credential_config`**
```java
// INJIBR-CUSTOM: usa didUrl da credential_config para suportar multi-issuer (MGI, INCRA, MDA)
templateParams.put(Constants.DID_URL, vcFormatter.getDidUrl(templateName));
```
Antes usava `@Value("${mosip.certify.data-provider-plugin.did-url}")` fixo para todos os issuers.

**b) Lookup por `issuerId` + `doctype` (fallback govbr)**

O token govbr não identifica o tipo de credencial pelo `scope`. Fluxo:
1. Tenta lookup pelo `scope` do token (compatibilidade esignet)
2. Se não encontrar, chama `resolveCredentialMetadata()` que filtra `credential_config` por `issuerId` + `doctype`

**c) cNonce bypass**

O govbr não envia cNonce. O `getValidClientNonce()` é chamado dentro de try/catch — se falhar, continua com `validCNonce = null`. O bypass real está no `JwtProofValidator.validateCNonce()`.

**d) `docType` injetado nos claims**
```java
// INJIBR-CUSTOM: DataProviderPluginImpl dispatches by docType; inject it into claims
parsedAccessToken.getClaims().put("docType", credentialRequest.getDoctype());
```

**e) CARReceipt switch AST/PCT**
```java
// INJIBR-CUSTOM: CARReceipt has two subtypes (AST/PCT) determined by data returned from provider
if ("CARReceipt".equals(credentialRequest.getDoctype())) {
    String tipoImovel = jsonObject.optString("tipoImovel");
    if ("AST".equals(tipoImovel)) vcRequestDto.setType(List.of("CARReceiptAST", "VerifiableCredential"));
    else if ("PCT".equals(tipoImovel)) vcRequestDto.setType(List.of("CARReceiptPCT", "VerifiableCredential"));
}
```

---

## 3. `certify-integration-api` — DataProviders

**Módulo:** `certify-integration-api`

Todos os arquivos abaixo são novos (não existiam no upstream v0.12.2):

| Arquivo | Descrição |
|---|---|
| `api/config/WebClientConfig.java` | Bean `WebClient` |
| `api/dataprovider/DataProviderService.java` | Interface com `getDocumentType()` e `getData(cpf)` |
| `api/dataprovider/impl/DataProviderPluginImpl.java` | Implementa `DataProviderPlugin`, despacha por `docType` |
| `api/dataprovider/impl/CARDataProvider.java` | Busca dados do CAR (demonstrativo) via SICAR |
| `api/dataprovider/impl/CARReceiptDataProvider.java` | Busca recibo do CAR via SICAR |
| `api/dataprovider/impl/CAFDataProvider.java` | Busca dados do CAF — flatten de `caf`/`membros`/`areas` |
| `api/dataprovider/impl/CCIRDataProvider.java` | Busca dados do CCIR — flatten de `ccir`/`titulares` |
| `api/dataprovider/impl/EcaDataProvider.java` | Busca dados ECA, calcula `isOver18` |
| `api/dataprovider/impl/CarTokenClient.java` | OAuth2 token client para CAR/SICAR |
| `api/dataprovider/impl/CafTokenClient.java` | OAuth2 token client para CAF |
| `api/dataprovider/impl/EcaTokenClient.java` | OAuth2 token client para ECA |
| `api/dataprovider/impl/CCIRTokenClient.java` | OAuth2 token client para CCIR/SNCR |
| `api/dataprovider/impl/SicarCpfCnpjClient.java` | Busca `codigoImovel` por CPF no SICAR (CAR) |
| `api/dataprovider/impl/SncrCpfCnpjClient.java` | Busca `codigoImovel` por CPF no SNCR (CCIR) |

### Detalhes relevantes por provider:

**`CAFDataProvider`** — a API retorna `{ "caf": {...}, "membros": [...], "areas": [...] }`.
O método `flattenCaf()` achata o objeto `caf` pro root, achata `entidadeEmissora` → `cnpj`/`razaoSocial`/`emissor`,
e serializa `membros` e `areas` como strings JSON escapadas para o template Velocity.

**`CCIRDataProvider`** — a API retorna `{ "ccir": {...} }`.
O método `flattenCcir()` achata o objeto `ccir` pro root.
O método `flattenTitulares()` extrai os campos do titular com `declarante=1` pro root
(substituindo o campo `declarante` pelo `nomeTitular`) e serializa o array filtrado
como string JSON escapada para o campo `$titulares` no template.

---

## 4. `certify-integration-api` — SncrCpfCnpjClient

**Arquivo:** `certify-integration-api/src/main/java/io/mosip/certify/api/dataprovider/impl/SncrCpfCnpjClient.java`

Arquivo novo. Busca o `codigoImovel` pelo CPF via `consultarImovelPorCpfCnpj` e retorna o primeiro elemento do array.

---

## 5. `post-credentials` — JSONs de configuração de credenciais

**Pasta:** `post-credentials/`

Arquivos usados para popular a tabela `credential_config` via `POST /v1/certify/credential-configurations`.

### Alterações:

- `CCIRCredential.json` — `vcTemplate` atualizado para bater com `cm-sql-populate-base.yaml`
- `CARReceipt.json` — `vcTemplate` atualizado para bater com `cm-sql-populate-base.yaml`
- `CARDocument.json` — `vcTemplate` atualizado para bater com `cm-sql-populate-base.yaml`
- `CAFCredential.json` — já estava correto
- `ECACredential.json` — já estava correto
- `CARReceiptAST.json` — **novo** — subtipo interno do CARReceipt para imóveis AST
- `CARReceiptPCT.json` — **novo** — subtipo interno do CARReceipt para imóveis PCT

### Observações importantes:

- `CARReceiptAST` e `CARReceiptPCT` **não aparecem no InjWeb** pois são criados com `INACTIVE` via SQL após o POST
- O usuário sempre solicita `CARReceipt` — o certify decide AST ou PCT baseado no campo `tipoImovel` da API
- O campo `credentialSubjectDefinition` popula a coluna `credential_subject` da `credential_config` e é usado pelo `.well-known` para expor os campos da credencial ao wallet
- O campo `issuerId` identifica o emissor lógico (MGI, INCRA, MDA) para o lookup multi-issuer govbr

### Como popular:

```bash
# DELETE dos existentes
curl -X DELETE http://localhost:30090/v1/certify/credential-configurations/CARReceipt
curl -X DELETE http://localhost:30090/v1/certify/credential-configurations/CARDocument
curl -X DELETE http://localhost:30090/v1/certify/credential-configurations/ECACredential
curl -X DELETE http://localhost:30090/v1/certify/credential-configurations/CCIRCredential
curl -X DELETE http://localhost:30090/v1/certify/credential-configurations/CAFCredential
curl -X DELETE http://localhost:30090/v1/certify/credential-configurations/CARReceiptAST
curl -X DELETE http://localhost:30090/v1/certify/credential-configurations/CARReceiptPCT

# POST dos novos (dentro da pasta post-credentials)
for f in CARReceipt.json CARDocument.json ECACredential.json CCIRCredential.json CAFCredential.json CARReceiptAST.json CARReceiptPCT.json; do
  echo "Postando $f..."
  curl -s -w "\n%{http_code}" -X POST http://localhost:30090/v1/certify/credential-configurations \
    -H "Content-Type: application/json" -d @"$f"
  echo
done

# Desativar subtipos internos do CARReceipt
UPDATE certify.credential_config
SET status = 'INACTIVE'
WHERE credential_config_key_id IN ('CARReceiptAST', 'CARReceiptPCT');
```

---

## 6. Arquitetura — decisões tomadas

### Por que 1 único VelocityTemplatingEngineImpl em vez de múltiplos engines?

O upstream v0.12.2 introduziu a camada `Credential`/`CredentialFactory`/`W3CJsonLD` que acopla
o `VCFormatter` dentro do `Credential`. Não é possível usar múltiplos engines sem refatorar
`W3CJsonLD` e `CredentialFactory`. A lógica específica de cada credencial foi absorvida
pelos próprios DataProviders (que entregam os dados já preparados para o template).

### Por que o flatten está no DataProvider e não no Velocity?

No SCM (v0.10.2) o flatten estava nos engines Velocity separados. Na v0.12.2, como há
um único engine, a responsabilidade de preparar os dados foi movida para os DataProviders,
que entregam os campos já no formato esperado pelo template Velocity.

### Multi-issuer

Cada `credential_config` tem seu próprio `didUrl`, `keyManagerAppId`, `keyManagerRefId`.
O `vcFormatter.getDidUrl(templateName)` busca o `didUrl` correto por credencial,
garantindo que cada issuer (MGI, INCRA, MDA) assine com sua própria chave.

---

## 7. O que ainda falta implementar (do PLANO-MIGRACAO.md)

| Item | Status |
|---|---|
| `AccessTokenValidationFilter` — comentar `CLIENT_ID` | ✅ implementado |
| `JwtProofValidator` — bypass cNonce | ✅ implementado |
| `CredentialRequest` — campo `issuerId` | ✅ implementado |
| `CertifyIssuanceServiceImpl` — lookup multi-issuer + docType + CARReceipt switch | ✅ implementado |
| `CredentialConfig` — coluna `issuer_id` | ✅ implementado |
| `CredentialConfigRepository` — `findByIssuerId` | ✅ implementado |
| `CredentialConfigurationServiceImpl` — `fetchCredentialIssuerMetadataByIssuerId` | ✅ implementado |
| `WellKnownController` — param `issuer_id` | ✅ implementado |
| DataProviders + TokenClients | ✅ implementado |
| `post-credentials` JSONs | ✅ implementado |
| Audit (entity, repository, service) | ✅ implementado — `CertifyAudit`, `CertifyAuditRepository`, `CertifyAuditService`, `CertifyAuditServiceImpl` |
| Audit aspect + config | ✅ implementado — `ControllerAuditAspect`, `AuditConfig` |
| `Dockerfile` — paths + `RUN sed` TLS_RSA | ✅ implementado |
| `Jenkinsfile` + `README.adoc` | ✅ já existiam |
