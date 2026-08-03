-- Temporada é referenciada por jogador_atributo, jogador_vinculo e edicao.
-- Vive em módulo próprio para que jogador não precise depender de competicao.
create table temporada (
    id         bigint generated always as identity primary key,
    label      text        not null unique check (length(label) between 4 and 9),
    ano_inicio integer     not null check (ano_inicio between 1900 and 2200),
    ano_fim    integer     not null check (ano_fim between 1900 and 2200),
    criado_em  timestamptz not null default now(),
    constraint ck_temporada_anos check (ano_fim >= ano_inicio)
);

comment on column temporada.label is 'Rótulo exibível: "2025" para temporada de ano civil, "2025/26" para temporada europeia';
