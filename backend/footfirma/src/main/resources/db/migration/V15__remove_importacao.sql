-- O importador existia para desconfiar de dado externo: manifesto, checksum e
-- ocorrência por linha recusada. Sem fonte externa não há do que desconfiar, e as
-- três tabelas de auditoria de carga perdem o sujeito.
--
-- A ordem é a inversa das dependências: ocorrência e contagem apontam para execução.
drop table if exists importacao_ocorrencia;
drop table if exists importacao_contagem;
drop table if exists importacao_execucao;
