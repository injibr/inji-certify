# Passo a passo: Docker Compose do zero

Como subir o Inji Certify localmente com Docker Compose e testar a emissão de credenciais com o script `govbr_token.py`.

## Pré-requisitos

- Docker instalado e rodando
- Java 21 e Maven (para compilar o JAR, se necessário)
- Python 3.8+
- Credenciais ECA (Dataprev) e SSO (Gov.br) configuradas no `.envrc`

## Passo 1: Compilar o projeto (se ainda não tiver o JAR)

```bash
mvn clean install -Dgpg.skip=true -Dmaven.javadoc.skip=true
```

## Passo 2: Construir a imagem Docker

```bash
docker build -t inji-certify:local -f certify-service/Dockerfile .
```

## Passo 3: Configurar credenciais ECA

Editar `docker-compose/docker-compose-injistack/.env`:

```
ECA_CLIENT_ID=<seu_client_id>
ECA_CLIENT_SECRET=<seu_client_secret>
```

## Passo 4: Criar rede Docker (se não existir)

```bash
docker network create --driver bridge mosip_network
```

## Passo 5: Subir os containers

```bash
cd docker-compose/docker-compose-injistack
docker compose up -d database certify
```

Aguardar ~30 segundos para o Certify iniciar. Para verificar:

```bash
curl -s "http://localhost:8090/v1/certify/issuance/.well-known/openid-credential-issuer?issuer_id=MGI" | head -5
```

## Passo 6: Rodar o script de teste

```bash
cd ../..
source .envrc
python3 scripts/govbr_token.py --no-cache
```

O browser abrirá para login no Gov.br. Após autenticação, o script emite uma ECACredential automaticamente.

## Derrubar tudo

```bash
cd docker-compose/docker-compose-injistack
docker compose down -v
```
