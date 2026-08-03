-- Características são as skills especiais importadas dos Traits/PlayStyles da
-- fonte externa. O efeito delas pertence ao motor de simulação; o catálogo só
-- as carrega.
create table caracteristica (
    id        bigint generated always as identity primary key,
    codigo    text   not null unique check (length(codigo) between 2 and 40),
    nome      text   not null check (length(nome) between 1 and 60),
    categoria text   not null check (categoria in ('ATAQUE', 'TECNICA', 'DEFESA', 'FISICO', 'MENTAL')),
    descricao text   not null check (length(descricao) between 1 and 240)
);

insert into caracteristica (codigo, nome, categoria, descricao) values
    ('DRIBLADOR',            'Driblador',              'TECNICA', 'Leva vantagem em duelos individuais com a bola dominada'),
    ('VOLEIO',               'Voleio',                 'ATAQUE',  'Finaliza bem antes de a bola tocar o chão'),
    ('BICICLETA',            'Bicicleta',              'ATAQUE',  'Tenta e converte finalizações acrobáticas'),
    ('CHUTE_DE_LONGE',       'Chute de longe',         'ATAQUE',  'Ameaça de fora da área'),
    ('CHUTE_COLOCADO',       'Chute colocado',         'ATAQUE',  'Prefere precisão a potência na finalização'),
    ('ESPECIALISTA_FALTA',   'Especialista em falta',  'ATAQUE',  'Cobrador de falta designado'),
    ('ESPECIALISTA_PENALTI', 'Especialista em pênalti','ATAQUE',  'Cobrador de pênalti designado'),
    ('ARMADOR',              'Armador',                'TECNICA', 'A equipe procura este jogador para construir as jogadas'),
    ('PASSE_LONGO',          'Passe longo',            'TECNICA', 'Inverte o jogo com precisão'),
    ('DESARME_LIMPO',        'Desarme limpo',          'DEFESA',  'Desarma com baixo risco de falta'),
    ('MARCADOR_AGRESSIVO',   'Marcador agressivo',     'DEFESA',  'Pressiona alto e disputa com intensidade'),
    ('CABECEIO_AEREO',       'Cabeceio aéreo',         'FISICO',  'Domina a bola aérea nas duas áreas'),
    ('LIDER',                'Líder',                  'MENTAL',  'Eleva a moral do elenco em campo');

create table jogador_caracteristica (
    jogador_id        bigint not null references jogador (id),
    caracteristica_id bigint not null references caracteristica (id),
    primary key (jogador_id, caracteristica_id)
);

create index idx_jogador_caracteristica_caracteristica on jogador_caracteristica (caracteristica_id);

-- O vínculo é o elenco por temporada. Um jogador pode ter mais de um vínculo na
-- mesma temporada (transferência no meio do ano), mas não dois no mesmo clube.
create table jogador_vinculo (
    id                bigint        generated always as identity primary key,
    jogador_id        bigint        not null references jogador (id),
    clube_id          bigint        not null references clube (id),
    temporada_id      bigint        not null references temporada (id),
    tipo              text          not null check (tipo in ('CONTRATO', 'EMPRESTIMO')),
    numero_camisa     integer       check (numero_camisa between 1 and 99),
    data_inicio       date,
    data_fim          date,
    valor_mercado_eur numeric(14,2) check (valor_mercado_eur >= 0),
    constraint uq_jogador_vinculo unique (jogador_id, temporada_id, clube_id),
    constraint ck_jogador_vinculo_datas check (data_fim is null or data_inicio is null or data_fim >= data_inicio)
);

create index idx_jogador_vinculo_clube_temporada on jogador_vinculo (clube_id, temporada_id);
create index idx_jogador_vinculo_jogador on jogador_vinculo (jogador_id);
