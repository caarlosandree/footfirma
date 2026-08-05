-- Rodada, confronto e jogo. É a primeira coisa do sistema que muda durante a temporada:
-- competicao é catálogo, escrito uma vez pelo gerador e lido pelo resto; aqui o placar é
-- reescrito a cada partida. Por isso módulo próprio, e não dentro de competicao.
create table rodada (
    id            bigint  generated always as identity primary key,
    fase_id       bigint  not null references fase (id),
    ordem         integer not null check (ordem > 0),
    tipo          text    not null check (tipo in ('FIM_DE_SEMANA', 'MEIO_DE_SEMANA')),
    data_alvo     date    not null,
    janela_inicio date    not null,
    janela_fim    date    not null,
    constraint ck_rodada_janela check (janela_inicio <= data_alvo and data_alvo <= janela_fim)
);

create unique index uq_rodada_ordem on rodada (fase_id, ordem);

comment on column rodada.tipo is
    'FIM_DE_SEMANA ou MEIO_DE_SEMANA. Decide o leque de dias em que os jogos podem cair';
comment on column rodada.data_alvo is
    'Âncora da rodada: domingo no fim de semana, quarta no meio de semana';
comment on constraint ck_rodada_janela on rodada is
    'O alvo mora dentro da própria janela. Fora dela, o alocador procuraria dias que a rodada não cobre';

-- O confronto é o slot do chaveamento; o jogo é a partida concreta. Em pontos corridos a
-- distinção parece burocracia, mas em mata-mata de ida e volta quem avança se decide pela
-- soma de dois jogos, com gol fora e pênaltis — e isso não tem onde morar se só houver jogo.
create table confronto (
    id                bigint  generated always as identity primary key,
    fase_id           bigint  not null references fase (id),
    ordem             integer not null check (ordem > 0),
    chave             text,
    origem_lado_a     bigint  references confronto (id),
    origem_lado_b     bigint  references confronto (id),
    clube_a_id        bigint  references clube (id),
    clube_b_id        bigint  references clube (id),
    vencedor_clube_id bigint  references clube (id),
    constraint ck_confronto_origens_distintas check (
        origem_lado_a is null or origem_lado_b is null or origem_lado_a <> origem_lado_b
    )
);

create unique index uq_confronto_ordem on confronto (fase_id, ordem);

-- A propagação do chaveamento busca confrontos por origem a cada confronto resolvido.
-- FK usada em filtro tem índice; o Postgres não o cria sozinho.
create index idx_confronto_origem_a on confronto (origem_lado_a) where origem_lado_a is not null;
create index idx_confronto_origem_b on confronto (origem_lado_b) where origem_lado_b is not null;

comment on column confronto.chave is
    'Rótulo do grupo (A, B, …) em fase de grupos; nulo em pontos corridos e eliminatória';
comment on column confronto.origem_lado_a is
    'Confronto que alimenta este lado. Nulo quando o clube veio do sorteio inicial';
comment on column confronto.clube_a_id is
    'Nulo até a fase anterior resolver. Em pontos corridos e grupos, nunca nulo';
comment on column confronto.vencedor_clube_id is
    'Só em fase ELIMINATORIA. Em pontos corridos e grupos fica nulo para sempre: quem avança ali é decidido por classificação, que não é deste módulo';

create table jogo (
    id                  bigint  generated always as identity primary key,
    confronto_id        bigint  not null references confronto (id),
    rodada_id           bigint  not null references rodada (id),
    ordem_no_confronto  integer not null check (ordem_no_confronto between 1 and 2),
    mandante_id         bigint  references clube (id),
    visitante_id        bigint  references clube (id),
    estadio_id          bigint  references estadio (id),
    data_jogo           date,
    situacao            text    not null default 'AGENDADO'
                        check (situacao in ('AGENDADO', 'ENCERRADO')),
    gols_mandante              integer check (gols_mandante              >= 0),
    gols_visitante             integer check (gols_visitante             >= 0),
    gols_mandante_prorrogacao  integer check (gols_mandante_prorrogacao  >= 0),
    gols_visitante_prorrogacao integer check (gols_visitante_prorrogacao >= 0),
    penaltis_mandante          integer check (penaltis_mandante          >= 0),
    penaltis_visitante         integer check (penaltis_visitante         >= 0),

    constraint ck_jogo_clubes_distintos check (mandante_id <> visitante_id)
);

create unique index uq_jogo_ordem on jogo (confronto_id, ordem_no_confronto);
create index idx_jogo_rodada    on jogo (rodada_id);
create index idx_jogo_mandante  on jogo (mandante_id, data_jogo);
create index idx_jogo_visitante on jogo (visitante_id, data_jogo);

comment on column jogo.estadio_id is
    'Gravado do mandante no momento em que o jogo ganha seus clubes, nunca inferido depois: o clube pode trocar de estádio, e o jogo passado aconteceu onde aconteceu';
comment on column jogo.data_jogo is
    'Provisória enquanto o confronto não tem os dois clubes. Realocada pela regra de descanso quando eles se definem';
comment on column jogo.situacao is
    'AGENDADO ou ENCERRADO. Não há ADIADO: jogo fora da janela da rodada é apenas um jogo em outro dia, e ninguém o adia por decisão externa neste sistema';
comment on column jogo.ordem_no_confronto is
    '1 ou 2, conforme fase.jogos_por_confronto. A faixa é o domínio de hoje: confronto de três jogos exigiria migration, e é essa a intenção — que a mudança seja deliberada';
comment on constraint ck_jogo_clubes_distintos on jogo is
    'Bloqueia clube contra si mesmo. Passa de propósito quando um dos lados é nulo: em eliminatória o jogo nasce sem clubes, e no Postgres null <> null é null, que o check trata como aprovado. Não é falha da constraint — é o caso pré-classificação sendo aceito';
