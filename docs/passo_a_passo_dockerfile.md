# Passo a passo: Dockerfile autossuficiente (sem docker-compose)

Como rodar o Inji Certify usando apenas `docker build` + `docker run`, sem docker-compose. Útil para ambientes de homolog onde configs precisam estar baked na imagem.

## Pré-requisitos

- Docker instalado e rodando
- Java 21 e Maven (para compilar o JAR)
- Python 3.8+ (para testar com `govbr_token.py`)
- Credenciais ECA (Dataprev) em `docker-compose/docker-compose-injistack/.env`

## Passo 0: Limpar ambiente anterior

```bash
docker rm -f certify database 2>/dev/null
docker network rm mosip_network 2>/dev/null
echo "Ambiente limpo"
```

## Passo 1: Compilar o projeto

> **Nota:** O `pom.xml` já inclui os fixes necessários para Java 21:
> Lombok 1.18.42, maven-compiler-plugin 3.13.0, `<fork>true</fork>` e `<proc>full</proc>`.
> Sem esses ajustes, o Lombok falha com `TypeTag :: UNKNOWN` no Java 21.

```bash
mvn clean install -Dgpg.skip=true -Dmaven.javadoc.skip=true -DskipTests
```

Resultado esperado: `BUILD SUCCESS` nos 4 módulos (certify, certify-integration-api, certify-core, certify-service).

## Passo 2: Build da imagem

O Dockerfile já inclui configs, plugin e keystore embutidos na imagem:

```bash
docker build -t inji-certify:local -f certify-service/Dockerfile .
```

## Passo 3: Criar rede Docker

```bash
docker network create mosip_network 2>/dev/null || true
```

## Passo 4: Subir PostgreSQL e inicializar banco

```bash
docker run -d \
  --name database \
  --network mosip_network \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:latest
```

Aguardar o banco ficar pronto (~3s) e rodar o init SQL:

```bash
sleep 3
docker exec database pg_isready -U postgres

docker cp docker-compose/docker-compose-injistack/certify_init.sql database:/tmp/certify_init.sql
docker exec database psql -U postgres -d postgres -f /tmp/certify_init.sql
```

Verificar que as 7 tabelas foram criadas:

```bash
docker exec database psql -U postgres -d inji_certify -c "\dt certify.*"
```

Resultado esperado: `ca_cert_store`, `certify_keys`, `credential_template`, `key_alias`, `key_policy_def`, `key_store`, `rendering_template`.

> **Importante:** Use `certify_init.sql` (do docker-compose), que é o script completo com schema, DDL e dados de seed. Os scripts em `db_scripts/` também funcionam via `deploy.sh`, mas o `certify_init.sql` é mais direto.

## Passo 5: Rodar o Certify

As credenciais ECA estão em `docker-compose/docker-compose-injistack/.env`.

```bash
docker run -d \
  --name certify \
  --network mosip_network \
  -p 8090:8090 \
  -e ECA_CLIENT_ID=<seu_client_id> \
  -e ECA_CLIENT_SECRET=<seu_client_secret> \
  inji-certify:local
```

Aguardar ~15s e verificar que iniciou:

```bash
docker logs certify 2>&1 | grep "Started CertifyServiceApplication"
```

Se não aparecer a mensagem de "Started", verificar erros:

```bash
docker logs certify 2>&1 | grep -E "ERROR|Application run failed"
```

## Passo 6: Testar emissão de credencial

```bash
python3 scripts/govbr_token.py --no-cache
```

Resultado esperado: `CREDENTIAL ISSUED SUCCESSFULLY!` com HTTP 200.

## Troubleshooting

- **`cannot find symbol: variable log` no build**: Lombok incompatível com Java 21. Verifique que `pom.xml` tem Lombok >= 1.18.42, maven-compiler-plugin >= 3.13.0, `<fork>true</fork>` e `<proc>full</proc>`.
- **`relation "certify_keys" does not exist`**: O banco não foi inicializado com `certify_init.sql`. Rode o Passo 4 novamente.
- **`relation "key_alias" does not exist`**: Mesmo problema acima.
- **Container certify para imediatamente**: Verifique `docker logs certify` — provavelmente erro de banco.
- **`pg_isready` falha**: O PostgreSQL ainda está iniciando. Aguarde mais alguns segundos.

## Diferenças em relação ao docker-compose

| Aspecto | docker-compose | Dockerfile autossuficiente |
|---|---|---|
| Configs | Montadas como volumes | Embutidas na imagem (COPY) |
| Plugin JAR | Volume em `loader_path/` | Copiado para `additional_jars/` |
| Keystore PKCS12 | Volume em `data/` | Copiado na imagem |
| PostgreSQL | Sobe junto no compose | Container separado |
| Segredos (ECA) | `.env` do compose | `-e` no `docker run` |
| Init do banco | `certify_init.sql` via volume | `docker cp` + `docker exec psql` |
