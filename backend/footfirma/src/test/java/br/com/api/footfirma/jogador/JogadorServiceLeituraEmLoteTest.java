package br.com.api.footfirma.jogador;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.jogador.dto.JogadorResumo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JogadorServiceLeituraEmLoteTest {

    @Autowired
    JogadorService jogadorService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepararTresJogadoresComAtributos() {
        // Tudo que referencia jogador vem primeiro: os testes @SpringBootTest não
        // fazem rollback, e outra classe pode ter deixado linhas para trás. A lista
        // precisa cobrir as seis tabelas dependentes — desde que o importador existe,
        // jogador_atributo_oculto, _caracteristica e _posicao também são populadas.
        jdbcTemplate.update("delete from jogador_overall");
        jdbcTemplate.update("delete from jogador_atributo");
        jdbcTemplate.update("delete from jogador_vinculo");
        jdbcTemplate.update("delete from jogador_atributo_oculto");
        jdbcTemplate.update("delete from jogador_caracteristica");
        jdbcTemplate.update("delete from jogador_posicao");
        jdbcTemplate.update("delete from jogador");
        jdbcTemplate.update("""
                insert into temporada (label, ano_inicio, ano_fim) values ('2040', 2040, 2040)
                on conflict (label) do nothing
                """);
        for (var indice = 1; indice <= 3; indice++) {
            inserirJogador("lote-" + indice, "Jogador Lote " + indice);
        }
    }

    @Test
    void deveDevolverAtributosPaginadosDaTemporada() {
        var primeiraPagina = jogadorService.listarAtributosPorTemporada(
                "2040", PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "jogador.id")));

        assertThat(primeiraPagina.getTotalElements()).isEqualTo(3);
        assertThat(primeiraPagina.getContent()).hasSize(2);
        assertThat(primeiraPagina.getContent().getFirst().atributos().finalizacao()).isEqualTo(70);
        assertThat(primeiraPagina.getContent().getFirst().jogadorId()).isNotNull();
    }

    @Test
    void deveDevolverPaginaVaziaQuandoTemporadaNaoExiste() {
        var pagina = jogadorService.listarAtributosPorTemporada("1900", PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isZero();
    }

    @Test
    void deveResolverVariosJogadoresEmUmaChamadaSo() {
        var ids = jdbcTemplate.queryForList("select id from jogador order by id", Long.class);

        var resumos = jogadorService.listarResumosPorIds(ids, "2040");

        assertThat(resumos).hasSize(3);
        assertThat(resumos).extracting(JogadorResumo::slug)
                .containsExactlyInAnyOrder("lote-1", "lote-2", "lote-3");
        assertThat(resumos).extracting(JogadorResumo::posicao).containsOnly("ATA");
    }

    @Test
    void deveDevolverListaVaziaQuandoNenhumIdEInformado() {
        var resumos = jogadorService.listarResumosPorIds(List.of(), "2040");

        assertThat(resumos).isEmpty();
    }

    @Test
    void deveResolverOSlugParaOIdentificadorInterno() {
        var id = jogadorService.buscarIdPorSlug("lote-2");

        assertThat(id).isPresent();
        assertThat(jogadorService.buscarIdPorSlug("nao-existe")).isEmpty();
    }

    private void inserirJogador(String slug, String nome) {
        var paisId = jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
        var posicaoId = jdbcTemplate.queryForObject(
                "select id from posicao where codigo = 'ATA'", Long.class);
        jdbcTemplate.update("""
                insert into jogador (slug, chave_natural, nome_completo, nome_exibicao, data_nascimento,
                                     pais_id, pe_preferido, posicao_principal_id, semente, origem)
                values (?, ?, ?, ?, date '1999-01-01', ?, 'DIREITO', ?, 1, 'REAL')
                """, slug, slug + "|1999-01-01|BRA", nome, nome, paisId, posicaoId);
        var jogadorId = jdbcTemplate.queryForObject(
                "select id from jogador where slug = ?", Long.class, slug);
        var temporadaId = jdbcTemplate.queryForObject(
                "select id from temporada where label = '2040'", Long.class);
        jdbcTemplate.update("""
                insert into jogador_atributo (
                    jogador_id, temporada_id,
                    ritmo, forca, folego, salto, agilidade,
                    passe, drible, cruzamento, frieza,
                    finalizacao, cabeceio, falta, penalti,
                    desarme, marcacao,
                    gol_reflexo, gol_posicionamento, gol_manejo,
                    potencial_base, potencial_variacao, fonte_atributo, coletado_em)
                values (?, ?, 75, 70, 80, 65, 78, 72, 76, 68, 74, 70, 60, 55, 70,
                        40, 38, 10, 10, 10, 85, 5, 'IMPORTADO', now())
                """, jogadorId, temporadaId);
    }
}
