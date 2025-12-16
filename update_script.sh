#!/bin/bash

# Configurações do script
# Parametros principais
TAG_PREDEFINIDA="v0.10.2"  # Defina a tag diretamente aqui
COMPONENTE=inji-certify    #componente inji a ser tratado no script

# !!! IMPORTANTE O primeiro push por padrão tem o MANAGE_GITIGNORE=false para baixar o git_ignore sem conflitos do repositorio original github. 
MANAGE_GITIGNORE=false     #Caso TRUE, adiciona o update_script e relatorios na lista do gitignore.
# A execução via terminal solicita os dados LDAP, caso queira fixar suas credenciais nas variaveis abaixo,
# desabilite a solicitação via terminal com a variavel INPUT_LDAP_CREDENTIAL=false e altere o MANAGE_GITIGNORE=true a partir da 2 atualização 
# para que o script não seja comitado.

INPUT_LDAP_CREDENTIAL=true
LDAP_USER="USER"  # Substitua pelo seu usuário
LDAP_PASSWORD="PASS"  # Substitua pela sua senha

#####f

USAR_TAG_PREDEFINIDA=true   # Defina como true para usar a tag predefinida ou false para solicitar ao usuário
TAG=""
VERSION=""
ID_COMMIT_PULL=""
REPOSITORIO_EXTERNO="https://github.com/injibr/$COMPONENTE/"
BRANCH=$(git rev-parse --abbrev-ref HEAD)

# Configuração do relatório
PASTA_RELATORIOS="$(pwd)/relatorios"
DATA_HORA=$(date +"%Y%m%d_%H%M%S")
NOME_RELATORIO="relatorio_atualizacao_${DATA_HORA}.log"
CAMINHO_RELATORIO="${PASTA_RELATORIOS}/${NOME_RELATORIO}"

# Nome do arquivo de script atual para excluir do commit
NOME_SCRIPT_ATUAL=$(basename "$0")

# Usar a rede dataprev-internet. Não utilizar dataprev-nac nem dataprev-corporativa

function escrever_mensagem() {
    echo "**************************************************************"
    echo "$1"
    echo ""
}

function escrever_log() {
    local mensagem="$1"
    local timestamp=$(date "+%Y-%m-%d %H:%M:%S")
    local entrada_log="[$timestamp] $mensagem"
    
    # Garantir que a pasta de relatórios exista
    if [ ! -d "$PASTA_RELATORIOS" ]; then
        mkdir -p "$PASTA_RELATORIOS"
    fi
    
    echo "$entrada_log" >> "$CAMINHO_RELATORIO"
}

function testar_repositorio_github() {
    escrever_log "Verificando repositório remoto do GitHub..."
    if git remote | grep -q "github"; then
        escrever_mensagem "Repositório remoto já existe."
    else
        git remote add github "$REPOSITORIO_EXTERNO"
        escrever_mensagem "Repositório remoto não existe localmente, criando repositório."
    fi
    
    # Testar conexão com GitHub
    escrever_mensagem "Testando conexão com GitHub..."
    resultado_teste=$(git ls-remote --heads github 2>&1)
    if [ $? -eq 0 ]; then
        escrever_mensagem "Conexão com GitHub bem-sucedida."
        escrever_log "Conexão com GitHub estabelecida com sucesso"
    else
        detalhes_erro="Código de erro: $?. Detalhes: $resultado_teste"
        escrever_mensagem "Falha na conexão com o GitHub. $detalhes_erro"
        escrever_mensagem "Tentando soluções alternativas..."
        escrever_log "[ERRO] Falha na conexão com GitHub. $detalhes_erro"
        
        # Tentar com formato de URL diferente
        escrever_log "Tentando URL alternativa: https://github.com/injibr/$COMPONENTE.git"
        git remote set-url github "https://github.com/injibr/$COMPONENTE.git"
        
        # Verificar se a alteração resolveu o problema
        resultado_teste=$(git ls-remote --heads github 2>&1)
        if [ $? -eq 0 ]; then
            escrever_mensagem "Conexão estabelecida com URL alternativa."
            escrever_log "Conexão estabelecida com URL alternativa."
        else
            escrever_mensagem "Falha persistente na conexão. Verifique sua rede e configurações de proxy."
            escrever_log "[ERRO CRÍTICO] Falha persistente na conexão com GitHub após tentativas alternativas."
            exit 1
        fi
    fi
}

function ler_entrada_tag() {
    if [ "$USAR_TAG_PREDEFINIDA" = true ]; then
        TAG="$TAG_PREDEFINIDA"
        escrever_mensagem "Tag predefinida no script: $TAG"
        
        # Solicitar confirmação do usuário
        read -p "Confirma o uso desta tag? (S/N): " confirmacao
        if [ "$confirmacao" != "S" ] && [ "$confirmacao" != "s" ]; then
            read -p "Digite a tag desejada para a nova versão: " TAG
            if [ -z "$TAG" ]; then
                mensagem_erro="Tag inválida, operação cancelada."
                escrever_mensagem "$mensagem_erro"
                escrever_log "[ERRO] $mensagem_erro"
                exit 1
            fi
        fi
    else
        read -p "Digite a tag para a nova versão: " TAG
        if [ -z "$TAG" ]; then
            mensagem_erro="Tag inválida, operação cancelada."
            escrever_mensagem "$mensagem_erro"
            escrever_log "[ERRO] $mensagem_erro"
            exit 1
        fi
    fi
    
    # Validar formato da tag
    if ! echo "$TAG" | grep -qE '^v[0-9]+\.[0-9]+\.[0-9]+$'; then
        escrever_mensagem "AVISO: A tag '$TAG' não segue o formato recomendado (vX.Y.Z)."
        read -p "Deseja continuar mesmo assim? (S/N): " confirmar_formato
        if [ "$confirmar_formato" != "S" ] && [ "$confirmar_formato" != "s" ]; then
            escrever_log "[AVISO] Operação cancelada pelo usuário devido ao formato da tag."
            exit 0
        fi
    fi
    
    VERSION=$(echo "$TAG" | sed 's/v//')
    escrever_log "Tag selecionada: $TAG (versão: $VERSION)"
    escrever_mensagem "Versão confirmada: $TAG"
}

function configurar_credenciais_ldap() {
    escrever_log "Configurando credenciais LDAP..."
    
    # Verificar se as credenciais precisam ser solicitadas
    if [ "$LDAP_USER" = "SEU_USUARIO_LDAP" ] || [ -z "$LDAP_USER" ]; then
        escrever_mensagem "Credenciais LDAP não configuradas no script."
        read -p "Digite seu usuário LDAP: " LDAP_USER
        if [ -z "$LDAP_USER" ]; then
            mensagem_erro="Usuário LDAP não pode estar vazio."
            escrever_mensagem "$mensagem_erro"
            escrever_log "[ERRO] $mensagem_erro"
            exit 1
        fi
    fi
    
    if [ "$LDAP_PASSWORD" = "SUA_SENHA_LDAP" ] || [ -z "$LDAP_PASSWORD" ]; then
        escrever_mensagem "Senha LDAP não configurada no script."
        read -s -p "Digite sua senha LDAP: " LDAP_PASSWORD
        echo  # Nova linha após input oculto
        if [ -z "$LDAP_PASSWORD" ]; then
            mensagem_erro="Senha LDAP não pode estar vazia."
            escrever_mensagem "$mensagem_erro"
            escrever_log "[ERRO] $mensagem_erro"
            exit 1
        fi
    fi
    
    escrever_mensagem "Credenciais LDAP configuradas (usuário: $LDAP_USER)."
    escrever_log "Credenciais LDAP carregadas (usuário: $LDAP_USER)"
    
    # Configurar proxy
    # set_proxy
}

function set_proxy() {
    export http_proxy="http://${LDAP_USER}:${LDAP_PASSWORD}@10.70.124.16:3128" && export https_proxy="http://${LDAP_USER}:${LDAP_PASSWORD}@10.70.124.16:3128"
    
}

function unset_proxy() {
    export http_proxy= && export https_proxy=
   
}

function baixar_codigo() {
    testar_repositorio_github
    escrever_mensagem "Atualizando código para nova versão $TAG..."
    escrever_log "Iniciando download do código versão $TAG"
    
    #git fetch github tag $TAG
    # Capturar saída completa do comando git pull
    #resultado_pull=$(git merge --allow-unrelated-histories -X theirs tags/$TAG 2>&1)
   
    git fetch github
    git merge --allow-unrelated-histories -X theirs github/$TAG



    if [ $? -ne 0 ]; then
        mensagem_erro="Falha ao baixar o código da versão $TAG."
        detalhes_erro="Código de erro: $?. Detalhes: $resultado_pull"
        
        # Analisar o tipo de erro para fornecer mensagens mais específicas
        if echo "$resultado_pull" | grep -q "couldn't find remote ref $TAG"; then
            mensagem_erro="A tag '$TAG' não foi encontrada no repositório remoto."
            detalhes_erro="$detalhes_erro. Verifique se a tag existe no GitHub."
        elif echo "$resultado_pull" | grep -q "Authentication failed"; then
            mensagem_erro="Falha de autenticação ao acessar o repositório."
            detalhes_erro="$detalhes_erro. Verifique suas credenciais LDAP e configurações de proxy."
        elif echo "$resultado_pull" | grep -q "Connection timed out"; then
            mensagem_erro="Tempo de conexão esgotado ao tentar acessar o repositório."
            detalhes_erro="$detalhes_erro. Verifique sua conexão de rede e configurações de firewall."
        fi
        
        escrever_mensagem "$mensagem_erro $detalhes_erro"
        escrever_log "[ERRO] $mensagem_erro"
        escrever_log "[DETALHE] $detalhes_erro"
        exit 1
    else
        ID_COMMIT_PULL=$(git rev-parse HEAD)
        mensagem_sucesso="Atualização do código bem-sucedida."
        escrever_mensagem "$mensagem_sucesso"
        escrever_log "$mensagem_sucesso (Commit: $ID_COMMIT_PULL)"
    fi
}

function nova_tag_git() {
    # Renomear tags para o padrão quando ok 
    if [ -n "$ID_COMMIT_PULL" ]; then
        escrever_mensagem "Criando nova tag $TAG ..."
        escrever_log "Criando tag anotada: $TAG"
        mensagem_commit=$(git show --pretty=format:%B -s "$ID_COMMIT_PULL")
        git tag -a "$TAG" -m "$mensagem_commit"
    else
        escrever_mensagem "Criando nova tag $TAG ..."
        escrever_log "Criando tag simples: $TAG"
        git tag "$TAG"
    fi
}

function enviar_codigo() {
    escrever_mensagem "Fazendo commit do código ..."
    escrever_log "Iniciando commit dos arquivos"
    
    # Adicionar todos os arquivos ao stage
    git add .
    
    # Remover o arquivo de script atual do stage para não incluir no commit
    git reset HEAD "$NOME_SCRIPT_ATUAL" 2>/dev/null || true
    escrever_log "Arquivo de script excluído do commit: $NOME_SCRIPT_ATUAL"
    
    # Ignorar a pasta de relatórios
    git reset HEAD "relatorios/*" 2>/dev/null || true
    escrever_log "Pasta de relatórios excluída do commit"
    
    # Realizar o commit
    git commit -m "Atualizando arquivos para versão $TAG"
    #Tag ja baixada junto com o codigo
    #nova_tag_git
}

function enviar_scm() {
    unset_proxy
    escrever_mensagem "Enviando alterações para SCM ..."
    escrever_log "Iniciando push para SCM"
    # Alterar nome da branch para a correta
    resultado_push=$(git push "https://$LDAP_USER:$LDAP_PASSWORD@www-scm.prevnet/inji/$COMPONENTE" $BRANCH --tags 2>&1)
    if [ $? -ne 0 ]; then
        mensagem_erro="Falha ao enviar alterações para o SCM."
        detalhes_erro="Código de erro: $?. Detalhes: $resultado_push"
        
        # Analisar o tipo de erro para fornecer mensagens mais específicas
        if echo "$resultado_push" | grep -q "Authentication failed"; then
            mensagem_erro="Falha de autenticação ao acessar o SCM."
            detalhes_erro="$detalhes_erro. Verifique suas credenciais LDAP."
        elif echo "$resultado_push" | grep -q "Connection timed out"; then
            mensagem_erro="Tempo de conexão esgotado ao tentar acessar o SCM."
            detalhes_erro="$detalhes_erro. Verifique sua conexão de rede e configurações de firewall."
        elif echo "$resultado_push" | grep -q "rejected"; then
            mensagem_erro="Push rejeitado pelo servidor SCM."
            detalhes_erro="$detalhes_erro. Pode haver conflitos ou permissões insuficientes."
        fi
        
        escrever_mensagem "$mensagem_erro $detalhes_erro"
        escrever_log "[ERRO] $mensagem_erro"
        escrever_log "[DETALHE] $detalhes_erro"
        exit 1
    else
        mensagem_sucesso="Código atualizado com sucesso no SCM!"
        escrever_mensagem "$mensagem_sucesso"
        escrever_log "[SUCESSO] $mensagem_sucesso"
    fi
}

function enviar_versao_para_ic() {
    # atualizar_arquivos_propriedades
    baixar_codigo
    enviar_codigo
    #enviar_scm
}

function atualizar_arquivos_propriedades() {
    # Tomar cuidado com inscrições no Dockerfile.
    escrever_log "Função de atualização de arquivos de propriedades chamada"
}

function inicializar_relatorio() {
    # Garantir que a pasta de relatórios exista
    if [ ! -d "$PASTA_RELATORIOS" ]; then
        mkdir -p "$PASTA_RELATORIOS"
    fi
    
    cat > "$CAMINHO_RELATORIO" << EOF
=================================================================
RELATÓRIO DE ATUALIZAÇÃO DO $COMPONENTE
=================================================================
Versão: $TAG
Data/Hora de Execução: $(date '+%d/%m/%Y %H:%M:%S')
Usuário: $USER
Diretório: $(pwd)
Arquivo de Script: $NOME_SCRIPT_ATUAL
=================================================================

EOF
    escrever_mensagem "Relatório iniciado: $CAMINHO_RELATORIO"
}

function finalizar_relatorio() {
    cat >> "$CAMINHO_RELATORIO" << EOF

=================================================================
EXECUÇÃO FINALIZADA EM: $(date '+%d/%m/%Y %H:%M:%S')
=================================================================
EOF
    escrever_mensagem "Relatório finalizado: $NOME_RELATORIO"
}

function criar_gitignore() {
    local gitignore_path="$(pwd)/.gitignore"
    local conteudo_gitignore="# Ignorar script de atualização (contém credenciais)
$NOME_SCRIPT_ATUAL

# Ignorar pasta de relatórios
/relatorios/"
    
    if [ ! -f "$gitignore_path" ]; then
        echo "$conteudo_gitignore" > "$gitignore_path"
        escrever_mensagem "Arquivo .gitignore criado para proteger credenciais e relatórios"
    else
        # Verificar se as entradas já existem no .gitignore
        local atualizar_gitignore=false
        
        if ! grep -q "$NOME_SCRIPT_ATUAL" "$gitignore_path"; then
            echo -e "\n# Ignorar script de atualização (contém credenciais)\n$NOME_SCRIPT_ATUAL" >> "$gitignore_path"
            atualizar_gitignore=true
        fi
        
        if ! grep -q "/relatorios/" "$gitignore_path"; then
            echo -e "\n# Ignorar pasta de relatórios\n/relatorios/" >> "$gitignore_path"
            atualizar_gitignore=true
        fi
        
        if [ "$atualizar_gitignore" = true ]; then
            escrever_mensagem "Arquivo .gitignore atualizado para proteger credenciais e relatórios"
        fi
    fi
}


function solicitar_ldap() {
    echo "Para configuração de proxy e push no SCM, por favor entre com suas credenciais LDAP"
    echo -n "Usuario LDAP: " 
	read usuario
	LDAP_USER=$usuario
	echo -n "Senha LDAP: " 
    read -s senha
	echo ""
	LDAP_PASSWORD=$senha

}

function principal() {
    # Criar pasta de relatórios se não existir
    if [ ! -d "$PASTA_RELATORIOS" ]; then
        mkdir -p "$PASTA_RELATORIOS"
        escrever_mensagem "Pasta de relatórios criada: $PASTA_RELATORIOS"
    fi
    
    if [ "$INPUT_LDAP_CREDENTIAL" = "true" ]; then
        solicitar_ldap
    fi

    
    
    if [ "$MANAGE_GITIGNORE" = "true" ]; then
        criar_gitignore
    fi
    inicializar_relatorio
    escrever_log "Iniciando execução do script de atualização"
    
    configurar_credenciais_ldap
    ler_entrada_tag
    enviar_versao_para_ic
    
    escrever_log "[SUCESSO] Script executado com sucesso"
    escrever_mensagem "Atualização concluída com sucesso!"
    escrever_mensagem "Relatório salvo em: $CAMINHO_RELATORIO"
    
    finalizar_relatorio
}

# Iniciar execução do script
principal