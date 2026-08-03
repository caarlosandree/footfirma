package br.com.api.footfirma.avaliacao;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.avaliacao.dto.AvaliacaoDePosicao;
import br.com.api.footfirma.avaliacao.dto.ItemDeRanking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ConsultaDeOverallTest {

    @Autowired
    AvaliacaoService avaliacaoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepararSeisJogadoresEmpatados() {
        jdbcTemplate.update("delete from jogador_overall");
        jdbcTemplate.update("delete from jogador_atributo");
        jdbcTemplate.update("delete from jogador_vinculo");
        jdbcTemplate.update("delete from jogador");
        jdbcTemplate.update("""
                insert into temporada (label, ano_inicio, ano_fim) values ('2043', 2043, 2043)
                on conflict (label) do nothing
                """);
        // Todos com os mesmos atributos: o ranking sai inteiramente empatado, que é
        // exatamente a condição em que a paginação instável se manifesta.
        for (var indice = 1; indice <= 6; indice++) {
            inserirJogador("rank-" + indice);
        }
        avaliacaoService.materializar("2043");
    }

    @Test
    void deveDevolverOverallNasNovePosicoesOrdenadoDoMaiorParaOMenor() {
        var avaliacoes = avaliacaoService.buscarPorSlug("rank-1", "2043");

        assertThat(avaliacoes).isPresent();
        assertThat(avaliacoes.get().avaliacoes()).hasSize(9);
        assertThat(avaliacoes.get().avaliacoes())
                .extracting(AvaliacaoDePosicao::overall)
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void deveDevolverVazioQuandoOJogadorNaoExiste() {
        assertThat(avaliacaoService.buscarPorSlug("nao-existe", "2043")).isEmpty();
    }

    @Test
    void deveDevolverVazioQuandoATemporadaNaoExiste() {
        assertThat(avaliacaoService.buscarPorSlug("rank-1", "1900")).isEmpty();
    }

    @Test
    void deveRanquearJogadoresNaPosicaoPedida() {
        var pagina = avaliacaoService.ranquear("2043", "ATA", null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(6);
        assertThat(pagina.getContent()).extracting(ItemDeRanking::posicaoAvaliada).containsOnly("ATA");
        assertThat(pagina.getContent()).extracting(ItemDeRanking::nomeExibicao).doesNotContainNull();
    }

    // Com seis jogadores empatados, paginar sem desempate estável faria o mesmo
    // jogador aparecer duas vezes ou sumir.
    @Test
    void deveManterAPaginacaoEstavelQuandoTodosEstaoEmpatados() {
        var primeira = avaliacaoService.ranquear("2043", "ATA", null, PageRequest.of(0, 3));
        var segunda = avaliacaoService.ranquear("2043", "ATA", null, PageRequest.of(1, 3));

        var vistos = new ArrayList<String>();
        primeira.getContent().forEach(item -> vistos.add(item.slug()));
        segunda.getContent().forEach(item -> vistos.add(item.slug()));

        assertThat(vistos).hasSize(6);
        assertThat(vistos).doesNotHaveDuplicates();
    }

    @Test
    void deveFiltrarPeloOverallMinimo() {
        var comFiltroImpossivel = avaliacaoService.ranquear("2043", "ATA", 99, PageRequest.of(0, 10));

        assertThat(comFiltroImpossivel.getTotalElements()).isZero();
    }

    @Test
    void deveDevolverPaginaVaziaQuandoAPosicaoNaoExiste() {
        var pagina = avaliacaoService.ranquear("2043", "XXX", null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isZero();
    }

    private void inserirJogador(String slug) {
        var paisId = jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
        var posicaoId = jdbcTemplate.queryForObject(
                "select id from posicao where codigo = 'ATA'", Long.class);
        jdbcTemplate.update("""
                insert into jogador (slug, chave_natural, nome_completo, nome_exibicao, data_nascimento,
                                     pais_id, pe_preferido, posicao_principal_id, semente, origem)
                values (?, ?, ?, ?, date '1999-01-01', ?, 'DIREITO', ?, 1, 'REAL')
                """, slug, slug + "|1999-01-01|BRA", slug, slug, paisId, posicaoId);
        var jogadorId = jdbcTemplate.queryForObject(
                "select id from jogador where slug = ?", Long.class, slug);
        var temporadaId = jdbcTemplate.queryForObject(
                "select id from temporada where label = '2043'", Long.class);
        jdbcTemplate.update("""
                insert into jogador_atributo (
                    jogador_id, temporada_id,
                    ritmo, forca, folego, salto, agilidade,
                    passe, drible, cruzamento, frieza,
                    finalizacao, cabeceio, falta, penalti,
                    desarme, marcacao,
                    gol_reflexo, gol_posicionamento, gol_manejo,
                    potencial_base, potencial_variacao, fonte_atributo, coletado_em)
                values (?, ?, 80, 70, 78, 66, 80, 68, 79, 60, 77, 84, 62, 50, 70,
                        35, 30, 10, 10, 10, 90, 5, 'IMPORTADO', now())
                """, jogadorId, temporadaId);
    }
}
