create table pais (
    id           bigint generated always as identity primary key,
    iso_code     text        not null unique check (length(iso_code) = 3),
    nome         text        not null check (length(nome) between 1 and 80),
    confederacao text        not null check (confederacao in ('CONMEBOL', 'UEFA', 'CONCACAF', 'CAF', 'AFC', 'OFC')),
    criado_em    timestamptz not null default now()
);

create table estado (
    id      bigint generated always as identity primary key,
    pais_id bigint not null references pais (id),
    uf      text   not null check (length(uf) = 2),
    nome    text   not null check (length(nome) between 1 and 60),
    constraint uq_estado_pais_uf unique (pais_id, uf)
);

create index idx_estado_pais on estado (pais_id);
