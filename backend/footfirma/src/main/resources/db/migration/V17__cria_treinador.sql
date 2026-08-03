-- Treinador é entidade de domínio, não conta de usuário. Os ~30 clubes sem humano têm
-- treinadores de IA com as mesmas skills, moral e vínculos — a simetria é o que dá ao
-- mercado de propostas candidatos de onde escolher. O elo entre pessoa e treinador é do
-- spec de autenticação; criar aqui uma coluna apontando para tabela inexistente seria
-- dívida disfarçada de preparo.
create table treinador (
    id                 bigint  generated always as identity primary key,
    slug               text    not null unique check (length(slug) between 2 and 80),
    nome_completo      text    not null check (length(nome_completo) between 1 and 160),
    nome_exibicao      text    not null check (length(nome_exibicao) between 1 and 80),
    data_nascimento    date    not null,
    pais_id            bigint  not null references pais (id),
    tipo               text    not null check (tipo in ('HUMANO', 'IA')),
    reputacao          integer not null default 50 check (reputacao between 0 and 99),
    pontos_disponiveis integer not null default 0 check (pontos_disponiveis >= 0),
    semente            bigint  not null,
    criado_em          timestamptz not null default now(),
    atualizado_em      timestamptz
);

create index idx_treinador_pais on treinador (pais_id);

-- Livres para o mercado: sem vínculo ativo. O índice cobre a varredura que o gerador de
-- propostas faz a cada vaga aberta.
create index idx_treinador_reputacao on treinador (reputacao desc, id);

comment on column treinador.reputacao is
    'Memória de carreira (0-99). Decide quais clubes fazem proposta e semeia a moral inicial do vínculo';
comment on column treinador.pontos_disponiveis is
    'Pontos ganhos ao fim da temporada e ainda não distribuídos entre as skills';
comment on column treinador.semente is
    'Semente determinística. Origem dos traços do treinador de IA e de qualquer sorteio ligado a ele';

-- Linha por skill, no formato de jogador_atributo e perfil_avaliacao_peso. Versionar por
-- temporada dá o histórico de evolução sem tabela de auditoria: comparar duas temporadas
-- é um select, não uma reconstrução.
create table treinador_skill (
    treinador_id bigint  not null references treinador (id),
    temporada_id bigint  not null references temporada (id),
    skill        text    not null check (skill in ('VISAO_DE_JOGO', 'PRELECAO', 'LIDERANCA',
                                                   'TREINAMENTO', 'TATICA', 'NEGOCIACAO')),
    valor        integer not null check (valor between 1 and 10),
    primary key (treinador_id, temporada_id, skill)
);

comment on table treinador_skill is
    'A soma das seis por temporada precisa bater com 20 + pontos ganhos. É invariante de agregado: check não enxerga outras linhas, então quem garante é TreinadorServiceImpl, coberto por SkillIntegridadeTest';

create table vinculo_treinador (
    id           bigint       generated always as identity primary key,
    treinador_id bigint       not null references treinador (id),
    clube_id     bigint       not null references clube (id),
    temporada_id bigint       not null references temporada (id),
    inicio       date         not null,
    fim          date,
    motivo_fim   text         check (motivo_fim in ('DEMISSAO', 'PEDIDO_DEMISSAO',
                                                    'FIM_DE_CONTRATO', 'ACEITOU_PROPOSTA')),
    moral        double precision not null check (moral between 0 and 99),
    meta_posicao integer      not null check (meta_posicao > 0),
    criado_em    timestamptz  not null default now(),
    constraint ck_vinculo_treinador_datas check (fim is null or fim >= inicio),
    constraint ck_vinculo_treinador_motivo check ((fim is null) = (motivo_fim is null))
);

-- Mesmo padrão de uq_perfil_avaliacao_ativo (V12): a integridade é do banco, não da
-- aplicação. Sem estes dois índices, uma corrida entre aceitar proposta e ser demitido
-- coloca dois treinadores no mesmo clube — e o mundo fica inconsistente em silêncio.
create unique index uq_vinculo_clube_ativo     on vinculo_treinador (clube_id)     where fim is null;
create unique index uq_vinculo_treinador_ativo on vinculo_treinador (treinador_id) where fim is null;

create index idx_vinculo_treinador_temporada on vinculo_treinador (temporada_id);

-- Ponto flutuante, não integer: os deltas por partida são fracionários (+5,9 · +0,8 ·
-- -3,2) e uma campanha tem 38 deles. Arredondar a cada aplicação acumularia erro de
-- dezenas de pontos na temporada, e a diferença entre +0,8 e +1 decidiria demissões.
comment on column vinculo_treinador.moral is
    'Relação com ESTE clube (0-99). Semeada pela reputação do treinador na assinatura, nunca 50 fixo';
comment on column vinculo_treinador.meta_posicao is
    'Expectativa congelada na contratação: rank do clube por reputação entre os participantes da edição';
comment on column vinculo_treinador.motivo_fim is
    'Preenchido junto com fim, garantido pelo check: vínculo encerrado sem motivo esconde por que a carreira virou';

create table proposta (
    id            bigint  generated always as identity primary key,
    clube_id      bigint  not null references clube (id),
    treinador_id  bigint  not null references treinador (id),
    temporada_id  bigint  not null references temporada (id),
    meta_posicao  integer not null check (meta_posicao > 0),
    status        text    not null default 'ABERTA'
                  check (status in ('ABERTA', 'ACEITA', 'RECUSADA', 'EXPIRADA')),
    criada_em     timestamptz not null default now(),
    expira_em     timestamptz not null,
    respondida_em timestamptz
);

create index idx_proposta_treinador_aberta on proposta (treinador_id) where status = 'ABERTA';

comment on column proposta.meta_posicao is
    'O que o clube vai cobrar, viajando na proposta: sabe-se o alvo antes de assinar, não depois';

-- Ancorada no vínculo: a relação morre quando a passagem acaba. O que sobrevive à troca
-- de clube é a afinidade, não o status declarado.
create table treinador_jogador (
    id                   bigint       generated always as identity primary key,
    vinculo_treinador_id bigint       not null references vinculo_treinador (id),
    jogador_id           bigint       not null references jogador (id),
    status_confianca     text         not null default 'ROTACAO'
                         check (status_confianca in ('INDISCUTIVEL', 'IMPORTANTE', 'ROTACAO',
                                                     'PROMESSA', 'FORA_DOS_PLANOS')),
    moral                double precision not null check (moral between 0 and 99),
    minutos_acumulados   integer      not null default 0 check (minutos_acumulados >= 0),
    atualizado_em        timestamptz,
    constraint uq_treinador_jogador unique (vinculo_treinador_id, jogador_id)
);

create index idx_treinador_jogador_jogador on treinador_jogador (jogador_id);

comment on column treinador_jogador.status_confianca is
    'Promessa quantificada: cada status implica minutos esperados por jogo, e é contra eles que a moral reage';

-- Ancorada no par, sem clube na chave: é a memória que atravessa carreiras. É o que faz
-- recontratar um ex-pupilo trazê-lo adiantado, sem nenhuma regra especial.
create table treinador_jogador_afinidade (
    treinador_id  bigint       not null references treinador (id),
    jogador_id    bigint       not null references jogador (id),
    afinidade     double precision not null default 50 check (afinidade between 0 and 99),
    jogos_juntos  integer      not null default 0 check (jogos_juntos >= 0),
    atualizado_em timestamptz,
    primary key (treinador_id, jogador_id)
);

comment on column treinador_jogador_afinidade.jogos_juntos is
    'Peso da consolidação: passagem de 3 jogos mexe pouco na afinidade, de 3 temporadas mexe muito';
