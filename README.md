# 🔍 Discord Search Bot

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![Oracle GraalVM](https://img.shields.io/badge/Oracle%20GraalVM%20(GFTC)-JDK%2025-red?logo=graalvm)](https://www.graalvm.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![SearXNG](https://img.shields.io/badge/SearXNG-Metasearch-blue)](https://searxng.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-blue?logo=postgresql)](https://www.postgresql.org/)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka%20%2F%20Redpanda-Event--Driven-black?logo=apachekafka)](https://kafka.apache.org/)
[![Resilience4j](https://img.shields.io/badge/Resilience4j-2.3.0-yellow)](https://resilience4j.readme.io/)
[![Garage S3](https://img.shields.io/badge/Garage-S3%20Compatible-blue)](https://garagehq.deuxfleurs.fr/)

O **Discord Search Bot** é um microsserviço corporativo de pesquisa na web e indexação multimídia orientado a eventos para comunidades no Discord. Desenvolvido sobre o **Java 25 com Oracle GraalVM (GFTC)** em compilação nativa (**Native Image**) e **Spring Boot 4**, ele adota os princípios de **Arquitetura Hexagonal (Ports and Adapters)**, operando de forma reativa e desacoplada via **Kafka / Redpanda**.

O bot integra o motor de metabusca privado **SearXNG**, permitindo aos usuários consultar a web geral, imagens, arquivos/documentos e notícias. Ele oferece download seguro com proteção anti-SSRF para upload de imagens em armazenamento **S3 (Garage)**, paginação dinâmica por botões interativos do Discord (Anterior, Próximo, Aleatório e Link direto), e trilha completa de auditoria persistida em **PostgreSQL**.

---

## 🏛️ Blueprint da Arquitetura

O bot opera de forma desacoplada do Discord: o **discord-gateway-api** consome os eventos da API do Discord e os publica em tópicos Kafka. O `discord-search-bot` consome os comandos `/pesquisar`, `.pesquisar` e as interações de botões, processa as buscas no SearXNG e publica as respostas prontas com embeds e botões de volta ao Gateway.

```mermaid
graph TD
    subgraph Discord Client
        U["Usuários Discord"]
    end

    subgraph "Gateway Layer"
        DGW["discord-gateway-api"]
    end

    subgraph "Kafka Event Broker (Redpanda)"
        TOP_CMD["discord.events.interaction.command"]
        TOP_MSG["discord.events.message.command"]
        TOP_BTN["discord.events.interaction.button"]
        TOP_RESP["discord.gateway.responses"]
        TOP_REG["discord.gateway.commands"]
    end

    subgraph "Core: discord-search-bot (Java 25 + Oracle GraalVM)"
        subgraph "Inbound Adapters"
            IEC["InboundEventConsumer"]
            IEM["InboundEventMapper"]
        end

        subgraph "Application Core (Hexagonal)"
            APP_SVC["SearchApplicationService"]
            USE_SEARCH["ExecuteSearchUseCase"]
            USE_NAV["NavigateSearchUseCase"]
            LAYOUT["SearchLayoutBuilder"]
            PARSER["CommandTextParser"]
        end

        subgraph "Outbound Adapters"
            SX_CLIENT["SearxngClient (RestClient + Resilience4j)"]
            S3_STORE["S3ImageStore & SafeImageDownloader"]
            JDBC_AUDIT["JdbcAuditLogAdapter (AuditLogPort)"]
            JDBC_SESS["JdbcSearchSessionAdapter (SearchSessionPort)"]
            RESP_PUB["GatewayResponsePublisher (ReplyPublisherPort)"]
            CMD_REG["CommandRegistrar (ApplicationRunner)"]
        end
    end

    subgraph "Infraestrutura Externa"
        SEARX["SearXNG Cluster (http://searxng.searxng.svc.cluster.local:8080)"]
        GARAGE["Garage S3 Object Storage"]
        POSTGRES["PostgreSQL (search_bot schema)"]
        CONSUL["Consul Server (KV Config)"]
        VAULT["HashiCorp Vault (Secrets Injector)"]
    end

    U --> DGW
    DGW --> TOP_CMD
    DGW --> TOP_MSG
    DGW --> TOP_BTN

    TOP_CMD --> IEC
    TOP_MSG --> IEC
    TOP_BTN --> IEC

    IEC --> IEM
    IEM --> USE_SEARCH
    IEM --> USE_NAV

    USE_SEARCH --> APP_SVC
    USE_NAV --> APP_SVC

    APP_SVC --> LAYOUT
    APP_SVC --> SX_CLIENT
    APP_SVC --> S3_STORE
    APP_SVC --> JDBC_SESS
    APP_SVC --> JDBC_AUDIT
    APP_SVC --> RESP_PUB

    SX_CLIENT --> SEARX
    S3_STORE --> GARAGE
    JDBC_AUDIT --> POSTGRES
    JDBC_SESS --> POSTGRES
    RESP_PUB --> TOP_RESP
    CMD_REG --> TOP_REG

    TOP_RESP --> DGW
    TOP_REG --> DGW
    DGW --> U
```

---

## ⚡ Fluxo de Execução e Paginação Interativa

```mermaid
sequenceDiagram
    autonumber
    actor Usuario as Usuário Discord
    participant Gateway as discord-gateway-api
    participant Kafka as Kafka / Redpanda
    participant Bot as discord-search-bot
    participant SearXNG as SearXNG
    participant Garage as Garage S3
    participant DB as PostgreSQL (search_bot)

    Usuario->>Gateway: Dispara /pesquisar termo: "linux" tipo: "imagem"
    Gateway->>Kafka: Publica discord.events.interaction.command
    Kafka->>Bot: Consome InboundEvent
    Bot->>SearXNG: Consulta /search?q=linux&categories=images&format=json
    SearXNG-->>Bot: Retorna resultados de imagens
    Bot->>Garage: Faz download seguro anti-SSRF e upload da 1ª imagem
    Garage-->>Bot: Retorna URL no bucket S3
    Bot->>DB: Salva SearchSession com resultados em cache (TTL 1h)
    Bot->>DB: Registra log de auditoria com payloads e tempo
    Bot->>Kafka: Publica GatewayResponse (Embed + Botões de Navegação)
    Kafka->>Gateway: Despacha resposta para a API do Discord
    Gateway-->>Usuario: Exibe Embed com Imagem e Botões [⬅️ Anterior] [🎲 Aleatório] [Próximo ➡️] [🌐 Acessar]

    Usuario->>Gateway: Clica no botão [Próximo ➡️]
    Gateway->>Kafka: Publica discord.events.interaction.button
    Kafka->>Bot: Consome clique de botão (componentId: search:next:sessionId)
    Bot->>DB: Recupera SearchSession e avança o índice
    Bot->>Garage: Baixa e armazena imagem da 2ª posição no S3
    Bot->>Kafka: Publica UPDATE_MESSAGE com o resultado 2
    Kafka->>Gateway: Edita a mensagem existente no Discord
    Gateway-->>Usuario: Mensagem atualizada instantaneamente
```

---

## 📂 Estrutura Hexagonal do Projeto

```
src/
├── main/
│   ├── java/dev/gdinizds/discordsearchbot/
│   │   ├── DiscordSearchBotApplication.java
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   │   ├── SearchType.java           # Enum (WEB, IMAGE, FILE, NEWS) e aliases
│   │   │   │   ├── SearchFilter.java         # Value Object para filtros e paginação
│   │   │   │   ├── SearchHit.java            # Resultado textual
│   │   │   │   ├── ImageHit.java             # Resultado de imagem com metadados e S3
│   │   │   │   ├── HitItem.java              # Item unificado de exibição
│   │   │   │   ├── SearchSession.java        # Sessão de busca com estado de navegação
│   │   │   │   ├── SearchCommand.java        # Comando de entrada de pesquisa
│   │   │   │   ├── NavigationCommand.java    # Comando de paginação por botões
│   │   │   │   ├── AuditLogEntry.java        # Registro imutável de auditoria
│   │   │   │   └── OutboundResponse.java     # Resposta para o Discord com Embeds e Botões
│   │   │   └── service/
│   │   │       ├── CommandTextParser.java    # Parser flexível de comandos .pesquisar
│   │   │       └── SearchLayoutBuilder.java  # Renderizador de Embeds e ActionRows
│   │   ├── application/
│   │   │   ├── port/
│   │   │   │   ├── in/
│   │   │   │   │   ├── ExecuteSearchUseCase.java
│   │   │   │   │   └── NavigateSearchUseCase.java
│   │   │   │   └── out/
│   │   │   │       ├── SearchEnginePort.java
│   │   │   │       ├── ImageStoragePort.java
│   │   │   │       ├── SearchSessionPort.java
│   │   │   │       ├── AuditLogPort.java
│   │   │   │       └── ReplyPublisherPort.java
│   │   │   └── service/
│   │   │       ├── SearchApplicationService.java
│   │   │       └── Mdc.java
│   │   ├── adapter/
│   │   │   ├── in/kafka/
│   │   │   │   ├── InboundEvent.java
│   │   │   │   ├── InboundEventMapper.java
│   │   │   │   └── InboundEventConsumer.java
│   │   │   └── out/
│   │   │       ├── searxng/SearxngClient.java
│   │   │       ├── s3/
│   │   │       │   ├── ImageFormat.java
│   │   │       │   ├── SafeImageDownloader.java
│   │   │       │   └── S3ImageStore.java
│   │   │       ├── persistence/
│   │   │       │   ├── JdbcAuditLogAdapter.java
│   │   │       │   └── JdbcSearchSessionAdapter.java
│   │   │       └── kafka/
│   │   │           ├── GatewayResponse.java
│   │   │           ├── GatewayResponsePublisher.java
│   │   │           └── CommandRegistrar.java
│   │   └── config/
│   │       ├── ApplicationConfig.java
│   │       ├── SearchBotProperties.java
│   │       ├── S3Config.java
│   │       ├── KafkaConfig.java
│   │       ├── ResilienceConfig.java
│   │       ├── Resilience.java
│   │       └── NativeRuntimeHints.java
│   └── resources/
│       ├── application.yaml
│       └── db/migration/
│           ├── V1__init_schema.sql
│           ├── V2__safesearch_config.sql
│           └── V3__partition_audit_log_and_cleanup_indexes.sql
└── test/
    └── java/dev/gdinizds/discordsearchbot/
        ├── ArchitectureTest.java             # ArchUnit validando fronteiras hexagonais
        ├── domain/
        ├── application/
        └── adapter/
```

---

## 🎯 Comandos Suportados

### 1. Slash Command: `/pesquisar`
Registrado automaticamente no Discord Gateway via `discord.gateway.commands`.

| Parâmetro | Tipo | Obrigatório | Descrição | Exemplo |
| :--- | :--- | :---: | :--- | :--- |
| `termo` | `STRING` | **Sim** | Termo ou expressão a ser pesquisada | `kubernetes tutorial` |
| `tipo` | `STRING` | Não | Tipo de pesquisa: `web`, `imagem`, `arquivo`, `noticia` | `imagem` |
| `localizacao` | `STRING` | Não | Código de idioma e região | `pt-BR`, `en-US` |
| `safesearch` | `INTEGER` | Não | Filtro de segurança: `0` (off), `1` (moderado), `2` (estrito) | `1` |

### 2. Dot Command: `.pesquisar`
Processa mensagens de texto comuns com suporte a argumentos posicionais ou flags:

```bash
# Busca Web padrão
.pesquisar arquitetura de microsserviços

# Busca por tipo via palavra-chave inicial
.pesquisar imagem pôr do sol na praia
.pesquisar arquivo arch linux iso
.pesquisar noticia eleições 2026

# Busca avançada com flags
.pesquisar --tipo=imagem --lang=pt-BR -s 2 capivara filhote
.pesquisar -t arquivo -l en-US spring framework pdf
```

---

## 🖼️ Disposição e Layout Interativo no Discord

A resposta do bot é estruturada para oferecer máxima usabilidade visual e funcional:

- **Embed Principal:**
  - **Título:** Título do resultado com link markdown clicável.
  - **Descrição:** Resumo textual higienizado com link direto e destaque dos termos.
  - **Mídia:**
    - No modo `imagem`: incorporação visual da imagem armazenada no S3 Garage.
    - Nos modos `web`, `arquivo` e `noticia`: exibição de thumbnail contextual se disponível.
  - **Campos Informativos:**
    - `📑 Categoria`: Web, Imagem, Arquivo ou Notícia.
    - `🔍 Motor`: Identificador do provedor (ex: Google, Bing, DuckDuckGo, Qwant).
    - `📊 Posição`: Indicador da página atual (ex: `1 de 10`).
  - **Rodapé:** Identificação da pesquisa e usuário solicitante (`SearXNG • Solicitado por @usuario`).
- **Botões Interativos (ActionRow):**
  - `⬅️ Anterior`: retrocede para o resultado anterior (desabilitado na 1ª posição).
  - `🎲 Aleatório`: sorteia um resultado aleatório da lista para descoberta dinâmica.
  - `Próximo ➡️`: avança para o próximo resultado (desabilitado na última posição).
  - `🌐 Acessar`: botão estilo Link que abre a página de origem em nova aba.

---

## 🛡️ Proteção Anti-SSRF, Hardening S3 e Virtual Threads

O módulo `SafeImageDownloader` e o adaptador `S3ImageStore` implementam proteção em profundidade:

1. **Validação de Rede Pré-Conexão e Proteção Anti-SSRF:**
   - Bloqueio estrito de resolução DNS para faixas privadas IPv4 (RFC 1918 `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), CGNAT (`100.64.0.0/10`), link-local (`169.254.0.0/16`) e loopback (`127.0.0.0/8`).
   - Bloqueio completo de evasões via IPv6 e IPv4-mapped/compatible (`::1`, `::ffff:127.0.0.1`, `::127.0.0.1`, `::ffff:169.254.169.254`, `::169.254.169.254`, faixas ULA `fc00::/7` e link-local `fe80::/10`).
   - Bloqueio de domínios internos de infraestrutura (`.consul`, `.cluster.local`, `localhost`).
2. **Restrição Estrita de Portas e Redirecionamentos:**
   - Permite apenas portas seguras (`80` e `443`).
   - Limite de 3 saltos HTTP com revalidação completa de IP e política de rede no cabeçalho `Location` a cada redirect.
3. **Inspeção de Magic Bytes e Limitação de Stream:**
   - Validação binária estrita dos magic bytes reais (PNG, JPEG, GIF, WEBP) descartando arquivos maliciosos ou disfarçados.
   - Streaming com corte estrito no `InputStream` (máximo de 10MB) prevenindo esgotamento de memória e DoS.
4. **Armazenamento S3 Seguro (Garage S3):**
   - Sanitização de S3 Object Keys com regex estrita `^[a-zA-Z0-9_.-]+$` contra ataques de **Path Traversal**.
   - Integração com `UrlConnectionHttpClient` no AWS S3 SDK, eliminando o problema de **Thread Pinning** sob Virtual Threads do Java 25 (presente no cliente Apache HTTP).

---

## 📊 Persistência, Particionamento e Governança de Dados (PostgreSQL)

O schema `search_bot` é versionado via **Flyway** e adota particionamento temporal nativo e expurgo automatizado no PostgreSQL:

```sql
-- Configuração de SafeSearch por Guild/Canal
CREATE TABLE search_bot.safesearch_config (
    id BIGSERIAL PRIMARY KEY,
    guild_id VARCHAR(64) NOT NULL,
    channel_id VARCHAR(64) NOT NULL DEFAULT '',
    level INT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_safesearch_scope UNIQUE (guild_id, channel_id)
);

-- Sessões interativas dos botões (otimizada com fillfactor 85 para HOT updates)
CREATE TABLE search_bot.search_sessions (
    id VARCHAR(64) PRIMARY KEY,
    correlation_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    guild_id VARCHAR(64),
    channel_id VARCHAR(64),
    message_id VARCHAR(64),
    interaction_token VARCHAR(256),
    query TEXT NOT NULL,
    search_type VARCHAR(32) NOT NULL,
    current_index INT NOT NULL DEFAULT 0,
    total_results INT NOT NULL,
    results_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL
) WITH (fillfactor = 85);

CREATE INDEX idx_sessions_expires_at ON search_bot.search_sessions (expires_at);
CREATE INDEX idx_sessions_user_id ON search_bot.search_sessions (user_id);
CREATE INDEX idx_sessions_correlation_id ON search_bot.search_sessions (correlation_id);

-- Tabela de auditoria particionada por range de data (pg_partman)
CREATE TABLE search_bot.search_audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    correlation_id VARCHAR(128) NOT NULL,
    guild_id VARCHAR(64),
    channel_id VARCHAR(64),
    user_id VARCHAR(64),
    username VARCHAR(128),
    event_type VARCHAR(64) NOT NULL,
    command_name VARCHAR(64),
    query TEXT,
    search_type VARCHAR(32),
    request_payload JSONB NOT NULL,
    response_payload JSONB,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    execution_time_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE INDEX idx_audit_correlation_id ON search_bot.search_audit_log (correlation_id);
CREATE INDEX idx_audit_user_id ON search_bot.search_audit_log (user_id);
CREATE INDEX idx_audit_guild_id ON search_bot.search_audit_log (guild_id);
CREATE INDEX idx_audit_created_at ON search_bot.search_audit_log (created_at DESC);
```

### Automação de Banco de Dados com `pg_partman` e `pg_cron`

Em conformidade com as melhores práticas de banco de dados, **nenhuma deleção periódica ou manutenção de partições roda via código Java / Spring `@Scheduled`**. Todas as rotinas são executadas pelo PostgreSQL via `pg_cron` no banco central `app`:

1. **Particionamento Diário e Retenção (`pg_partman`):**
   - Tabela particionada diariamente com `premake = 3` e retenção estrita de **30 dias**.
   - Job `partman-maintenance-discord-search-bot` agendado no `pg_cron` a cada hora (`40 * * * *`):
     ```sql
     SELECT cron.schedule_in_database('partman-maintenance-discord-search-bot', '40 * * * *', 'SELECT partman.run_maintenance(p_analyze := FALSE)', 'discord_search_bot', 'postgres');
     ```
2. **Expurgo Automático de Sessões Expiradas:**
   - Job `purge-search-sessions` agendado no `pg_cron` a cada 15 minutos (`*/15 * * * *`):
     ```sql
     SELECT cron.schedule_in_database('purge-search-sessions', '*/15 * * * *', 'DELETE FROM search_bot.search_sessions WHERE expires_at < CURRENT_TIMESTAMP', 'discord_search_bot', 'postgres');
     ```
3. **HOT (Heap-Only Tuples) Updates:**
   - `search_sessions` configurada com `fillfactor = 85`, permitindo atualizações in-place na navegação de índices (`current_index`, `updated_at`) sem gerar bloat.
   - Remoção de índices redundantes (`idx_safesearch_scope` coberto por `uk_safesearch_scope` e `idx_audit_event_type` de baixa cardinalidade).

---

## 📨 Mensageria de Alta Performance (Redpanda / Kafka)

O consumo e produção de eventos foi ajustado para máxima vazão e tolerância a falhas sobre o cluster Redpanda de 3 nós:

- **Concorrência Alinhada (3 Partições):**
  - Listener configurado com `concurrency = 3`, garantindo 1 consumidor dedicado por partição do tópico sem contenção.
  - Rebalanceamento não-destrutivo com `CooperativeStickyAssignor`.
- **Confirmação Manual de Offset (`MANUAL_IMMEDIATE`):**
  - Integração com `Acknowledgment` nos listeners acoplada ao ciclo de vida das Virtual Threads. O commit do offset só ocorre após o término do processamento com sucesso ou descarte explícito.
- **Isolamento de Poison Pills (Dead Letter Topic):**
  - Integração do `DeadLetterPublishingRecoverer` publicando mensagens irrecuperáveis no tópico correspondente `.DLT`.
  - `DefaultErrorHandler` com `FixedBackOff(1000L, 2)` para retentativas imediatas antes de encaminhar ao DLT.
- **Otimização de Throughput do Producer:**
  - `enable.idempotence = true` para garantia estrita de entrega exactly-once sem duplicações.
  - Compressão `lz4`, `linger.ms = 5` e `batch.size = 32768` para agregação de batch de alto rendimento.

---

## 🛡️ Resiliência com Resilience4j

Todas as chamadas para serviços externos contam com proteção de falha em cascata:

| Alvo | Circuit Breaker | Retry | Time Limiter | Bulkhead |
| :--- | :---: | :---: | :---: | :---: |
| **SearXNG API** | 50% falha, janela de 10 | 2 tentativas com backoff | 6s | - |
| **Garage S3 Storage** | 50% falha, janela de 10 | 2 tentativas (500ms) | 8s | - |
| **PostgreSQL Database** | 50% falha, janela de 20 | 3 tentativas | - | - |
| **Processamento Concorrente** | - | - | - | 32 chamadas simultâneas |

---

## 🚀 Compilação Nativa com Oracle GraalVM (GFTC)

O binário nativo é gerado pelo plugin `org.graalvm.buildtools.native` utilizando a imagem oficial da Oracle:

```dockerfile
FROM container-registry.oracle.com/graalvm/native-image:25 AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew nativeCompile --no-daemon -x test

FROM debian:bookworm-slim AS runtime
WORKDIR /app
COPY --from=build /workspace/build/native/nativeCompile/discord-search-bot /app/discord-search-bot
USER 1001
ENTRYPOINT ["/app/discord-search-bot", "-XX:MaximumHeapSizePercent=50"]
```

Benefícios da execução nativa:
- **Tempo de Inicialização:** < 80ms para ficar pronto e responder ao Kafka.
- **Consumo de Memória Base:** ~45MB RSS (em comparação aos ~350MB de uma JVM tradicional).
- **Segurança:** Superfície de ataque reduzida com contêiner `debian:bookworm-slim` sem ferramentas de desenvolvimento e sem execução como root.

---

## 🚢 Deploy no Kubernetes via GitOps (FluxCD)

O ciclo de vida no cluster é gerenciado através do repositório GitOps [fleet-infra](https://github.com/gdinizds/fleet-infra.git):

- **Injeção de Segredos pelo HashiCorp Vault:**
  - Banco de Dados: `secret/discord-search-bot/db` (injetado em `/vault/secrets/db.yaml`).
  - Armazenamento S3: `secret/discord-search-bot/s3` (injetado em `/vault/secrets/s3.yaml`).
- **Configurações Centralizadas no Consul:**
  - Carregadas automaticamente via Spring Cloud Consul Config em `consul-server.consul.svc.cluster.local:8500`.
- **Hardening de Segurança e Isolamento:**
  - Sistema de arquivos somente leitura: `readOnlyRootFilesystem: true`.
  - Execução não-root com UID dedicado (`runAsUser: 1001`), `allowPrivilegeEscalation: false` e drop total de capabilities (`drop: ["ALL"]`).
  - Restrição do Spring Boot Actuator (`show-details: never`).
  - Isolamento de rede estrito via `NetworkPolicy` dedicada no namespace `discord-search-bot`.
- **Image Update Automation do Flux:**
  - A cada nova tag gerada no GitHub (`v*.*.*`), o GitHub Actions constrói a imagem no GHCR e o Flux aplica a atualização no cluster via RollingUpdate.

---

## 🛠️ Comandos de Desenvolvimento

```bash
# Executar todos os testes automatizados
./gradlew test

# Executar aplicação localmente
./gradlew bootRun

# Compilar binário nativo localmente
./gradlew nativeCompile
```
