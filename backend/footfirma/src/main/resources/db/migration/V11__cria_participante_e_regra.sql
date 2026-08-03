create table edicao_participante (
    id            bigint  generated always as identity primary key,
    edicao_id     bigint  not null references edicao (id),
    clube_id      bigint  not null references clube (id),
    posicao_final integer check (posicao_final > 0),
    constraint uq_edicao_participante unique (edicao_id, clube_id)
);

create index idx_edicao_participante_clube on edicao_participante (clube_id);

-- Regra de classificação é dado, não código: acesso, rebaixamento e vaga
-- continental viram linhas. Um campeonato com formato novo é um INSERT.
create table regra_classificacao (
    id                    bigint  generated always as identity primary key,
    edicao_id             bigint  not null references edicao (id),
    posicao_inicio        integer not null check (posicao_inicio > 0),
    posicao_fim           integer not null check (posicao_fim > 0),
    tipo                  text    not null check (tipo in ('ACESSO', 'REBAIXAMENTO', 'LIBERTADORES_GRUPOS', 'LIBERTADORES_PRE', 'SULAMERICANA')),
    competicao_destino_id bigint  references competicao (id),
    constraint ck_regra_classificacao_faixa check (posicao_fim >= posicao_inicio),
    constraint uq_regra_classificacao unique (edicao_id, posicao_inicio, posicao_fim, tipo)
);

create index idx_regra_classificacao_edicao on regra_classificacao (edicao_id);
