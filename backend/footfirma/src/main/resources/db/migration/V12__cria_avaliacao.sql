create table perfil_avaliacao (
    id            bigint  generated always as identity primary key,
    posicao_id    bigint  not null references posicao (id),
    versao        integer not null check (versao > 0),
    ativo         boolean not null default false,
    vigente_desde date    not null,
    constraint uq_perfil_avaliacao unique (posicao_id, versao)
);

-- Um único perfil ativo por posição, garantido pelo banco e não por convenção.
create unique index uq_perfil_avaliacao_ativo
    on perfil_avaliacao (posicao_id) where ativo;

comment on column perfil_avaliacao.vigente_desde is
    'Informativa: registra quando esta versão entrou em uso. A seleção do perfil é por ativo, nunca por data';

-- Rebalancear cria versão nova; nunca UPDATE. Sem isso não há como comparar o
-- equilíbrio antes e depois, nem impedir que uma carreira mude de regra no meio.
create table perfil_avaliacao_peso (
    perfil_id bigint       not null references perfil_avaliacao (id),
    atributo  text         not null check (atributo in ('RITMO','FORCA','FOLEGO','SALTO','AGILIDADE',
                                                        'PASSE','DRIBLE','CRUZAMENTO','FRIEZA',
                                                        'FINALIZACAO','CABECEIO','FALTA','PENALTI',
                                                        'DESARME','MARCACAO',
                                                        'GOL_REFLEXO','GOL_POSICIONAMENTO','GOL_MANEJO')),
    peso      numeric(5,4) not null check (peso >= 0 and peso <= 1),
    primary key (perfil_id, atributo)
);

-- Nove linhas por jogador e temporada, uma por posição — inclusive as que ele não
-- joga. É o que responde "esse volante serve como zagueiro?" sem cálculo extra.
create table jogador_overall (
    id            bigint      generated always as identity primary key,
    jogador_id    bigint      not null references jogador (id),
    temporada_id  bigint      not null references temporada (id),
    posicao_id    bigint      not null references posicao (id),
    perfil_versao integer     not null,
    overall       integer     not null check (overall between 0 and 99),
    calculado_em  timestamptz not null,
    constraint uq_jogador_overall unique (jogador_id, temporada_id, posicao_id)
);

-- jogador_id entra no índice porque o ORDER BY do ranking precisa dele como
-- desempate estável: só por overall, a paginação com empates não é determinística.
create index idx_jogador_overall_ranking
    on jogador_overall (temporada_id, posicao_id, overall desc, jogador_id);

comment on column jogador_overall.perfil_versao is
    'Versão do perfil usada neste cálculo. Gravada, nunca inferida: sem ela o versionamento de perfil perde a função';
