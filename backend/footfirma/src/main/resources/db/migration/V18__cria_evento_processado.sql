-- Chave de idempotência do consumo de eventos, e a lacuna que a revisão do plano
-- encontrou no spec. @ApplicationModuleListener reenvia toda publicação não completada
-- na reinicialização: sem esta tabela, a moral de uma partida seria aplicada de novo a
-- cada restart e a campanha deixaria de ser reproduzível.
--
-- A chave é por vínculo, não por evento: uma PartidaEncerrada toca os dois clubes, e cada
-- lado tem seu próprio vínculo a atualizar. O mandante já ter sido processado não diz
-- nada sobre o visitante.
--
-- A tabela também é a contagem de partidas do vínculo, que a política de demissão usa
-- para a carência — uma linha por partida processada, sem coluna redundante para manter
-- em sincronia.
create table treinador_evento_processado (
    vinculo_id    bigint      not null references vinculo_treinador (id),
    chave_evento  text        not null check (length(chave_evento) between 1 and 120),
    processado_em timestamptz not null default now(),
    primary key (vinculo_id, chave_evento)
);

comment on column treinador_evento_processado.chave_evento is
    'Identidade do fato, derivada do próprio evento: edição, os dois clubes e o instante. Vira o id da partida quando o módulo partida existir';
