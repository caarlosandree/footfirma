-- A proposta passa a congelar a reputação do clube no instante em que foi feita.
--
-- É a decisão 8 do spec aplicada à contratação: a moral de assinatura sai de
-- clamp(50 + (rep_treinador - rep_clube) x 0,4), e sem congelar aqui ela dependeria de
-- quanto o clube vale no dia em que o treinador aceita — não no dia em que foi
-- procurado. Uma proposta feita em março não deve ser reprecificada em junho.
--
-- Congelar também evita que o módulo treinador tenha de consultar clube para escrever:
-- quem abre a vaga informa a reputação, e ela viaja na proposta junto da meta_posicao.
alter table proposta add column reputacao_clube integer not null default 50
    check (reputacao_clube between 0 and 99);

-- O default existe só para a tabela conseguir ganhar a coluna como not null. Nenhuma
-- linha foi escrita até aqui, e daqui em diante a reputação é obrigatória na inserção:
-- deixar 50 valer por omissão esconderia a falta do dado.
alter table proposta alter column reputacao_clube drop default;

comment on column proposta.reputacao_clube is
    'Reputação do clube congelada na abertura da vaga. É contra ela que a moral de assinatura é calculada, nunca contra o valor corrente';
