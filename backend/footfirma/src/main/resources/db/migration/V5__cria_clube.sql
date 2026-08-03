create table estadio (
    id              bigint generated always as identity primary key,
    nome            text        not null check (length(nome) between 1 and 120),
    cidade          text        not null check (length(cidade) between 1 and 80),
    estado_id       bigint      references estado (id),
    capacidade      integer     check (capacidade > 0),
    ano_inauguracao integer     check (ano_inauguracao between 1800 and 2200),
    criado_em       timestamptz not null default now()
);

create index idx_estadio_estado on estadio (estado_id);

create table clube (
    id             bigint generated always as identity primary key,
    slug           text        not null unique check (length(slug) between 2 and 80),
    nome_oficial   text        not null check (length(nome_oficial) between 1 and 120),
    nome_curto     text        not null check (length(nome_curto) between 1 and 40),
    apelido        text        check (length(apelido) between 1 and 60),
    ano_fundacao   integer     check (ano_fundacao between 1800 and 2200),
    pais_id        bigint      not null references pais (id),
    estado_id      bigint      references estado (id),
    estadio_id     bigint      references estadio (id),
    cor_primaria   text        check (cor_primaria ~ '^#[0-9A-Fa-f]{6}$'),
    cor_secundaria text        check (cor_secundaria ~ '^#[0-9A-Fa-f]{6}$'),
    reputacao      integer     not null default 50 check (reputacao between 0 and 99),
    qualidade_base integer     not null default 50 check (qualidade_base between 0 and 99),
    estado_base_id bigint      references estado (id),
    criado_em      timestamptz not null default now(),
    atualizado_em  timestamptz
);

create index idx_clube_pais on clube (pais_id);
create index idx_clube_estado on clube (estado_id);
create index idx_clube_estadio on clube (estadio_id);

comment on column clube.qualidade_base is 'Qualidade da categoria de base (0-99). Alimenta o gerador de jogadores no Plano 2';
comment on column clube.estado_base_id is 'Estado de onde a base do clube tende a recrutar. Nulo = sem viés regional';

create table clube_alias (
    id       bigint generated always as identity primary key,
    clube_id bigint not null references clube (id),
    alias    text   not null check (length(alias) between 1 and 120),
    fonte    text   not null check (fonte in ('EA_FC', 'TRANSFERMARKT', 'MANUAL')),
    constraint uq_clube_alias unique (alias, fonte)
);

create index idx_clube_alias_clube on clube_alias (clube_id);

create table clube_referencia_externa (
    id         bigint generated always as identity primary key,
    clube_id   bigint not null references clube (id),
    fonte      text   not null check (fonte in ('EA_FC', 'TRANSFERMARKT')),
    id_externo text   not null check (length(id_externo) between 1 and 60),
    constraint uq_clube_referencia_externa unique (fonte, id_externo)
);

create index idx_clube_referencia_externa_clube on clube_referencia_externa (clube_id);
