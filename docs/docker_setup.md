# Docker Compose - Setup Local (Inji Stack)

Este documento descreve as alterações feitas no diretório
`docker-compose/docker-compose-injistack/` para rodar o Inji Certify
localmente via Docker Compose, integrado aos serviços do Gov.br (ECA/Dataprev).

## Pré-requisitos

- Docker e Docker Compose
- Java 21 (para build da imagem)
- Maven (para build do projeto)
- Python 3 (para o script de teste `govbr_token.py`)
- Credenciais ECA (client_id e client_secret da Dataprev)

## Arquitetura dos serviços

```
Host (localhost)
  :3001 → Nginx (inji-web)  :3004
  :8090 → Certify            :8090
  :8099 → Mimoto             :8099
  :5434 → PostgreSQL         :5432

                 ┌──────────────────────────────┐
                 │     mosip_network (docker)    │
                 │                               │
  :3001 ──────── │  Nginx (:3004)                │
                 │    ├─ /v1/mimoto → mimoto     │
                 │    ├─ /v1/certify → certify   │
                 │    └─ static files + templates │
                 │                               │
                 │  Certify (:8090)              │
                 │    └─ PostgreSQL (:5432)      │
                 │                               │
                 │  Mimoto (:8099)               │
                 └──────────────────────────────┘
                          │
                          ▼ HTTPS
               Serviços externos:
               - Gov.br SSO (sso.staging.acesso.gov.br)
               - Dataprev ECA (hapirj.dataprev.gov.br)
               - SICAR/CAR, CAF, CCIR APIs
```

## Setup passo a passo

### 1. Build da imagem

```bash
mvn clean install -Dgpg.skip=true -Dmaven.javadoc.skip=true
docker build -t inji-certify:local -f certify-service/Dockerfile .
```

### 2. Criar a rede Docker externa

```bash
docker network create mosip_network
```

### 3. Configurar credenciais

Criar o arquivo `.env` em `docker-compose/docker-compose-injistack/`:

```env
ECA_CLIENT_ID=<seu-client-id-dataprev>
ECA_CLIENT_SECRET=<seu-client-secret-dataprev>
```

Este arquivo está no `.gitignore` e não deve ser commitado.

### 4. Subir os serviços

```bash
cd docker-compose/docker-compose-injistack
docker compose up -d
```

### 5. Testar emissão de credencial

```bash
CERTIFY_URL=http://localhost:8090/v1/certify python3 scripts/govbr_token.py
```

---

## Alterações realizadas

### docker-compose.yaml

**Serviço `certify`:**

- **Profiles Spring Boot:** `active_profile_env=default, csvdp-farmer` — carrega
  `certify-default.properties` e `certify-csvdp-farmer.properties`.
- **Variáveis de ambiente:**
  - `ECA_CLIENT_ID` e `ECA_CLIENT_SECRET` — lidos do `.env`, injetados no container
    e referenciados no `certify-csvdp-farmer.properties` via `${ECA_CLIENT_ID:placeholder}`.
  - `JAVA_TOOL_OPTIONS` — carrega override de segurança TLS (ver seção abaixo).
  - `SPRING_CONFIG_NAME=certify` e `SPRING_CONFIG_LOCATION=/home/mosip/config/` —
    faz o Spring Boot ler configs dos volumes montados em vez do config server remoto.
- **Volumes montados:** properties, CSV de dados, PKCS12 keystore, JARs de plugins
  e o arquivo `java-security-override.properties`.

**Serviço `mimoto-service`:**

- Imagem `mosipid/mimoto:0.17.1`.
- Volumes com `mimoto-issuers-config.json` configurado para apontar ao Certify
  via hostname Docker interno (`http://certify:8090`).

**Serviço `inji-web`:**

- Imagem `mosipid/inji-web:0.11.0`, porta 3001→3004.
- `nginx.conf` customizado montado como volume.
- Templates HTML de credenciais montados.

**Rede:** `mosip_network` (external) — deve ser criada antes do `docker compose up`.

### certify_init.sql

Script de inicialização do PostgreSQL. Cria:

- Schema `certify` e tabelas de key management (`key_alias`, `key_policy_def`,
  `key_store`, `ca_cert_store`).
- Tabela `certify_audit` — necessária para o audit logging funcionar.
- Tabela `credential_template` — com templates Velocity para cada tipo de credencial
  (FarmerCredential, ECACredential, CARReceipt, CARDocument, CAFCredential, etc.).
- Tabela `certify_keys` — com metadados dos issuers (MGI, INCRA, MDA) em formato JSON,
  incluindo `credential_configurations_supported` de cada um.

### nginx.conf

- **Resolver:** `resolver 127.0.0.11` (DNS interno do Docker) para resolução dinâmica
  de upstreams.
- **Proxy com variáveis:** usa `set $upstream_*` + `proxy_pass $upstream_*$request_uri`
  para que o Nginx resolva os hostnames Docker em runtime (evita falha de startup se
  um serviço não estiver pronto).
- **Rotas:**
  - `/v1/mimoto/` → `mimoto-service:8099`
  - `/v1/certify/` → `certify:8090`
  - Templates HTML servidos diretamente.
- **CORS:** headers `Access-Control-Allow-*` para chamadas do frontend.

### config/mimoto-issuers-config.json

Configuração dos issuers para o Mimoto (serviço de discovery):

- `credential_issuer_host`: `http://certify:8090` (hostname Docker interno).
- `wellknown_endpoint`: aponta ao well-known do Certify via hostname interno.
- `token_endpoint`: apontando ao Gov.br SSO (`sso.staging.acesso.gov.br/token`).
- Issuers configurados: MGI (ECA, CAR), INCRA (CCIR), MDA (CAF).

**Importante:** O Mimoto 0.17.1 remove query params do `wellknown_endpoint`, então
foi necessário implementar `getMergedIssuerMetadata()` no Certify (ver abaixo).

### config/certify-default.properties

Configurações base do Certify:

- `mosip.certify.plugin-mode=DataProvider` — Certify busca dados via plugins e
  assina a VC internamente.
- `mosipbox.public.url=http://certify:8090` — URL interna usada como `credential_issuer`.
- `mosip.certify.authorization.url=https://sso.staging.acesso.gov.br` — Gov.br SSO.
- `spring.datasource.url=jdbc:postgresql://database:5432/inji_certify` — banco dentro
  da rede Docker.
- Cache simples (sem Redis) com TTLs para `userinfo` e `vcissuance`.

### config/certify-csvdp-farmer.properties

Configurações dos data providers (APIs externas):

- **ECA (Dataprev):** endpoints `hisrj.dataprev.gov.br` (token) e
  `hapirj.dataprev.gov.br` (dados). Credenciais via env vars.
- **CAR (SICAR):** endpoints para documento, recibo e consulta por CPF/CNPJ.
- **CAF (MDA):** endpoint de consulta por CPF.
- **Issuer DID:** `did:web:vharsh.github.io:DID:harsh` com assinatura Ed25519.
- **Scan base package:** `io.mosip.certify.api.dataprovider` — onde estão os plugins.

---

## Fix: TLS handshake_failure com Dataprev

### Problema

O servidor da Dataprev (`hapirj.dataprev.gov.br`) só aceita o cipher suite
`TLS_RSA_WITH_AES_256_GCM_SHA384`, que usa RSA key exchange (sem forward secrecy).

O Java 21 (eclipse-temurin:21-jre) desabilita todos os ciphers `TLS_RSA_*` por padrão
no arquivo `java.security`:

```
jdk.tls.disabledAlgorithms=..., TLS_RSA_*, ...
```

Resultado: o Java se recusa a negociar com o servidor → `handshake_failure`.

### Solução

**Arquivo `config/java-security-override.properties`:** contém a mesma lista de
`jdk.tls.disabledAlgorithms` do Java 21, mas **sem `TLS_RSA_*`**. Isso re-habilita
os ciphers RSA apenas neste container.

**No `docker-compose.yaml`:**

```yaml
environment:
  - JAVA_TOOL_OPTIONS=-Djava.security.properties=/home/mosip/config/java-security-override.properties
volumes:
  - ./config/java-security-override.properties:/home/mosip/config/java-security-override.properties
```

### Por que `-Djdk.tls.disabledAlgorithms=...` não funciona

`jdk.tls.disabledAlgorithms` é uma **security property**, não uma system property.
Flags `-D` definem system properties. Security properties só podem ser alteradas via
arquivo de override (`-Djava.security.properties=<path>`) ou programaticamente
(`Security.setProperty()`).

A flag `-Djava.security.properties=` (um `=`) faz merge com o `java.security` padrão,
sobrescrevendo apenas as propriedades presentes no arquivo de override.

---

## Fix: Well-known sem issuer_id

### Problema

O Mimoto 0.17.1 faz strip dos query params ao chamar o `wellknown_endpoint`.
O Certify precisa do `?issuer_id=MGI` para saber qual issuer retornar.

### Solução

Adicionado o método `getMergedIssuerMetadata()` em `CertifyIssuanceServiceImpl.java`.
Quando `issuer_id` não é fornecido (ou é `latest`), o Certify retorna os metadados
combinados de **todos** os issuers, mesclando os `credential_configurations_supported`
de cada um em uma única resposta.

---

## Fix: Nginx upstream resolution

### Problema

O Nginx falha ao iniciar se os hostnames dos upstreams (`certify`, `mimoto-service`)
não estiverem resolvíveis no momento do parse da config.

### Solução

Usar variáveis no `proxy_pass` força o Nginx a resolver em runtime:

```nginx
resolver 127.0.0.11;

location /v1/certify/ {
    set $upstream_certify http://certify:8090;
    proxy_pass $upstream_certify$request_uri;
}
```

O `resolver 127.0.0.11` usa o DNS interno do Docker.

---

## Estrutura de arquivos

```
docker-compose/docker-compose-injistack/
├── .env                          # Credenciais ECA (não commitado)
├── docker-compose.yaml           # Orquestração dos serviços
├── certify_init.sql              # Schema + dados iniciais do banco
├── nginx.conf                    # Proxy reverso + CORS
├── config/
│   ├── certify-default.properties          # Config base do Certify
│   ├── certify-csvdp-farmer.properties     # Data providers (ECA, CAR, CAF)
│   ├── java-security-override.properties   # Fix TLS para Dataprev
│   ├── mimoto-default.properties           # Config do Mimoto
│   ├── mimoto-issuers-config.json          # Issuers para discovery
│   ├── mimoto-trusted-verifiers.json       # Verifiers confiáveis
│   └── farmer_identity_data.csv            # Dados mock para FarmerCredential
├── data/
│   └── CERTIFY_PKCS12/                     # Keystore para assinatura de VCs
├── certs/
│   └── oidckeystore.p12                    # Keystore OIDC do Mimoto
└── loader_path/
    └── certify/                            # JARs de plugins (data providers)
```
