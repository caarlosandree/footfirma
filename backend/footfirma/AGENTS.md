# FootFirma — Backend

API do FootFirma: Spring Boot 4 sobre Java 25, organizada em módulos de domínio
verificados pelo Spring Modulith, com PostgreSQL, Redis e Flyway.

Este arquivo é o índice. As regras completas estão em `.rules/` — **leia o arquivo
relevante antes de escrever código**, não trabalhe só por este resumo.

## Regras

| Arquivo | Leia antes de |
|---|---|
| `.rules/java-core.md` | qualquer código Java — arquitetura, módulos, camadas, DI, transações, Lombok, MapStruct |
| `.rules/java-api.md` | mexer em controller, DTO, validação, erro ou OpenAPI |
| `.rules/java-testing.md` | escrever ou alterar teste |
| `.rules/database.md` | criar migration, entidade, query ou usar Redis |
| `.rules/security.md` | qualquer endpoint, autenticação, autorização ou dado sensível |
| `.rules/java-checklist.md` | **encerrar qualquer tarefa** (obrigatório) |
| `.rules/commit.md` | fazer commit |
| `docs/runbooks/validation.md` | decidir **qual** validação rodar para a mudança em mãos |

## Documentação

`.rules/` diz **como trabalhar** no repositório; `docs/` diz **o que o sistema é**.
Toda nota em `docs/` segue o padrão obrigatório de `docs/README.md`:
`Status: verificado em AAAA-MM-DD`, `## Fontes` com caminhos reais e um comando de
validação. Nota sem fonte verificável não entra.

## Guardrails automáticos

`.claude/settings.json` registra um hook `PreToolUse` (`.claude/hooks/guard.mjs`) que
**bloqueia de verdade**, não apenas avisa:

- subir serviços (`./gradlew bootRun`, `docker compose up`)
- `git push` e force-push — **só o que publica no remote**; operação local é livre
- editar ou commitar migration Flyway já versionada
- commitar segredo (PAT, chave de API, private key, URL de banco com senha, JWT)

Bloqueio não é sugestão de contornar por outro caminho. Se o usuário realmente quer o
comando, ele executa na própria sessão com `! <comando>`.

## Comandos

```bash
./gradlew compileJava    # validação rápida — use este durante o trabalho
./gradlew test           # suíte completa (exige Docker; não rode a cada edição)
./gradlew build          # compila + testa
./gradlew bootRun        # sobe a API + Postgres + Redis (só com pedido explícito)
```

Swagger UI: `http://localhost:8080/swagger-ui.html` · OpenAPI: `/v3/api-docs`

## Estrutura

```
src/main/java/br/com/api/footfirma/
├── FootfirmaApplication.java
├── config/       # cross-cutting (OpenApiConfig, SecurityConfig) — módulo aberto
├── shared/       # tipos reutilizáveis entre módulos — módulo aberto
└── <feature>/    # um módulo Modulith por domínio: web/ domain/ repository/ mapper/ dto/ internal/

src/main/resources/
├── application.properties
└── db/migration/ # Flyway: V{n}__descricao.sql (V1…V16 aplicadas)
```

Status: verificado em 2026-08-03.

Sete módulos de domínio existem: `temporada`, `geografia`, `clube`, `jogador`,
`competicao`, `avaliacao` e `mundo`. **Ao criar o próximo, copie o formato de um
deles** — a estrutura já está estabelecida, e partir do zero só produz divergência.

Dois pontos que a leitura do código não entrega de imediato:

- **Referência a outro módulo é coluna `Long` crua, nunca `@ManyToOne`.** Associação
  JPA só dentro do mesmo módulo. Veja `Jogador.paisId`, `JogadorAtributo.temporadaId`
  e as três de `JogadorOverall`. A integridade fica na chave estrangeira da migration.
- **Subpacote só cruza a fronteira do módulo com `@NamedInterface`.** Hoje
  `temporada/dto`, `clube/dto`, `jogador/dto` e `avaliacao/dto` o declaram. Sem isso o
  `ModularidadeTest` reprova o consumidor — e o erro aparece como falha de
  modularidade, não como erro de compilação no lugar da causa.

A API é read-only por decisão registrada em
`docs/adr/2026-08-01-catalogo-read-only.md`: nenhum controller aceita `POST`, `PUT`,
`PATCH` ou `DELETE`. A única escrita vem do módulo `mundo`, que não é endpoint — é um
`ApplicationRunner` sob o profile `mundo`.

Em banco zerado o catálogo continua **vazio de clubes e jogadores**, e os endpoints
respondem lista vazia e 404. Rodar o gerador popula duas ligas fictícias com 40
clubes e 1.520 jogadores — **tudo inventado menos a geografia**. Ver
`docs/runbooks/mundo.md`.

Três coisas que a leitura de `mundo` não entrega de imediato:

- **Ele orquestra e não escreve em tabela alheia.** Cada módulo de catálogo expõe
  métodos `sincronizar*`; o gerador só os chama. A exceção é `LimpezaDoCatalogo`, que
  apaga por `JdbcTemplate` — justificada em `docs/adr/2026-08-03-mundo-gerado.md`.
- **`gerar()` não abre transação envolvente, de propósito.**
  `AvaliacaoService.materializar` é `NOT_SUPPORTED` e suspende a transação corrente:
  dentro de um commit único ela não veria os atributos recém-gravados e produziria
  zero overall.
- **As fábricas são puras** — recebem `SplittableRandom` e devolvem records, sem
  Spring e sem banco. É o que permite testar balanceamento sem Docker.

## O que nunca fazer neste repositório

- Criar pacotes técnicos globais (`controllers/`, `services/`, `entities/`) — a
  organização é **package by feature** e o Spring Modulith valida isso
- Referenciar tipo de subpacote interno de outro módulo
- Escrever em tabela de outro módulo a partir de `mundo` — a geração passa pela
  interface pública do módulo dono, sempre
- Expor `@Entity` em controller, DTO ou payload de evento
- Editar migration já aplicada
- Deixar o schema ser gerado por `ddl-auto` em vez de Flyway
- Usar H2 ou mock de repository em teste de persistência — é Testcontainers
- Reordenar os annotation processors do `build.gradle` (lombok → binding → mapstruct)
- Usar `@Data`/`@EqualsAndHashCode` em `@Entity`
- Escrever `componentModel = "spring"` no `@Mapper` (já é padrão do build)
- Copiar snippets de Spring Boot 3: aqui é Boot 4, os starters mudaram de nome
- Subir serviços (`bootRun`, `docker compose up`) sem pedido explícito
- `git push` / force-push sem pedido explícito (`merge` local é livre)
- Adicionar `Co-authored-by:` em mensagem de commit

## Onde este repositório se encaixa

O frontend Next.js vive em `../../frontend`, no **mesmo repositório git** — a raiz é um
monorepo, não um agrupador de repositórios separados. Ele consome esta API em
`/api/v1/`. Contrato quebrado aqui quebra o frontend: mudança incompatível vira
`/api/v2`, não alteração da v1. Índice do sistema: `../../AGENTS.md`.
