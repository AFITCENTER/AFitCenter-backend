#!/bin/sh
# =============================================================================
# Cria um banco e um usuário por microsserviço, cada usuário dono só do próprio banco.
#
# Roda apenas na PRIMEIRA inicialização do volume (docker-entrypoint-initdb.d). Para um
# serviço novo num volume já existente, crie o banco manualmente com os mesmos comandos.
# =============================================================================
set -eu

criar() {
  banco="$1"
  usuario="$2"
  senha="$3"
  echo "Criando banco ${banco} (usuário ${usuario})"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
       -v banco="$banco" -v usuario="$usuario" -v senha="$senha" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'usuario', :'senha') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'banco', :'usuario') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'banco') \gexec
SQL
}

criar "$AUTH_DB_NAME"          "$AUTH_DB_USER"          "$AUTH_DB_PASSWORD"
criar "$NUTRICIONISTA_DB_NAME" "$NUTRICIONISTA_DB_USER" "$NUTRICIONISTA_DB_PASSWORD"
criar "$PACIENTE_DB_NAME"      "$PACIENTE_DB_USER"      "$PACIENTE_DB_PASSWORD"
criar "$CONSULTA_DB_NAME"      "$CONSULTA_DB_USER"      "$CONSULTA_DB_PASSWORD"
criar "$AVALIACAO_DB_NAME"     "$AVALIACAO_DB_USER"     "$AVALIACAO_DB_PASSWORD"
criar "$DIETA_DB_NAME"         "$DIETA_DB_USER"         "$DIETA_DB_PASSWORD"
criar "$PLANOS_DB_NAME"        "$PLANOS_DB_USER"        "$PLANOS_DB_PASSWORD"
criar "$PAGAMENTOS_DB_NAME"    "$PAGAMENTOS_DB_USER"    "$PAGAMENTOS_DB_PASSWORD"
criar "$AUDITORIA_DB_NAME"     "$AUDITORIA_DB_USER"     "$AUDITORIA_DB_PASSWORD"
