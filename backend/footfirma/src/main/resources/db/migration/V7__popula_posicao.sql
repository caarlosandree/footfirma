-- As 9 posições são catálogo fixo: o modelo de overall do Plano 2 tem um perfil
-- de pesos por posição, e o conjunto não muda com importação de dados.
insert into posicao (codigo, nome, setor, ordem) values
    ('GOL', 'Goleiro',           'GOLEIRO', 1),
    ('ZAG', 'Zagueiro',          'DEFESA',  2),
    ('LTD', 'Lateral direito',   'DEFESA',  3),
    ('LTE', 'Lateral esquerdo',  'DEFESA',  4),
    ('VOL', 'Volante',           'MEIO',    5),
    ('MEC', 'Meia central',      'MEIO',    6),
    ('MEA', 'Meia atacante',     'MEIO',    7),
    ('PTA', 'Ponta',             'ATAQUE',  8),
    ('ATA', 'Atacante',          'ATAQUE',  9);
