-- Atributos são versionados por temporada: o mesmo jogador em 2025 e 2026 são
-- linhas distintas. Sem isso, recarregar a base no ano seguinte destrói o
-- histórico de que o modelo de progressão depende.
create table jogador_atributo (
    id                  bigint      generated always as identity primary key,
    jogador_id          bigint      not null references jogador (id),
    temporada_id        bigint      not null references temporada (id),

    ritmo               integer     not null check (ritmo between 0 and 99),
    forca               integer     not null check (forca between 0 and 99),
    folego              integer     not null check (folego between 0 and 99),
    salto               integer     not null check (salto between 0 and 99),
    agilidade           integer     not null check (agilidade between 0 and 99),

    passe               integer     not null check (passe between 0 and 99),
    drible              integer     not null check (drible between 0 and 99),
    cruzamento          integer     not null check (cruzamento between 0 and 99),
    frieza              integer     not null check (frieza between 0 and 99),

    finalizacao         integer     not null check (finalizacao between 0 and 99),
    cabeceio            integer     not null check (cabeceio between 0 and 99),
    falta               integer     not null check (falta between 0 and 99),
    penalti             integer     not null check (penalti between 0 and 99),

    desarme             integer     not null check (desarme between 0 and 99),
    marcacao            integer     not null check (marcacao between 0 and 99),

    gol_reflexo         integer     not null check (gol_reflexo between 0 and 99),
    gol_posicionamento  integer     not null check (gol_posicionamento between 0 and 99),
    gol_manejo          integer     not null check (gol_manejo between 0 and 99),

    potencial_base      integer     not null check (potencial_base between 0 and 99),
    potencial_variacao  integer     not null default 0 check (potencial_variacao between 0 and 20),
    fonte_atributo      text        not null check (fonte_atributo in ('IMPORTADO', 'ESTIMADO')),
    coletado_em         timestamptz not null,

    constraint uq_jogador_atributo unique (jogador_id, temporada_id)
);

create index idx_jogador_atributo_temporada on jogador_atributo (temporada_id);

comment on column jogador_atributo.fonte_atributo is
    'ESTIMADO marca dado derivado de idade/posição/valor quando a fonte não cobre o jogador. Dado inventado fica marcado como inventado';
comment on column jogador_atributo.potencial_variacao is
    'Amplitude da banda de potencial. A carreira sorteia o teto real dentro de potencial_base +/- esta variação';

-- Atributos ocultos são estáveis ao longo da carreira e gerados a partir da
-- semente do jogador, nunca a partir de juízo sobre a pessoa real.
create table jogador_atributo_oculto (
    jogador_id          bigint  primary key references jogador (id),
    profissionalismo    integer not null check (profissionalismo between 0 and 99),
    ambicao             integer not null check (ambicao between 0 and 99),
    lealdade            integer not null check (lealdade between 0 and 99),
    temperamento        integer not null check (temperamento between 0 and 99),
    lideranca           integer not null check (lideranca between 0 and 99),
    regularidade        integer not null check (regularidade between 0 and 99),
    propensao_lesao     integer not null check (propensao_lesao between 0 and 99),
    resistencia_pressao integer not null check (resistencia_pressao between 0 and 99)
);
