create table posicao (
    id     bigint  generated always as identity primary key,
    codigo text    not null unique check (codigo in ('GOL', 'ZAG', 'LTD', 'LTE', 'VOL', 'MEC', 'MEA', 'PTA', 'ATA')),
    nome   text    not null check (length(nome) between 1 and 40),
    setor  text    not null check (setor in ('GOLEIRO', 'DEFESA', 'MEIO', 'ATAQUE')),
    ordem  integer not null unique check (ordem > 0)
);

create table jogador (
    id                       bigint      generated always as identity primary key,
    slug                     text        not null unique check (length(slug) between 2 and 120),
    chave_natural            text        not null unique check (length(chave_natural) between 5 and 220),
    nome_completo            text        not null check (length(nome_completo) between 1 and 160),
    nome_exibicao            text        not null check (length(nome_exibicao) between 1 and 80),
    data_nascimento          date        not null,
    pais_id                  bigint      not null references pais (id),
    segunda_nacionalidade_id bigint      references pais (id),
    altura_cm                integer     check (altura_cm between 140 and 230),
    peso_kg                  integer     check (peso_kg between 40 and 140),
    pe_preferido             text        not null check (pe_preferido in ('DIREITO', 'ESQUERDO', 'AMBIDESTRO')),
    posicao_principal_id     bigint      not null references posicao (id),
    semente                  bigint      not null,
    origem                   text        not null check (origem in ('REAL', 'GERADO')),
    criado_em                timestamptz not null default now(),
    atualizado_em            timestamptz
);

create index idx_jogador_pais on jogador (pais_id);
create index idx_jogador_posicao_principal on jogador (posicao_principal_id);

comment on column jogador.chave_natural is
    'normalizar(nome_completo)|data_nascimento|iso_pais — chave de idempotência do importador';
comment on column jogador.semente is
    'Semente determinística derivada da chave natural. Origem de todo atributo oculto e do ruído de crescimento';

create table jogador_posicao (
    jogador_id bigint  not null references jogador (id),
    posicao_id bigint  not null references posicao (id),
    ordem      integer not null check (ordem between 1 and 5),
    primary key (jogador_id, posicao_id)
);

create index idx_jogador_posicao_posicao on jogador_posicao (posicao_id);

create table jogador_referencia_externa (
    id         bigint generated always as identity primary key,
    jogador_id bigint not null references jogador (id),
    fonte      text   not null check (fonte in ('EA_FC', 'TRANSFERMARKT')),
    id_externo text   not null check (length(id_externo) between 1 and 60),
    constraint uq_jogador_referencia_externa unique (fonte, id_externo)
);

create index idx_jogador_referencia_externa_jogador on jogador_referencia_externa (jogador_id);
