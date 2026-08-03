-- País e unidade federativa são catálogo estável: não dependem do pipeline de
-- importação e por isso entram como seed versionado.
insert into pais (iso_code, nome, confederacao) values ('BRA', 'Brasil', 'CONMEBOL');

insert into estado (pais_id, uf, nome)
select p.id, dados.uf, dados.nome
from pais p,
     (values ('AC', 'Acre'), ('AL', 'Alagoas'), ('AP', 'Amapá'), ('AM', 'Amazonas'),
             ('BA', 'Bahia'), ('CE', 'Ceará'), ('DF', 'Distrito Federal'),
             ('ES', 'Espírito Santo'), ('GO', 'Goiás'), ('MA', 'Maranhão'),
             ('MT', 'Mato Grosso'), ('MS', 'Mato Grosso do Sul'), ('MG', 'Minas Gerais'),
             ('PA', 'Pará'), ('PB', 'Paraíba'), ('PR', 'Paraná'), ('PE', 'Pernambuco'),
             ('PI', 'Piauí'), ('RJ', 'Rio de Janeiro'), ('RN', 'Rio Grande do Norte'),
             ('RS', 'Rio Grande do Sul'), ('RO', 'Rondônia'), ('RR', 'Roraima'),
             ('SC', 'Santa Catarina'), ('SP', 'São Paulo'), ('SE', 'Sergipe'),
             ('TO', 'Tocantins')) as dados(uf, nome)
where p.iso_code = 'BRA';
