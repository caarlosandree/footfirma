package br.com.api.footfirma.treinador;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.treinador.domain.StatusConfianca;
import br.com.api.footfirma.treinador.domain.Treinador;
import br.com.api.footfirma.treinador.domain.TreinadorJogador;
import br.com.api.footfirma.treinador.domain.VinculoTreinador;
import br.com.api.footfirma.treinador.repository.AfinidadeRepository;
import br.com.api.footfirma.treinador.repository.EventoProcessadoRepository;
import br.com.api.footfirma.treinador.repository.TreinadorJogadorRepository;
import br.com.api.footfirma.treinador.repository.TreinadorRepository;
import br.com.api.footfirma.treinador.repository.TreinadorSkillRepository;
import br.com.api.footfirma.treinador.repository.VinculoTreinadorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * O mundo mínimo que os testes do módulo precisam: país, clube, temporada, jogador e um
 * vínculo ativo.
 *
 * <p>Clube, temporada e jogador entram por SQL cru de propósito — são tabelas de outros
 * módulos, e o módulo {@code treinador} não pode alcançar as entidades delas. É a mesma
 * fronteira que faz {@code vinculo_treinador.clube_id} ser {@code Long}, não
 * {@code @ManyToOne}.
 *
 * <p>{@code @Transactional} faz cada teste terminar em rollback, então os slugs podem se
 * repetir entre classes sem colidir nos índices únicos.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
public abstract class CenarioDeTreinador {

    @Autowired
    protected TreinadorRepository treinadores;

    @Autowired
    protected VinculoTreinadorRepository vinculos;

    @Autowired
    protected TreinadorJogadorRepository confiancas;

    @Autowired
    protected TreinadorSkillRepository skills;

    @Autowired
    protected AfinidadeRepository afinidades;

    @Autowired
    protected EventoProcessadoRepository processados;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected Treinador treinador(String slug) {
        return treinadores.save(TreinadorFactory.humano(slug, paisBrasil()));
    }

    protected VinculoTreinador vinculo(Treinador treinador, Long clubeId,
                                       double moral, int metaPosicao) {
        return vinculos.save(TreinadorFactory.vinculoComMoral(
                treinador, clubeId, temporada(), moral, metaPosicao));
    }

    protected TreinadorJogador elenco(VinculoTreinador vinculo, Long jogadorId,
                                      StatusConfianca status, double moral) {
        var relacao = new TreinadorJogador(vinculo, jogadorId, moral);
        relacao.setStatusConfianca(status);
        return confiancas.save(relacao);
    }

    protected double moralDe(VinculoTreinador vinculo) {
        return recarregar(vinculo).getMoral();
    }

    protected VinculoTreinador recarregar(VinculoTreinador vinculo) {
        return vinculos.findById(vinculo.getId()).orElseThrow();
    }

    /** Marca partidas já processadas sem passar pelo ouvinte, para pular a carência. */
    protected void comPartidasJogadas(VinculoTreinador vinculo, int quantidade) {
        for (var partida = 0; partida < quantidade; partida++) {
            jdbcTemplate.update("""
                    insert into treinador_evento_processado (vinculo_id, chave_evento)
                    values (?, ?)
                    """, vinculo.getId(), "carencia-" + partida);
        }
    }

    protected Long clube(String slug, int reputacao) {
        jdbcTemplate.update("""
                insert into clube (slug, nome_oficial, nome_curto, pais_id, reputacao)
                values (?, ?, ?, ?, ?)
                on conflict (slug) do update set reputacao = excluded.reputacao
                """, slug, slug + " oficial", slug, paisBrasil(), reputacao);
        return jdbcTemplate.queryForObject("select id from clube where slug = ?", Long.class, slug);
    }

    protected Long jogador(String slug) {
        jdbcTemplate.update("""
                insert into jogador (slug, chave_natural, nome_completo, nome_exibicao,
                                     data_nascimento, pais_id, pe_preferido,
                                     posicao_principal_id, semente, origem)
                values (?, ?, ?, ?, date '2000-01-01', ?, 'DIREITO',
                        (select id from posicao where codigo = 'ATA'), 1, 'GERADO')
                on conflict (slug) do nothing
                """, slug, "natural-" + slug, "Jogador " + slug, slug, paisBrasil());
        return jdbcTemplate.queryForObject("select id from jogador where slug = ?", Long.class, slug);
    }

    protected Long temporada() {
        jdbcTemplate.update("""
                insert into temporada (label, ano_inicio, ano_fim) values ('2026', 2026, 2026)
                on conflict (label) do nothing
                """);
        return jdbcTemplate.queryForObject(
                "select id from temporada where label = '2026'", Long.class);
    }

    protected Long paisBrasil() {
        return jdbcTemplate.queryForObject(
                "select id from pais where iso_code = 'BRA'", Long.class);
    }
}
