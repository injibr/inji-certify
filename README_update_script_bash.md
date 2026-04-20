# Script de Atualização (Bash)

## Visão Geral

Este script Bash (`update_script.sh`) automatiza o processo de atualização do código do projeto MIMOTO a partir do repositório GitHub, aplicando uma tag específica e enviando as alterações para o SCM (Source Control Management). É a versão Unix/Linux do script PowerShell equivalente.

## Funcionalidades

- Baixa uma versão específica (tag) do código do repositório GitHub
- Cria uma tag Git local com a mesma versão
- Realiza commit das alterações
- Envia as alterações para o repositório SCM
- Gera relatórios detalhados de execução
- Protege credenciais LDAP, excluindo o script dos commits
- Organiza os relatórios em uma pasta dedicada
- Solicita credenciais interativamente quando não configuradas

## Pré-requisitos

- Sistema Unix/Linux ou macOS
- Bash 4.0 ou superior
- Git instalado e configurado
- Acesso à rede dataprev-internet (não utilizar dataprev-nac nem dataprev-corporativa)
- Credenciais LDAP válidas

## Configuração Inicial

### Opção 1: Credenciais no Script (Recomendado para uso frequente)

Edite o script e substitua os valores das variáveis:
```bash
LDAP_USER="seu_usuario_ldap"    # Substitua pelo seu usuário
LDAP_PASSWORD="sua_senha_ldap"  # Substitua pela sua senha
```

### Opção 2: Credenciais Interativas (Recomendado para uso esporádico)

Deixe as credenciais com os valores padrão:
```bash
LDAP_USER="SEU_USUARIO_LDAP"    # Mantenha assim
LDAP_PASSWORD="SUA_SENHA_LDAP"  # Mantenha assim
```

### Configuração da Tag

Configure a tag desejada no início do script:
```bash
TAG_PREDEFINIDA="v0.17.1"       # Defina a tag desejada
USAR_TAG_PREDEFINIDA=true       # Use true para usar a tag predefinida
```

## Como Utilizar

### 1. Preparação
```bash
# Dar permissão de execução ao script
chmod +x update_script.sh

# Certificar-se de estar na rede dataprev-internet
# Configurar credenciais (se escolheu a Opção 1)
```

### 2. Execução
```bash
# Navegar até a pasta do projeto MIMOTO
cd /caminho/para/mimoto

# Executar o script
./update_script.sh
```

### 3. Interação

**Se credenciais estão configuradas no script:**
- O script solicitará confirmação da tag predefinida
- Você pode aceitar ou inserir uma nova tag

**Se credenciais não estão configuradas:**
```
Credenciais LDAP não configuradas no script.
Digite seu usuário LDAP: [digite aqui]
Senha LDAP não configurada no script.
Digite sua senha LDAP: [senha oculta]
```

### 4. Validação de Tag
- O script validará o formato da tag (recomendado: vX.Y.Z)
- Alertará sobre tags em formato não padrão

### 5. Acompanhamento
- O progresso será exibido no terminal
- Um relatório detalhado será gerado na pasta "relatorios"

## Cuidados Importantes

### 1. Segurança de Credenciais
- **NUNCA** compartilhe o script com suas credenciais configuradas
- O script é automaticamente excluído dos commits
- Use credenciais interativas para maior segurança em ambientes compartilhados

### 2. Permissões de Arquivo
```bash
# Verificar permissões do script
ls -la update_script.sh

# Dar permissão de execução se necessário
chmod +x update_script.sh
```

### 3. Formato de Tag
- Recomenda-se usar o formato padrão: `vX.Y.Z` (exemplo: v1.2.3)
- O script alertará sobre tags em formato não padrão

### 4. Rede
- Use apenas a rede dataprev-internet
- Não utilize dataprev-nac nem dataprev-corporativa

### 5. Proxy
- Se necessário, descomente e configure as linhas de proxy na função `configurar_proxy_git`

## Estrutura de Arquivos

```
projeto/
├── update_script.sh          # Script principal (excluído dos commits)
├── relatorios/              # Pasta de relatórios (excluída dos commits)
│   └── relatorio_atualizacao_YYYYMMDD_HHMMSS.log
├── .gitignore              # Criado automaticamente
└── [outros arquivos do projeto]
```

## Resolução de Problemas

### Problemas de Permissão
```bash
# Erro: Permission denied
chmod +x update_script.sh
```

### Problemas de Conexão
- Verificar se está na rede dataprev-internet
- Testar conectividade: `ping github.com`
- Verificar configurações de proxy

### Problemas de Autenticação
- Verificar credenciais LDAP
- Testar acesso manual ao repositório

### Tags Inexistentes
- Verificar se a tag existe no GitHub
- Listar tags disponíveis: `git ls-remote --tags origin`

## Diferenças do Script PowerShell

| Aspecto | PowerShell | Bash |
|---------|------------|------|
| Sistema | Windows | Unix/Linux/macOS |
| Extensão | `.ps1` | `.sh` |
| Permissões | Política de execução | `chmod +x` |
| Variáveis | `$variavel` | `$variavel` |
| Funções | `Function-Name` | `function_name` |
| Condicionais | `if (condition)` | `if [ condition ]` |

## Logs e Relatórios

### Localização
- Pasta: `./relatorios/`
- Nome: `relatorio_atualizacao_YYYYMMDD_HHMMSS.log`

### Conteúdo
- Cabeçalho com informações da execução
- Log detalhado de todas as operações
- Categorização de mensagens: `[ERRO]`, `[AVISO]`, `[SUCESSO]`
- Timestamp de cada operação

### Exemplo de Entrada de Log
```
[2024-01-15 14:30:25] Iniciando execução do script de atualização
[2024-01-15 14:30:26] Credenciais LDAP carregadas (usuário: usuario.exemplo)
[2024-01-15 14:30:27] Tag selecionada: v0.17.1 (versão: 0.17.1)
```

## Comandos Úteis

```bash
# Verificar status do Git
git status

# Listar tags remotas
git ls-remote --tags origin

# Verificar conectividade
ping github.com

# Ver últimas execuções
ls -la relatorios/

# Visualizar último relatório
tail -f relatorios/relatorio_atualizacao_*.log
```

---

Para mais informações ou suporte, entre em contato com a equipe de desenvolvimento.
