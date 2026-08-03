-- Versão 1 dos perfis de peso, uma por posição. Rebalancear no futuro é inserir
-- versao = 2 e desativar esta, nunca dar UPDATE nos pesos: carreiras em curso não
-- podem mudar de regra no meio.
insert into perfil_avaliacao (posicao_id, versao, ativo, vigente_desde)
select id, 1, true, date '2026-08-02' from posicao;

-- Os 85 pesos não nulos. Falta e Pênalti não aparecem em nenhum perfil por decisão
-- de design: são habilidades de especialista, usadas pelo motor de simulação para
-- escolher quem cobra, não medida de qualidade do jogador.
insert into perfil_avaliacao_peso (perfil_id, atributo, peso)
select p.id, v.atributo, v.peso
from perfil_avaliacao p
join posicao pos on pos.id = p.posicao_id
join (values
    ('GOL', 'SALTO', 0.02), ('GOL', 'AGILIDADE', 0.08), ('GOL', 'PASSE', 0.03),
    ('GOL', 'FRIEZA', 0.05), ('GOL', 'GOL_REFLEXO', 0.30),
    ('GOL', 'GOL_POSICIONAMENTO', 0.28), ('GOL', 'GOL_MANEJO', 0.24),

    ('ZAG', 'RITMO', 0.06), ('ZAG', 'FORCA', 0.14), ('ZAG', 'FOLEGO', 0.01),
    ('ZAG', 'SALTO', 0.09), ('ZAG', 'AGILIDADE', 0.02), ('ZAG', 'PASSE', 0.07),
    ('ZAG', 'FRIEZA', 0.04), ('ZAG', 'CABECEIO', 0.13), ('ZAG', 'DESARME', 0.20),
    ('ZAG', 'MARCACAO', 0.24),

    -- LTD e LTE têm pesos idênticos de propósito: a diferença entre lateral direito
    -- e esquerdo é pé preferido, que vive em jogador.pe_preferido, não conjunto de
    -- habilidades. Não é copy-paste por descuido.
    ('LTD', 'RITMO', 0.14), ('LTD', 'FORCA', 0.04), ('LTD', 'FOLEGO', 0.13),
    ('LTD', 'AGILIDADE', 0.07), ('LTD', 'PASSE', 0.10), ('LTD', 'DRIBLE', 0.06),
    ('LTD', 'CRUZAMENTO', 0.15), ('LTD', 'FRIEZA', 0.02), ('LTD', 'DESARME', 0.13),
    ('LTD', 'MARCACAO', 0.16),

    ('LTE', 'RITMO', 0.14), ('LTE', 'FORCA', 0.04), ('LTE', 'FOLEGO', 0.13),
    ('LTE', 'AGILIDADE', 0.07), ('LTE', 'PASSE', 0.10), ('LTE', 'DRIBLE', 0.06),
    ('LTE', 'CRUZAMENTO', 0.15), ('LTE', 'FRIEZA', 0.02), ('LTE', 'DESARME', 0.13),
    ('LTE', 'MARCACAO', 0.16),

    ('VOL', 'RITMO', 0.03), ('VOL', 'FORCA', 0.11), ('VOL', 'FOLEGO', 0.12),
    ('VOL', 'AGILIDADE', 0.04), ('VOL', 'PASSE', 0.17), ('VOL', 'DRIBLE', 0.02),
    ('VOL', 'FRIEZA', 0.07), ('VOL', 'CABECEIO', 0.05), ('VOL', 'DESARME', 0.19),
    ('VOL', 'MARCACAO', 0.20),

    ('MEC', 'RITMO', 0.05), ('MEC', 'FORCA', 0.03), ('MEC', 'FOLEGO', 0.09),
    ('MEC', 'AGILIDADE', 0.11), ('MEC', 'PASSE', 0.26), ('MEC', 'DRIBLE', 0.15),
    ('MEC', 'CRUZAMENTO', 0.07), ('MEC', 'FRIEZA', 0.13), ('MEC', 'FINALIZACAO', 0.07),
    ('MEC', 'DESARME', 0.04),

    ('MEA', 'RITMO', 0.09), ('MEA', 'FORCA', 0.03), ('MEA', 'FOLEGO', 0.05),
    ('MEA', 'AGILIDADE', 0.12), ('MEA', 'PASSE', 0.19), ('MEA', 'DRIBLE', 0.18),
    ('MEA', 'CRUZAMENTO', 0.07), ('MEA', 'FRIEZA', 0.14), ('MEA', 'FINALIZACAO', 0.13),

    ('PTA', 'RITMO', 0.20), ('PTA', 'FORCA', 0.03), ('PTA', 'FOLEGO', 0.05),
    ('PTA', 'AGILIDADE', 0.14), ('PTA', 'PASSE', 0.08), ('PTA', 'DRIBLE', 0.20),
    ('PTA', 'CRUZAMENTO', 0.12), ('PTA', 'FRIEZA', 0.07), ('PTA', 'FINALIZACAO', 0.11),

    ('ATA', 'RITMO', 0.14), ('ATA', 'FORCA', 0.08), ('ATA', 'FOLEGO', 0.03),
    ('ATA', 'SALTO', 0.05), ('ATA', 'AGILIDADE', 0.07), ('ATA', 'PASSE', 0.07),
    ('ATA', 'DRIBLE', 0.12), ('ATA', 'FRIEZA', 0.10), ('ATA', 'FINALIZACAO', 0.24),
    ('ATA', 'CABECEIO', 0.10)
) as v(codigo, atributo, peso) on v.codigo = pos.codigo
where p.versao = 1;

-- Completa com zero os atributos restantes de cada perfil. Todo perfil fica com as
-- 18 linhas: sem NULL e sem caso especial no calculador. Skills de goleiro existem
-- em jogador de linha, e vice-versa — apenas com peso 0.
insert into perfil_avaliacao_peso (perfil_id, atributo, peso)
select p.id, a.atributo, 0
from perfil_avaliacao p
cross join (values
    ('RITMO'), ('FORCA'), ('FOLEGO'), ('SALTO'), ('AGILIDADE'),
    ('PASSE'), ('DRIBLE'), ('CRUZAMENTO'), ('FRIEZA'),
    ('FINALIZACAO'), ('CABECEIO'), ('FALTA'), ('PENALTI'),
    ('DESARME'), ('MARCACAO'),
    ('GOL_REFLEXO'), ('GOL_POSICIONAMENTO'), ('GOL_MANEJO')
) as a(atributo)
where p.versao = 1
  and not exists (
      select 1 from perfil_avaliacao_peso w
      where w.perfil_id = p.id and w.atributo = a.atributo
  );
