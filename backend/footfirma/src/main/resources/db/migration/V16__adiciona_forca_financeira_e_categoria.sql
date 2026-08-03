-- Riqueza como índice 0-99, irmão de reputacao e qualidade_base. Orçamento em
-- valor monetário não entra enquanto não existir transferência que o gaste.
alter table clube add column forca_financeira integer not null default 50
    check (forca_financeira between 0 and 99);

comment on column clube.forca_financeira is
    'Capacidade financeira do clube (0-99). Alimenta o nível do elenco no gerador de mundo';

-- Base e profissional convivem na mesma tabela: o vínculo já é versionado por
-- temporada, e promover um garoto é gravar o vínculo do ano seguinte como
-- PROFISSIONAL, não migrar linha entre tabelas.
alter table jogador_vinculo add column categoria text not null default 'PROFISSIONAL'
    check (categoria in ('PROFISSIONAL', 'BASE'));

create index idx_jogador_vinculo_categoria on jogador_vinculo (clube_id, categoria);

-- 'IMPORTADO' e 'ESTIMADO' cobriam dado vindo de fora e dado inferido. Atributo
-- gerado não é nenhum dos dois: não foi importado de lugar nenhum nem estimado a
-- partir de observação. 'IMPORTADO' permanece no conjunto porque linhas antigas
-- ainda o usam — o check é sobre o que existe, não sobre o que deveria existir.
alter table jogador_atributo drop constraint jogador_atributo_fonte_atributo_check;
alter table jogador_atributo add constraint jogador_atributo_fonte_atributo_check
    check (fonte_atributo in ('IMPORTADO', 'ESTIMADO', 'GERADO'));
