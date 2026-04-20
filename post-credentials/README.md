# post-credentials

Payloads para cadastrar as configurações de credenciais INJIBR via API REST.

## Endpoint

```
POST http://localhost:8090/v1/certify/credential-configurations
Content-Type: application/json
```

## Credenciais

| Arquivo | issuerId | Tipo |
|---|---|---|
| `CARReceipt.json` | MGI | Recibo CAR (o certify decide AST/PCT internamente via `tipoImovel`) |
| `CARDocument.json` | MGI | Demonstrativo CAR |
| `ECACredential.json` | MGI | ECA - Verificação de Idade |
| `CCIRCredential.json` | INCRA | CCIR - Certificado de Cadastro de Imóvel Rural |
| `CAFCredential.json` | MDA | CAF - Cadastro Ambiental Familiar |

> **Nota:** `CARReceiptAST` e `CARReceiptPCT` são variantes internas do `CARReceipt`.
> O certify seleciona o template correto automaticamente com base no campo `tipoImovel`
> retornado pela API do SICAR. Eles existem na tabela `credential_template` mas **não**
> devem ser registrados via este endpoint.

## Como usar

### curl
```bash
curl -X POST http://localhost:8090/v1/certify/credential-configurations \
  -H "Content-Type: application/json" \
  -d @CARReceipt.json
```

### Script para cadastrar todas
```bash
for f in *.json; do
  echo "Cadastrando $f..."
  curl -s -w "\n%{http_code}" -X POST http://localhost:8090/v1/certify/credential-configurations \
    -H "Content-Type: application/json" \
    -d @"$f"
  echo ""
done
```

## Observações

- O campo `vcTemplate` é o template Velocity em base64
- O campo `credentialSubjectDefinition` define os campos exibíveis da credencial
- O campo `issuerId` identifica o emissor lógico (MGI, INCRA, MDA) para o lookup multi-issuer govbr
- O campo `keyManagerAppId` e `keyManagerRefId` identificam o par de chaves no keymanager para assinar a VC
- O campo `scope` é `openid` para todas as credenciais govbr

## DIDs por Issuer (ambiente local)

| Arquivo | issuerId | didUrl |
|---|---|---|
| `CARReceipt.json` | MGI | `did:web:did-server.inji-local.svc.cluster.local:mgi` |
| `CARDocument.json` | MGI | `did:web:did-server.inji-local.svc.cluster.local:mgi` |
| `ECACredential.json` | MGI | `did:web:did-server.inji-local.svc.cluster.local:mgi` |
| `CCIRCredential.json` | INCRA | `did:web:did-server.inji-local.svc.cluster.local:incra` |
| `CAFCredential.json` | MDA | `did:web:did-server.inji-local.svc.cluster.local:mda` |

> **Atenção (ambiente local):** O did-server local só possui o DID do MGI (`/mgi/did.json`).
> Os DIDs de INCRA (`/incra/did.json`) e MDA (`/mda/did.json`) **não existem** no did-server local.
> Por isso, em ambiente local, as credenciais CCIR e CAF devem usar temporariamente o DID do MGI:
> `did:web:did-server.inji-local.svc.cluster.local:mgi`
> Em produção/homologação cada issuer terá seu próprio `did.json` com as chaves corretas.
