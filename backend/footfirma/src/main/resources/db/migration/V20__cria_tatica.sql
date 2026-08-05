-- Uma formação é uma lista de 11 posições, nada além disso. É catálogo estável, como
-- posicao e caracteristica, e por isso mora no mesmo módulo do estado que a consome:
-- um módulo Modulith inteiro para hospedar um seed não se paga.
create table formacao (
    id     bigint  generated always as identity primary key,
    codigo text    not null unique check (length(codigo) between 3 and 12),
    nome   text    not null check (length(nome) between 1 and 60),
    ordem  integer not null unique check (ordem > 0)
);

comment on column formacao.ordem is
    'Ordem do repertório: o escalador de IA avalia as 1 + TATICA/2 primeiras, então a de ordem 1 precisa ser a mais genérica';

create table formacao_slot (
    formacao_id bigint  not null references formacao (id),
    ordem       integer not null check (ordem between 1 and 11),
    posicao_id  bigint  not null references posicao (id),
    primary key (formacao_id, ordem)
);

create index idx_formacao_slot_posicao on formacao_slot (posicao_id);

comment on table formacao_slot is
    'Exatamente 11 slots e exatamente um GOL por formação são invariantes do seed, garantidas por CatalogoDeFormacaoTest — check de linha não enxerga as outras linhas';

-- O plano é do clube, não do vínculo do treinador nem da partida. Trocar de treinador
-- não apaga a escalação que estava lá, e partida ainda não existe para receber FK.
create table plano_tatico (
    id              bigint  generated always as identity primary key,
    clube_id        bigint  not null references clube (id),
    temporada_id    bigint  not null references temporada (id),
    versao          integer not null check (versao > 0),
    vigente         boolean not null default true,
    formacao_id     bigint  not null references formacao (id),
    capitao_id      bigint  references jogador (id),
    origem          text    not null check (origem in ('MANUAL', 'AUTOMATICO')),
    mentalidade     text    not null check (mentalidade in ('MUITO_DEFENSIVA', 'DEFENSIVA',
                                                            'EQUILIBRADA', 'OFENSIVA',
                                                            'MUITO_OFENSIVA')),
    ritmo           text    not null check (ritmo in ('LENTO', 'EQUILIBRADO', 'INTENSO')),
    linha_defensiva text    not null check (linha_defensiva in ('RECUADA', 'MEDIA', 'ADIANTADA')),
    pressao         text    not null check (pressao in ('BAIXA', 'MEDIA', 'ALTA')),
    largura         text    not null check (largura in ('ESTREITA', 'MEDIA', 'ABERTA')),
    criado_em       timestamptz not null default now(),
    constraint uq_plano_versao unique (clube_id, temporada_id, versao)
);

-- Alterar é versionar: marca-se o vigente como false e insere-se a versão seguinte.
-- Este índice é o que garante, no banco, que nem concorrência produz dois vigentes.
create unique index uq_plano_vigente on plano_tatico (clube_id, temporada_id) where vigente;

create index idx_plano_tatico_clube_temporada on plano_tatico (clube_id, temporada_id, versao desc);

comment on column plano_tatico.origem is
    'MANUAL quando o treinador escalou; AUTOMATICO quando o escalador preencheu';

create table plano_escalacao (
    plano_id           bigint  not null references plano_tatico (id),
    jogador_id         bigint  not null references jogador (id),
    papel              text    not null check (papel in ('TITULAR', 'RESERVA')),
    slot_ordem         integer check (slot_ordem  between 1 and 11),
    ordem_banco        integer check (ordem_banco between 1 and 12),
    posicao_id         bigint  not null references posicao (id),
    aptidao_no_momento integer not null check (aptidao_no_momento between 0 and 99),
    primary key (plano_id, jogador_id),
    constraint ck_papel_coerente check (
        (papel = 'TITULAR' and slot_ordem is not null and ordem_banco is null) or
        (papel = 'RESERVA' and slot_ordem is null     and ordem_banco is not null)
    )
);

create unique index uq_escalacao_slot on plano_escalacao (plano_id, slot_ordem)
    where slot_ordem is not null;

create unique index uq_escalacao_banco on plano_escalacao (plano_id, ordem_banco)
    where ordem_banco is not null;

create index idx_plano_escalacao_jogador on plano_escalacao (jogador_id);

comment on column plano_escalacao.posicao_id is
    'Em que posição o jogador foi escalado. Do slot da formação, se titular; de jogador.posicao_principal_id, se reserva. Gravada e nunca inferida: é a base do congelamento e do recálculo';
comment on column plano_escalacao.aptidao_no_momento is
    'Overall do jogador em posicao_id, congelado na gravação. O valor atual vem do join com jogador_overall na leitura';
