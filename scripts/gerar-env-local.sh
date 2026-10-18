#!/usr/bin/env bash
# =============================================================================
# Gera um arquivo de ambiente para DESENVOLVIMENTO com segredos aleatórios e um par de
# chaves ES256 novo. Funciona no Git Bash (Windows), Linux e macOS; precisa de openssl.
#
#   ./scripts/gerar-env-local.sh              # cria .env.local
#   ./scripts/gerar-env-local.sh outro.env    # cria outro arquivo
#
# Não sobrescreve um arquivo existente.
# =============================================================================
set -euo pipefail

destino="${1:-.env.local}"
if [ -e "$destino" ]; then
  echo "O arquivo $destino já existe; nada foi alterado." >&2
  exit 1
fi
command -v openssl >/dev/null || { echo "openssl não encontrado." >&2; exit 1; }

hex() { openssl rand -hex "$1"; }
base64_linha() { openssl base64 -A; }

temp="$(mktemp -d)"
trap 'rm -rf "$temp"' EXIT
openssl ecparam -name prime256v1 -genkey -noout -out "$temp/jwt.pem" 2>/dev/null
privada="$(openssl pkcs8 -topk8 -nocrypt -in "$temp/jwt.pem" -outform DER | base64_linha)"
publica="$(openssl ec -in "$temp/jwt.pem" -pubout -outform DER 2>/dev/null | base64_linha)"

eureka_user="$(hex 12)"
eureka_password="$(hex 24)"

umask 077
cat > "$destino" <<EOF
# Gerado por scripts/gerar-env-local.sh em $(date -u +%Y-%m-%dT%H:%M:%SZ). Somente desenvolvimento.
SPRING_PROFILES_ACTIVE=local

GATEWAY_PORT=8080
EUREKA_PORT=8761
RABBITMQ_MANAGEMENT_PORT=15672
POSTGRES_HOST_PORT=5432
RABBITMQ_PORT=5672

EUREKA_USER=$eureka_user
EUREKA_PASSWORD=$eureka_password
EUREKA_URL=http://$eureka_user:$eureka_password@localhost:8761/eureka/

RABBITMQ_USER=afit
RABBITMQ_PASSWORD=$(hex 16)

POSTGRES_ADMIN_USER=afit_admin
POSTGRES_ADMIN_PASSWORD=$(hex 16)
AUTH_DB_NAME=afit_auth
AUTH_DB_USER=afit_auth
AUTH_DB_PASSWORD=$(hex 16)
NUTRICIONISTA_DB_NAME=afit_nutricionista
NUTRICIONISTA_DB_USER=afit_nutricionista
NUTRICIONISTA_DB_PASSWORD=$(hex 16)
PACIENTE_DB_NAME=afit_paciente
PACIENTE_DB_USER=afit_paciente
PACIENTE_DB_PASSWORD=$(hex 16)
CONSULTA_DB_NAME=afit_consulta
CONSULTA_DB_USER=afit_consulta
CONSULTA_DB_PASSWORD=$(hex 16)
AVALIACAO_DB_NAME=afit_avaliacao
AVALIACAO_DB_USER=afit_avaliacao
AVALIACAO_DB_PASSWORD=$(hex 16)
DIETA_DB_NAME=afit_dieta
DIETA_DB_USER=afit_dieta
DIETA_DB_PASSWORD=$(hex 16)
PLANOS_DB_NAME=afit_planos
PLANOS_DB_USER=afit_planos
PLANOS_DB_PASSWORD=$(hex 16)
PAGAMENTOS_DB_NAME=afit_pagamentos
PAGAMENTOS_DB_USER=afit_pagamentos
PAGAMENTOS_DB_PASSWORD=$(hex 16)
AUDITORIA_DB_NAME=afit_auditoria
AUDITORIA_DB_USER=afit_auditoria
AUDITORIA_DB_PASSWORD=$(hex 16)

AFIT_JWT_ISSUER=afitcenter-local
AFIT_JWT_PRIVATE_KEY=$privada
AFIT_JWT_PUBLIC_KEY=$publica
AFIT_TOKEN_VALIDADE_MINUTOS=60
AFIT_INTERNAL_TOKEN=$(hex 32)
AFIT_LOGIN_MAX_FALHAS=5
AFIT_LOGIN_BLOQUEIO_MINUTOS=15

AFIT_PAGAMENTOS_PROVEDOR=SIMULADO
AFIT_PAGAMENTOS_WEBHOOK_SEGREDO=$(hex 32)
AUDITORIA_RETENCAO_DIAS=1825
EOF

echo "Arquivo $destino criado."
echo "Suba o ambiente com:"
echo "  docker compose --env-file $destino -f compose.yml -f compose.homolog.yml up -d --build"
