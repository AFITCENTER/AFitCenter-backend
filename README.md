# AFitCenter Backend

Backend do AFitCenter em microsserviços Spring Boot: projeto Maven multi-módulo, descoberta de serviços via Eureka, entrada única pelo API Gateway, mensageria RabbitMQ e um banco PostgreSQL por serviço. Como os serviços conversam, como o isolamento entre nutricionistas é garantido e por que as fronteiras são estas: [docs/ARQUITETURA.md](../docs/ARQUITETURA.md).

## Estrutura

```text
backend/
├── afitcenter-parent/    # POM pai: versões (Boot, Cloud, SpringDoc, JWT) e configuração de build
├── afitcenter-core/      # Bibliotecas compartilhadas; não gera container
│   ├── core-base         #   papéis, headers internos, Problem Details, auditoria, CPF
│   ├── core-auth         #   JWT ES256: emissão (só ms-pessoa) e verificação
│   ├── core-web          #   auto-config: filtro de autenticação, tenant, segurança, erros, Feign, limite de tentativas
│   ├── core-mensageria   #   contratos dos eventos, filas com DLQ, outbox transacional
│   ├── core-paciente     #   réplica local do paciente para os serviços consumidores
│   ├── core-assinatura   #   réplica da assinatura e aplicação dos limites do plano
│   ├── core-auditoria    #   trilha de auditoria: registra cada requisição /v1/** na outbox
│   └── core-teste        #   chaves/tokens de teste e @TesteIntegracao (PostgreSQL embutido)
├── service-registry/     # Eureka Server (basic auth)
├── gateway/              # API Gateway
├── ms-pessoa/
├── ms-nutricionista/
├── ms-paciente/
├── ms-consulta/
├── ms-avaliacao/
├── ms-dieta/
├── ms-planos/
├── ms-pagamentos/
├── ms-auditoria/
├── docker/postgres/      # Criação de um banco e um usuário por serviço
├── scripts/              # gerar-env-local.sh
├── compose.yml           # Base (não publica portas)
├── compose.homolog.yml   # Local/homologação: publica Gateway, Eureka e painel do RabbitMQ
├── compose.prod.yml      # Produção: containers endurecidos, só o Gateway publicado
├── compose.dev.yml       # Opcional: PostgreSQL e RabbitMQ no host (rodar serviço pela IDE)
├── Dockerfile.service    # Build único de todos os serviços Java
└── .env.example
```

## Microsserviços

| Módulo | Prefixo no Gateway | Responsabilidade | Situação |
| --- | --- | --- | --- |
| `service-registry` | — | Eureka: registro e descoberta | pronto |
| `gateway` | — | Roteamento, JWT, bloqueio de `/internal/**`, CORS | pronto |
| `ms-pessoa` | `/ms-pessoa` | Quem acessa o sistema (nutricionistas, pacientes e administradores): dados pessoais, senha, papéis, termos, login e emissão do JWT; cadastro público do paciente; recuperação de senha e confirmação do e-mail | pronto |
| `ms-nutricionista` | `/ms-nutricionista` | Cadastro público (req. 7), perfil profissional, consultórios | pronto |
| `ms-paciente` | `/ms-paciente` | Pacientes, contatos, condições, medicamentos (req. 1); código de acesso e portal do paciente | pronto |
| `ms-consulta` | `/ms-consulta` | Agenda e consultas (req. 4), análises (req. 2) | pronto |
| `ms-avaliacao` | `/ms-avaliacao` | Avaliação corporal, metas, exames, evolução (req. 3 e 6) | esquema, catálogos e réplica; CRUD a implementar |
| `ms-dieta` | `/ms-dieta` | Alimentos e dietas (req. 5) | esquema, catálogos, réplica e busca de alimentos; CRUD a implementar |
| `ms-planos` | `/ms-planos` | Planos da plataforma, assinatura do nutricionista, faturas, renovação e inadimplência | pronto |
| `ms-pagamentos` | `/ms-pagamentos` | Cobranças (Pix, boleto, cartão) num provedor plugável, webhooks, vencimento e estorno | pronto: simulador (padrão) e Asaas, este ainda não validado no sandbox |
| `ms-auditoria` | `/ms-auditoria` | Trilha de auditoria de todos os serviços (LGPD) | pronto |

## Stack

Java 21 · Spring Boot 3.5 · Spring Cloud 2025.0 (Eureka, Gateway, OpenFeign) · PostgreSQL 17 · Flyway · RabbitMQ 4.1 · SpringDoc · Docker Compose v2. Testes de integração com PostgreSQL embutido (binários reais, sem Docker).

## Subindo tudo com Docker

Pré-requisitos: Docker Desktop (ou Engine) com Compose v2. Não é preciso instalar Java, Maven, PostgreSQL ou RabbitMQ.

```bash
cd backend
./scripts/gerar-env-local.sh
docker compose --env-file .env.local -f compose.yml -f compose.homolog.yml up -d --build
```

O script cria um `.env.local` com segredos aleatórios e um par de chaves ES256 novo, já com o perfil `local` (Swagger, logs detalhados e dados fictícios). A primeira subida demora: o build baixa as dependências Maven, que ficam em cache para os próximos.

| Endereço | O quê |
| --- | --- |
| http://localhost:8080 | Gateway (toda a API) |
| http://localhost:8761 | Painel do Eureka (usuário e senha em `EUREKA_USER`/`EUREKA_PASSWORD`) |
| http://localhost:15672 | Painel do RabbitMQ (`RABBITMQ_USER`/`RABBITMQ_PASSWORD`) |
| http://localhost:8080/ms-paciente/swagger-ui.html | Swagger de cada serviço (só no perfil `local`) |

No perfil `local` existem dois nutricionistas fictícios (tenants diferentes, para testar o isolamento) com pacientes, consultório e consultas de exemplo, um paciente com acesso ao portal (ligado à ficha dele na primeira nutricionista) e um administrador (papel `ADMIN`: planos, cobranças e auditoria de todos). A primeira nutricionista assina o plano Profissional; o segundo está no Gratuito. As credenciais estão no comentário de `ms-pessoa/src/main/resources/db/migration/local/R__dados_ficticios.sql`.

```bash
# login (nutricionista, paciente ou administrador) → token
curl -s -X POST http://localhost:8080/ms-pessoa/v1/auth/login -H "Content-Type: application/json" \
     -d '{"email":"<email do seed>","senha":"<senha do seed>"}'

# pacientes do nutricionista logado
curl -s http://localhost:8080/ms-paciente/v1/pacientes -H "Authorization: Bearer <accessToken>"

# planos à venda (público) e mudança para o Clínica (id 3): devolve a fatura e o Pix fictício
curl -s http://localhost:8080/ms-planos/v1/planos
curl -s -X POST http://localhost:8080/ms-planos/v1/assinatura/mudanca-plano -H "Authorization: Bearer <accessToken>" \
     -H "Content-Type: application/json" -d '{"planoId":3,"ciclo":"MENSAL","formaPagamento":"PIX"}'

# "pagar" a cobrança no simulador (perfis local e homolog): o plano muda em instantes
curl -s -X POST http://localhost:8080/ms-pagamentos/v1/simulador/cobrancas/<cobranca.id>/pagamento \
     -H "Authorization: Bearer <accessToken>"

# portal do paciente: o paciente cria a conta, o nutricionista gera o código da ficha e o paciente liga a ficha
curl -s -X POST http://localhost:8080/ms-pessoa/v1/cadastros/paciente -H "Content-Type: application/json" \
     -d '{"nome":"Maria","email":"maria@exemplo.local","senha":"senha-da-maria","aceiteTermos":true,"versaoTermos":"1.0"}'
curl -s -X POST http://localhost:8080/ms-paciente/v1/pacientes/<id da ficha>/acesso -H "Authorization: Bearer <token do nutricionista>"
curl -s -X POST http://localhost:8080/ms-paciente/v1/portal/vinculos -H "Authorization: Bearer <token da Maria>" \
     -H "Content-Type: application/json" -d '{"codigo":"<codigo>"}'
curl -s http://localhost:8080/ms-paciente/v1/portal/fichas -H "Authorization: Bearer <token da Maria>"

# esqueci a senha: no perfil local nenhum e-mail sai, o link (com o token) aparece no log do ms-pessoa
curl -s -X POST http://localhost:8080/ms-pessoa/v1/senha/esqueci -H "Content-Type: application/json" \
     -d '{"email":"maria@exemplo.local"}'
docker compose --env-file .env.local -f compose.yml -f compose.homolog.yml logs ms-pessoa | grep -A6 "redefinição de senha"
curl -s -X POST http://localhost:8080/ms-pessoa/v1/senha/redefinir -H "Content-Type: application/json" \
     -d '{"token":"<token do link>","novaSenha":"outra-senha-da-maria"}'

# quem acessou os dados de um paciente
curl -s "http://localhost:8080/ms-auditoria/v1/auditoria/registros?pacienteId=<id>" -H "Authorization: Bearer <accessToken>"
```

> O provedor de pagamento padrão é o **simulador**: nenhuma cobrança é real. O ms-pagamentos também fala com o **Asaas** (Pix, boleto e cartão pela fatura hospedada). Para usá-lo, preencha `AFIT_PAGAMENTOS_ASAAS_*` no `.env` (a chave entre aspas simples, porque começa com `$`), cadastre no painel do Asaas o webhook `<gateway>/ms-pagamentos/v1/webhooks/asaas` com o mesmo token e troque `AFIT_PAGAMENTOS_PROVEDOR=ASAAS`. A URL padrão é a do sandbox. Detalhes em [docs/ARQUITETURA.md](../docs/ARQUITETURA.md#assinatura-da-plataforma).

Para rodar um serviço pela IDE usando a infraestrutura do Docker, acrescente `-f compose.dev.yml` (publica PostgreSQL e RabbitMQ em `127.0.0.1`) e exporte as variáveis do `.env.local` com `<SERVICO>_DB_HOST=localhost`, `RABBITMQ_HOST=localhost` e o `EUREKA_URL` apontando para `localhost:8761`.

### Perfis

| Perfil | Uso |
| --- | --- |
| `local` | Desenvolvimento: dados fictícios (`db/migration/local`), Swagger, mensagens de erro detalhadas, CORS para `localhost:5173`, e-mails só no log do ms-pessoa (com o link) |
| `homolog` | Swagger ligado, sem dados fictícios, e-mails só no log (sem o link) até `AFIT_EMAIL_PROVEDOR=SMTP` |
| `prod` | Logs de segurança em WARN |
| (vazio) | Configuração segura padrão |

## Build e testes

```bash
cd backend
mvn verify
```

Compila todos os módulos e roda os testes. Os testes de integração (`@TesteIntegracao`) sobem cada serviço com PostgreSQL 17 embutido, aplicam as migrações Flyway e validam o mapeamento JPA (`ddl-auto=validate`). Eureka e RabbitMQ ficam desligados nos testes; os eventos (inclusive a trilha de auditoria) são conferidos na tabela `outbox_evento`. Os agendadores do ms-planos, ms-pagamentos e ms-auditoria também ficam desligados: os testes chamam os serviços com o instante que quiserem. Nenhum teste chama a internet: o Asaas é testado contra o `MockRestServiceServer` e contra um servidor HTTP local que imita a API dele (`AsaasFalso`).

Para compilar só um serviço (com o core): `mvn -pl ms-paciente -am verify`.

## Variáveis de ambiente

Modelo comentado: [.env.example](.env.example). O arquivo passado em `--env-file` serve só para a interpolação do Compose: cada container recebe apenas as variáveis que usa. Só o `ms-pessoa` recebe a chave privada do JWT e as credenciais SMTP (`AFIT_SMTP_*`; sem elas, fora dos perfis `local` e `homolog`, ele não sobe), só o `ms-pagamentos` recebe a chave de API do provedor de pagamento e os segredos dos webhooks, o Gateway não recebe nenhum segredo e cada serviço só conhece a senha do próprio banco.

> Nunca versione `.env.local`, `.env.homolog`, `.env.prod` ou qualquer arquivo com segredos.

O script `docker/postgres/01-criar-bancos.sh` cria os bancos só na **primeira** inicialização do volume. Ao adicionar um serviço num ambiente que já existe, crie o banco dele manualmente (é o caso de `afit_planos`, `afit_pagamentos` e `afit_auditoria` num volume criado antes deles), ou recrie o volume num ambiente local.

## Adicionando um microsserviço

1. Crie o módulo com `afitcenter-parent` como pai e adicione-o ao `pom.xml` da raiz.
2. Dependa de `core-web` e de `core-auditoria` (com `afitcenter.outbox.habilitado=true` e a tabela `outbox_evento`); de `core-paciente` se referenciar pacientes e de `core-assinatura` se aplicar limites do plano.
3. Declare o `SecurityFilterChain` com `SegurancaPadrao.configurar(...)` e use `UsuarioAutenticado.nutricionistaId()` em todo acesso a dados.
4. Crie as migrações em `src/main/resources/db/migration/common` (e `local` para dados fictícios).
5. Adicione o banco em `docker/postgres/01-criar-bancos.sh`, o serviço no `compose.yml`/`compose.prod.yml` e a rota no `GatewayConfig`.
6. Escreva os testes com `@TesteIntegracao`, incluindo o caso "outro nutricionista não acessa".
