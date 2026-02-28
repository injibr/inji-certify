# Plano de Merge: injibr/inji-certify com upstream mosip/inji-certify

**Data:** 2026-02-28
**Branch de trabalho:** `master-updated`
**Branch de backup:** `master-backup`

## Contexto

O repositorio `injibr/inji-certify` foi criado copiando o codigo do `mosip/inji-certify`
(nao via GitHub Fork). Por isso, os commits possuem **hashes diferentes** mesmo quando
o conteudo e identico. O Git nao reconhece ancestral comum entre os dois repositorios
("unrelated histories").

- **Fork (injibr):** 294 commits no master, parado na versao **0.11.x**
- **Upstream (mosip):** 461 commits no master, atualmente na versao **0.13.x**
- **Branch fix_424:** ~125 commits a frente do master do fork, contendo todas as
  customizacoes brasileiras

## Por que nao funciona um merge direto?

```
$ git merge upstream/master
fatal: refusing to merge unrelated histories
```

O Git trata os repositorios como completamente independentes. Usar
`--allow-unrelated-histories` geraria conflitos em praticamente todos os arquivos,
tornando a resolucao impraticavel.

## Estrategia adotada

### Abordagem: "Novo branch a partir do upstream + reaplicacao das customizacoes"

1. Criar branch `master-updated` a partir de `upstream/master` (base limpa e atualizada)
2. Reaplicar apenas os commits/mudancas exclusivos do fork brasileiro
3. Ignorar commits de ruido (testes de CI, iteracoes de Jenkinsfile, bumps de versao ja superados)
4. Validar com build (`mvn clean install`)

### Por que essa abordagem?

- Garante que o codigo base esta 100% atualizado com o upstream
- Evita arrastar commits de versao/release antigos que nao fazem mais sentido
- Permite revisar cada customizacao antes de reaplicar
- Resultado: historico limpo com upstream atual + customizacoes brasileiras

## Inventario de customizacoes do fork (commits exclusivos)

### 1. Data Providers brasileiros (core business)
Arquivos novos criados pelo fork para integracao com servicos brasileiros:

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
- `certify-integration-api/src/test/java/.../EcaTokenClientTest.java`
- `certify-integration-api/src/main/java/.../config/WebClientConfig.java`

### 2. Velocity Template Engines customizados
- `certify-service/src/main/java/.../vcformatters/CafVelocityTemplatingEngineImpl.java`
- `certify-service/src/main/java/.../vcformatters/CarDocumentVelocityTemplatingEngineImpl.java`
- `certify-service/src/main/java/.../vcformatters/CarVelocityTemplatingEngineImpl.java`
- `certify-service/src/main/java/.../vcformatters/EcaVelocityTemplatingEngineImpl.java`
- `certify-service/src/main/java/.../vcformatters/VelocityTemplatingEngineFactory.java`

### 3. Funcionalidade de Auditoria
- `certify-service/src/main/java/.../aspect/ControllerAuditAspect.java`
- `certify-service/src/main/java/.../config/AuditConfig.java`
- `certify-service/src/main/java/.../entity/CertifyAudit.java`
- `certify-service/src/main/java/.../repository/CertifyAuditRepository.java`
- `certify-service/src/main/java/.../services/CertifyAuditService.java`
- `certify-service/src/main/java/.../services/CertifyAuditServiceImpl.java`

### 4. Configuracao e Docker
- `certify-service/Dockerfile` (customizacoes)
- `certify-service/configure_start.sh`
- `docker-compose/docker-compose-injistack/` (configuracoes locais)
- `certify-service/src/main/resources/application-local.properties`

### 5. Scripts e documentacao
- `scripts/govbr_token.py` - script de teste OID4VCI com Gov.br
- `scripts/.gitignore`
- `update_script.sh` - script de atualizacao de versao nos POMs
- `docs/setup_environment.md` - guia de setup
- `Jenkinsfile` - pipeline CI
- `README_update_script_bash.md`

### 6. Modificacoes em arquivos existentes do upstream
Estes precisam de atencao especial pois podem conflitar:
- `certify-service/src/main/java/.../services/CertifyIssuanceServiceImpl.java`
- `certify-service/src/main/java/.../services/VCIssuanceServiceImpl.java`
- `certify-service/src/main/java/.../services/CertifyKeysService.java`
- `certify-service/src/main/java/.../controller/VCIssuanceController.java`
- `certify-service/src/main/java/.../filter/AccessTokenValidationFilter.java`
- `certify-service/src/main/java/.../proof/JwtProofValidator.java`
- `certify-core/src/main/java/.../core/constants/Constants.java`
- `pom.xml`, `certify-core/pom.xml`, `certify-service/pom.xml`
- `.gitignore`

## Commits ignorados (ruido)

Os seguintes tipos de commits NAO serao reaplicados:
- ~30 commits de iteracao do Jenkinsfile (`test: jenkinsfile`, `fix jenkinsfile`, etc.)
- ~15 commits de teste de Dockerfile (`test dockerfile`, `ajuste dockerfile p teste`, etc.)
- ~10 commits de bump de versao superados (`update version`, `new version 3.0.0`, etc.)
- Commits de merge internos do fork
- Commits que espelhavam releases upstream (0.10.0, 0.10.1, 0.10.2, 0.11.0)

## Passos de execucao

1. [x] Adicionar remote upstream: `git remote add upstream https://github.com/mosip/inji-certify.git`
2. [x] Fetch upstream: `git fetch upstream`
3. [x] Criar backup: `git branch master-backup master`
4. [ ] Criar branch `master-updated` a partir de `upstream/master`
5. [ ] Copiar arquivos novos (data providers, audit, templates, scripts)
6. [ ] Aplicar modificacoes nos arquivos existentes do upstream
7. [ ] Ajustar POMs e configuracoes
8. [ ] Testar build: `mvn clean install -Dgpg.skip=true -Dmaven.javadoc.skip=true`
9. [ ] Resolver eventuais erros de compilacao
10. [ ] Quando estavel, substituir master

## Rollback

Se algo der errado, o branch `master-backup` contem o estado original do master do fork.
O branch `fix_424` permanece intacto como referencia das customizacoes.
