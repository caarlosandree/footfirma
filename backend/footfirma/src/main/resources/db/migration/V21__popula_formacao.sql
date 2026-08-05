-- Seis formações. A ordem é o repertório: EscolhedorDeFormacao avalia as
-- 1 + TATICA/2 primeiras, então a de ordem 1 precisa ser a mais genérica.
insert into formacao (codigo, nome, ordem) values
    ('4-4-2',   'Quatro-quatro-dois',  1),
    ('4-3-3',   'Quatro-três-três',    2),
    ('4-2-3-1', 'Quatro-dois-três-um', 3),
    ('3-5-2',   'Três-cinco-dois',     4),
    ('5-3-2',   'Cinco-três-dois',     5),
    ('4-1-4-1', 'Quatro-um-quatro-um', 6);

insert into formacao_slot (formacao_id, ordem, posicao_id)
select f.id, s.ordem, p.id
from (values
    ('4-4-2',    1, 'GOL'), ('4-4-2',    2, 'ZAG'), ('4-4-2',    3, 'ZAG'),
    ('4-4-2',    4, 'LTD'), ('4-4-2',    5, 'LTE'), ('4-4-2',    6, 'VOL'),
    ('4-4-2',    7, 'MEC'), ('4-4-2',    8, 'PTA'), ('4-4-2',    9, 'PTA'),
    ('4-4-2',   10, 'ATA'), ('4-4-2',   11, 'ATA'),

    ('4-3-3',    1, 'GOL'), ('4-3-3',    2, 'ZAG'), ('4-3-3',    3, 'ZAG'),
    ('4-3-3',    4, 'LTD'), ('4-3-3',    5, 'LTE'), ('4-3-3',    6, 'VOL'),
    ('4-3-3',    7, 'MEC'), ('4-3-3',    8, 'MEC'), ('4-3-3',    9, 'PTA'),
    ('4-3-3',   10, 'PTA'), ('4-3-3',   11, 'ATA'),

    ('4-2-3-1',  1, 'GOL'), ('4-2-3-1',  2, 'ZAG'), ('4-2-3-1',  3, 'ZAG'),
    ('4-2-3-1',  4, 'LTD'), ('4-2-3-1',  5, 'LTE'), ('4-2-3-1',  6, 'VOL'),
    ('4-2-3-1',  7, 'VOL'), ('4-2-3-1',  8, 'MEA'), ('4-2-3-1',  9, 'PTA'),
    ('4-2-3-1', 10, 'PTA'), ('4-2-3-1', 11, 'ATA'),

    ('3-5-2',    1, 'GOL'), ('3-5-2',    2, 'ZAG'), ('3-5-2',    3, 'ZAG'),
    ('3-5-2',    4, 'ZAG'), ('3-5-2',    5, 'LTD'), ('3-5-2',    6, 'LTE'),
    ('3-5-2',    7, 'VOL'), ('3-5-2',    8, 'MEC'), ('3-5-2',    9, 'MEA'),
    ('3-5-2',   10, 'ATA'), ('3-5-2',   11, 'ATA'),

    ('5-3-2',    1, 'GOL'), ('5-3-2',    2, 'ZAG'), ('5-3-2',    3, 'ZAG'),
    ('5-3-2',    4, 'ZAG'), ('5-3-2',    5, 'LTD'), ('5-3-2',    6, 'LTE'),
    ('5-3-2',    7, 'VOL'), ('5-3-2',    8, 'MEC'), ('5-3-2',    9, 'MEC'),
    ('5-3-2',   10, 'ATA'), ('5-3-2',   11, 'ATA'),

    ('4-1-4-1',  1, 'GOL'), ('4-1-4-1',  2, 'ZAG'), ('4-1-4-1',  3, 'ZAG'),
    ('4-1-4-1',  4, 'LTD'), ('4-1-4-1',  5, 'LTE'), ('4-1-4-1',  6, 'VOL'),
    ('4-1-4-1',  7, 'MEC'), ('4-1-4-1',  8, 'MEC'), ('4-1-4-1',  9, 'PTA'),
    ('4-1-4-1', 10, 'PTA'), ('4-1-4-1', 11, 'ATA')
) as s(codigo, ordem, posicao)
join formacao f on f.codigo = s.codigo
join posicao  p on p.codigo = s.posicao;
