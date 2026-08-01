---
trigger: model_decision
globs: "src/main/resources/db/migration/*.sql"
description: Migrations Flyway, modelagem PostgreSQL, mapeamento JPA e uso do Redis no backend FootFirma.
---

# FootFirma Backend — Banco de Dados

PostgreSQL como banco relacional, Redis como cache e apoio. Schema governado
exclusivamente por Flyway.

## Ambiente local

`compose.yaml` (raiz de `backend/footfirma/`) define:

| Serviço | Porta | Detalhe |
|---|---|---|
| `postgres` | 5432 | database `footfirma` |
| `redis` | 6379 | — |

O `spring-boot-docker-compose` sobe esses containers ao rodar `./gradlew bootRun`.
As credenciais do compose são **locais**; nunca as reutilize em outro ambiente nem
as escreva em código.

## Flyway

Local: `src/main/resources/db/migration/` (hoje vazio — a primeira migration ainda
será criada).

### Convenções

- Nome: `V{numero}__{descricao_em_snake_case}.sql` — dois underscores.
  `V1__cria_tabela_usuario.sql`, `V2__adiciona_indice_partida_inicio.sql`.
- Numeração sequencial inteira, sem buracos e sem timestamp.
- Uma migration = uma mudança lógica coesa.
- SQL puro. Migrations Java só se houver transformação de dados impossível em SQL.
- Comentário no topo explicando o **porquê** quando a mudança não for óbvia.

### Regras invioláveis

- **Migration aplicada nunca é editada.** Nem para corrigir typo em comentário.
  Corrija com uma nova migration.
- `spring.jpa.hibernate.ddl-auto` nunca gera schema. O alvo é `validate`.
  Se a entidade e a tabela divergirem, a migration é que está faltando.
- Toda mudança de `@Entity` que afete o schema exige migration no mesmo commit.
- Migration destrutiva (`DROP COLUMN`, `DROP TABLE`, mudança de tipo com perda) é
  feita em duas etapas separadas por um deploy: primeiro pare de usar, depois remova.

### Tabela de eventos do Modulith

O `spring-modulith-starter-jpa` persiste publicações de evento na tabela
`event_publication`. Ela precisa existir via Flyway antes do primeiro evento
persistido. O DDL correspondente está no jar do Modulith
(`org/springframework/modulith/events/jdbc/schema-postgresql.sql`) — copie para uma
migration própria em vez de habilitar criação automática de schema.

## Modelagem PostgreSQL

### Nomenclatura

| Objeto | Convenção | Exemplo |
|---|---|---|
| Tabela | `snake_case`, singular | `partida`, `inscricao_partida` |
| Coluna | `snake_case` | `criado_em`, `quadra_id` |
| Chave primária | `id` | `id uuid primary key` |
| Chave estrangeira | `{tabela}_id` | `usuario_id` |
| Índice | `idx_{tabela}_{colunas}` | `idx_partida_inicio` |
| Índice único | `uq_{tabela}_{colunas}` | `uq_usuario_email` |
| Constraint check | `ck_{tabela}_{regra}` | `ck_partida_vagas_positivas` |
| Chave estrangeira (constraint) | `fk_{tabela}_{referencia}` | `fk_inscricao_partida` |

Nomes em português, coerentes com os módulos de domínio.

### Tipos

- Identificador: `uuid` (`gen_random_uuid()` via extensão `pgcrypto`) ou `bigint
  generated always as identity`. Escolha uma estratégia e mantenha no projeto inteiro.
- Texto: `text` com `check` de tamanho quando houver limite de negócio.
  Não use `varchar(n)` arbitrário nem `char`.
- Dinheiro: `numeric(12,2)`. **Nunca** `float`/`double`.
- Data e hora: `timestamptz` sempre. `timestamp` sem timezone é bug esperando acontecer.
- Booleano: `boolean` com `not null default false`.
- Enum de domínio: `text` + `check (status in (...))`, mapeado com
  `@Enumerated(EnumType.STRING)`. Não use tipo `enum` nativo do Postgres (alterar
  depois é doloroso) nem `EnumType.ORDINAL`.
- JSON: `jsonb`, nunca `json`.

### Constraints

Regra que precisa ser verdade no banco vai como constraint, não só como validação
Java. `not null`, `unique`, `check` e `foreign key` são baratos e impedem dado
corrompido por caminho não previsto.

```sql
create table partida (
    id          uuid primary key default gen_random_uuid(),
    titulo      text        not null check (length(titulo) between 1 and 120),
    inicio      timestamptz not null,
    vagas       integer     not null check (vagas > 0),
    quadra_id   uuid        not null references quadra (id),
    criado_em   timestamptz not null default now(),
    atualizado_em timestamptz
);

create index idx_partida_inicio on partida (inicio);
```

### Índices

- Toda FK usada em join ou filtro tem índice (o Postgres **não** cria automaticamente).
- Índice composto segue a ordem dos filtros mais seletivos primeiro.
- Índice parcial para subconjunto quente: `where cancelada = false`.
- Não crie índice "por precaução": cada um custa escrita e espaço.

## JPA

- `@Entity` em `<feature>/domain/`, sem lógica de negócio complexa e sem dependência de service.
- `FetchType.LAZY` em **todo** `@ManyToOne` e `@OneToOne` — o padrão do JPA é EAGER e
  é quase sempre errado.
- N+1: resolva com `@EntityGraph` ou `join fetch` na query, nunca iterando e
  acessando a associação dentro do loop.
- Paginação sempre via `Pageable`; nunca `findAll()` seguido de corte em memória.
- Query derivada por nome do método enquanto for legível; acima disso, `@Query` com
  JPQL nomeado e parâmetros nomeados.
- **Nunca** concatene valor em string de query. Parâmetro sempre.
- Alterou entidade? Verifique se precisa de migration antes de terminar a tarefa.

```java
// ✅ parametrizado
@Query("select p from Partida p where p.quadra.id = :quadraId and p.inicio >= :desde")
List<Partida> buscarPorQuadraDesde(@Param("quadraId") UUID quadraId,
                                   @Param("desde") OffsetDateTime desde);
```

## Redis

- Uso previsto: cache de leitura e dados efêmeros (sessão, rate limit, presença).
- **Todo dado no Redis é descartável.** Nada pode existir só lá.
- Defina TTL em toda chave. Chave sem expiração vira vazamento.
- Nome de chave hierárquico e previsível: `footfirma:partida:{id}`,
  `footfirma:ratelimit:{ip}`.
- Serialize DTOs, nunca entidades JPA (proxies não serializam de forma confiável).
- Invalide o cache na escrita, no mesmo service que alterou o dado — mas **fora** da
  transação do banco (publique um evento).

## Checklist de mudança no banco

- [ ] Migration criada com o próximo número e nome descritivo
- [ ] Nenhuma migration já aplicada foi editada
- [ ] Constraints (`not null`, `unique`, `check`, FK) refletem as regras de negócio
- [ ] Índices criados para as FKs e filtros novos
- [ ] `timestamptz` para datas, `numeric` para valores monetários
- [ ] Entidade JPA corresponde exatamente ao schema
- [ ] Associações `@ManyToOne`/`@OneToOne` marcadas como `LAZY`
- [ ] Nenhuma query monta SQL por concatenação
- [ ] Teste com `@DataJpaTest` + Testcontainers cobre a mudança
- [ ] Migration rodou localmente sem erro

## Módulos relacionados

- `.rules/java-core.md` — transações e arquitetura
- `.rules/java-testing.md` — Testcontainers
- `.rules/security.md` — dados pessoais e SQL injection
