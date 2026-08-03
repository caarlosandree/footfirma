-- O importador registra o que fez. Sem isso, "a carga rodou" é afirmação sem
-- lastro: não há como saber qual dataset entrou, quantas linhas foram criadas
-- nem quais foram recusadas.
create table importacao_execucao (
    id              bigint      generated always as identity primary key,
    dataset_versao  text        not null check (length(dataset_versao) between 1 and 60),
    schema_versao   text        not null check (length(schema_versao) between 1 and 10),
    diretorio       text        not null check (length(diretorio) between 1 and 400),
    status          text        not null check (status in ('EM_ANDAMENTO', 'CONCLUIDA', 'FALHOU')),
    iniciada_em     timestamptz not null default now(),
    finalizada_em   timestamptz,
    motivo_da_falha text        check (length(motivo_da_falha) between 1 and 400),
    constraint ck_importacao_execucao_falha
        check ((status = 'FALHOU') = (motivo_da_falha is not null))
);

create index idx_importacao_execucao_iniciada on importacao_execucao (iniciada_em desc);

comment on column importacao_execucao.dataset_versao is
    'Vem do manifest.json. É o que liga um estado do banco ao dataset que o produziu';

-- Contagem por entidade em tabela, não em jsonb: o resto do schema é tipado e
-- consultável, e "quantos jogadores foram criados na última carga" deve ser um
-- where, não uma extração de JSON.
create table importacao_contagem (
    id          bigint  generated always as identity primary key,
    execucao_id bigint  not null references importacao_execucao (id),
    entidade    text    not null check (length(entidade) between 1 and 60),
    lidos       integer not null default 0 check (lidos >= 0),
    criados     integer not null default 0 check (criados >= 0),
    atualizados integer not null default 0 check (atualizados >= 0),
    recusados   integer not null default 0 check (recusados >= 0),
    constraint uq_importacao_contagem unique (execucao_id, entidade)
);

-- Linha que o importador recusou antes de tocar no banco. Erro de banco não vira
-- ocorrência: ele aborta a etapa, porque uma transação marcada como rollback-only
-- não pode prosseguir fingindo que gravou.
create table importacao_ocorrencia (
    id            bigint      generated always as identity primary key,
    execucao_id   bigint      not null references importacao_execucao (id),
    entidade      text        not null check (length(entidade) between 1 and 60),
    linha         integer     not null check (linha > 0),
    chave         text        not null check (length(chave) between 1 and 220),
    severidade    text        not null check (severidade in ('AVISO', 'ERRO')),
    motivo        text        not null check (length(motivo) between 1 and 240),
    registrada_em timestamptz not null default now()
);

create index idx_importacao_ocorrencia_execucao on importacao_ocorrencia (execucao_id);

-- estadio nasceu sem chave natural em V5. Sem ela não existe upsert idempotente:
-- reimportar o mesmo dataset duplicaria todo estádio. Constraint nova em migration
-- nova — V5 permanece intocada.
alter table estadio add constraint uq_estadio_nome_cidade unique (nome, cidade);
