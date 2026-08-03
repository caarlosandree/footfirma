create table competicao (
    id      bigint  generated always as identity primary key,
    slug    text    not null unique check (length(slug) between 2 and 80),
    nome    text    not null check (length(nome) between 1 and 120),
    pais_id bigint  references pais (id),
    tipo    text    not null check (tipo in ('LIGA', 'COPA', 'MISTA')),
    nivel   integer check (nivel between 1 and 10),
    genero  text    not null default 'MASCULINO' check (genero in ('MASCULINO', 'FEMININO'))
);

create index idx_competicao_pais on competicao (pais_id);

comment on column competicao.pais_id is 'Nulo para competição continental (Libertadores, Sul-Americana)';
comment on column competicao.nivel is 'Divisão na pirâmide nacional: 1 = Série A, 2 = Série B. Nulo para copa';

create table edicao (
    id            bigint generated always as identity primary key,
    competicao_id bigint not null references competicao (id),
    temporada_id  bigint not null references temporada (id),
    nome          text   not null check (length(nome) between 1 and 140),
    data_inicio   date,
    data_fim      date,
    constraint uq_edicao unique (competicao_id, temporada_id),
    constraint ck_edicao_datas check (data_fim is null or data_inicio is null or data_fim >= data_inicio)
);

create index idx_edicao_temporada on edicao (temporada_id);

-- Fase existe desde a v1 porque a Copa do Brasil obriga o modelo a suportar
-- mata-mata: sem ela, a limitação só apareceria ao adicionar a Libertadores.
create table fase (
    id                  bigint  generated always as identity primary key,
    edicao_id           bigint  not null references edicao (id),
    ordem               integer not null check (ordem > 0),
    nome                text    not null check (length(nome) between 1 and 60),
    tipo                text    not null check (tipo in ('PONTOS_CORRIDOS', 'GRUPOS', 'ELIMINATORIA')),
    jogos_por_confronto integer not null default 1 check (jogos_por_confronto between 1 and 2),
    tem_gol_fora        boolean not null default false,
    tem_prorrogacao     boolean not null default false,
    tem_penaltis        boolean not null default false,
    constraint uq_fase_ordem unique (edicao_id, ordem)
);

create table competicao_referencia_externa (
    id            bigint generated always as identity primary key,
    competicao_id bigint not null references competicao (id),
    fonte         text   not null check (fonte in ('EA_FC', 'TRANSFERMARKT')),
    id_externo    text   not null check (length(id_externo) between 1 and 60),
    constraint uq_competicao_referencia_externa unique (fonte, id_externo)
);

create index idx_competicao_referencia_externa_competicao on competicao_referencia_externa (competicao_id);
