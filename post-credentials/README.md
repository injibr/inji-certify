# post-credentials

Payloads para cadastrar as configurações de credenciais INJIBR via API REST.

## Ambientes

| Ambiente | Base URL |
|---|---|
| Local K8s | `http://localhost:30090/v1/certify` |
| Dev | `https://injicertify.credenciaisverificaveis-dev.dataprev.gov.br/v1/certify` |
| Homolog | `https://injicertify.credenciaisverificaveis-hml.dataprev.gov.br/v1/certify` |

## Autenticação

O endpoint `/credential-configurations` exige um Bearer token válido do Gov.br SSO.

```bash
# Defina o token e a URL base antes de usar os comandos abaixo
TOKEN="<access_token_do_govbr>"
BASE_URL="https://injicertify.credenciaisverificaveis-dev.dataprev.gov.br/v1/certify"
```

> **Dica:** O token pode ser obtido via scripts em `../scripts/` ou do arquivo `.token_cache.json`.

---

## Credenciais disponíveis

| Arquivo | issuerId | Tipo |
|---|---|---|
| `CARReceipt.json` | MGI | Recibo CAR (o certify decide AST/PCT internamente via `tipoImovel`) |
| `CARDocument.json` | MGI | Demonstrativo CAR |
| `ECACredential.json` | MGI | ECA - Verificação de Idade |
| `ECACredential-mdoc.json` | MGI | ECA - Verificação de Idade (formato mso_mdoc) |
| `CCIRCredential.json` | INCRA | CCIR - Certificado de Cadastro de Imóvel Rural |
| `CAFCredential.json` | MDA | CAF - Cadastro Nacional da Agricultura Familiar |

> **Nota:** `CARReceiptAST` e `CARReceiptPCT` são variantes internas do `CARReceipt`.
> O certify seleciona o template correto automaticamente com base no campo `tipoImovel`
> retornado pela API do SICAR.

---

## Consultar uma configuração (GET)

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/credential-configurations/ECACredential"
```

Exemplos com outros IDs:
```bash
curl -s -H "Authorization: Bearer $TOKEN" "$BASE_URL/credential-configurations/CARReceipt"
curl -s -H "Authorization: Bearer $TOKEN" "$BASE_URL/credential-configurations/CARDocument"
curl -s -H "Authorization: Bearer $TOKEN" "$BASE_URL/credential-configurations/CCIRCredential"
curl -s -H "Authorization: Bearer $TOKEN" "$BASE_URL/credential-configurations/CAFCredential"
curl -s -H "Authorization: Bearer $TOKEN" "$BASE_URL/credential-configurations/ECACredential-mdoc"
```

---

## Cadastrar uma configuração (POST)

```bash
curl -s -w "\n%{http_code}" -X POST "$BASE_URL/credential-configurations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d @CARReceipt.json
```

---

## Atualizar uma configuração (PUT)

```bash
curl -s -w "\n%{http_code}" -X PUT "$BASE_URL/credential-configurations/CARReceipt" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d @CARReceipt.json
```

---

## Deletar uma configuração (DELETE)

```bash
curl -s -w "\n%{http_code}" -X DELETE "$BASE_URL/credential-configurations/CARReceipt" \
  -H "Authorization: Bearer $TOKEN"
```

---

## Scripts em lote

### Cadastrar todas
```bash
for f in *.json; do
  echo "Cadastrando $f..."
  curl -s -w "\n%{http_code}" -X POST "$BASE_URL/credential-configurations" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d @"$f"
  echo ""
done
```

### Atualizar todas (PUT)
```bash
for f in *.json; do
  key=$(python -c "import json; print(json.load(open('$f', encoding='utf-8'))['credentialConfigKeyId'])")
  echo "Atualizando $f ($key)..."
  curl -s -w "\n%{http_code}" -X PUT "$BASE_URL/credential-configurations/$key" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d @"$f"
  echo ""
done
```

### Deletar todas
```bash
for f in *.json; do
  key=$(python -c "import json; print(json.load(open('$f', encoding='utf-8'))['credentialConfigKeyId'])")
  echo "Deletando $key..."
  curl -s -w "\n%{http_code}" -X DELETE "$BASE_URL/credential-configurations/$key" \
    -H "Authorization: Bearer $TOKEN"
  echo ""
done
```

---

## Consultar via well-known (sem autenticação)

Para ver todas as credenciais publicadas de um issuer (não exige token):

```bash
curl -s "$BASE_URL/issuance/.well-known/openid-credential-issuer?issuer_id=MGI"
curl -s "$BASE_URL/issuance/.well-known/openid-credential-issuer?issuer_id=INCRA"
curl -s "$BASE_URL/issuance/.well-known/openid-credential-issuer?issuer_id=MDA"
```

---

## Observações

- O campo `vcTemplate` é o template Velocity em base64
- O campo `credentialSubjectDefinition` define os campos exibíveis da credencial
- O campo `issuerId` identifica o emissor lógico (MGI, INCRA, MDA) para o lookup multi-issuer govbr
- O campo `keyManagerAppId` e `keyManagerRefId` identificam o par de chaves no keymanager para assinar a VC
- O campo `scope` é `openid` para todas as credenciais govbr

## DIDs por Issuer

| Ambiente | MGI | INCRA | MDA |
|---|---|---|---|
| Dev | `did:web:injibr.github.io:inji-did:dev:mgi` | `did:web:injibr.github.io:inji-did:dev:incra` | `did:web:injibr.github.io:inji-did:dev:mda` |
| Local K8s | `did:web:did-server.inji-local.svc.cluster.local:mgi` | `did:web:did-server.inji-local.svc.cluster.local:incra` | `did:web:did-server.inji-local.svc.cluster.local:mda` |

> **Atenção (ambiente local):** O did-server local só possui o DID do MGI (`/mgi/did.json`).
> Os DIDs de INCRA e MDA não existem no did-server local.
> Em produção/homologação cada issuer terá seu próprio `did.json` com as chaves corretas.
