package br.com.api.footfirma.avaliacao;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MaterializacaoOverallTest {

    @Autowired
    AvaliacaoService avaliacaoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepararDoisJogadores() {
        // As seis tabelas dependentes de jogador, antes de jogador: desde que o
        // importador existe, _atributo_oculto, _caracteristica e _posicao também
        // podem ter linhas deixadas por outra classe de teste.
        jdbcTemplate.update("delete from jogador_overall");
        jdbcTemplate.update("delete from jogador_atributo");
        jdbcTemplate.update("delete from jogador_vinculo");
        jdbcTemplate.update("delete from jogador_atributo_oculto");
        jdbcTemplate.update("delete from jogador_caracteristica");
        jdbcTemplate.update("delete from jogador_posicao");
        jdbcTemplate.update("delete from jogador");
        jdbcTemplate.update("""
                insert into temporada (label, ano_inicio, ano_fim) values ('2041', 2041, 2041)
                on conflict (label) do nothing
                """);
        inserirJogador("mat-1", "ATA", 88);
        inserirJogador("mat-2", "ZAG", 60);
    }

    @Test
    void deveGravarNoveLinhasDeOverallPorJogador() {
        var resultado = avaliacaoService.materializar("2041");

        assertThat(resultado.jogadores()).isEqualTo(2);
        assertThat(resultado.linhas()).isEqualTo(18);
        var gravadas = jdbcTemplate.queryForObject(
                "select count(*) from jogador_overall", Integer.class);
        assertThat(gravadas).isEqualTo(18);
    }

    @Test
    void deveGravarAVersaoDoPerfilUsadaNoCalculo() {
        avaliacaoService.materializar("2041");

        var versoes = jdbcTemplate.queryForList(
                "select distinct perfil_versao from jogador_overall", Integer.class);

        assertThat(versoes).containsExactly(1);
    }

    // A garantia que o importador do Plano 3 depende: rodar duas vezes sobre o mesmo
    // estado não duplica nem muda nada.
    @Test
    void deveSerIdempotenteQuandoExecutadaDuasVezes() {
        avaliacaoService.materializar("2041");
        var primeiraLeitura = jdbcTemplate.queryForList(
                "select jogador_id, posicao_id, overall from jogador_overall order by jogador_id, posicao_id");

        avaliacaoService.materializar("2041");
        var segundaLeitura = jdbcTemplate.queryForList(
                "select jogador_id, posicao_id, overall from jogador_overall order by jogador_id, posicao_id");

        assertThat(segundaLeitura).hasSize(18);
        assertThat(segundaLeitura).isEqualTo(primeiraLeitura);
    }

    @Test
    void deveDarNotaMaiorAoAtacanteNaPosicaoDeAtacanteDoQueNaDeZagueiro() {
        avaliacaoService.materializar("2041");

        var comoAtacante = overallDe("mat-1", "ATA");
        var comoZagueiro = overallDe("mat-1", "ZAG");

        assertThat(comoAtacante).isGreaterThan(comoZagueiro);
    }

    @Test
    void naoDeveGravarNadaQuandoATemporadaNaoTemAtributos() {
        jdbcTemplate.update("""
                insert into temporada (label, ano_inicio, ano_fim) values ('2042', 2042, 2042)
                on conflict (label) do nothing
                """);

        var resultado = avaliacaoService.materializar("2042");

        assertThat(resultado.jogadores()).isZero();
        assertThat(resultado.linhas()).isZero();
    }

    private Integer overallDe(String slug, String codigoPosicao) {
        return jdbcTemplate.queryForObject("""
                select o.overall from jogador_overall o
                join jogador j on j.id = o.jogador_id
                join posicao p on p.id = o.posicao_id
                where j.slug = ? and p.codigo = ?
                """, Integer.class, slug, codigoPosicao);
    }

    private void inserirJogador(String slug, String codigoPosicao, int finalizacao) {
        var paisId = jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
        var posicaoId = jdbcTemplate.queryForObject(
                "select id from posicao where codigo = ?", Long.class, codigoPosicao);
        jdbcTemplate.update("""
                insert into jogador (slug, chave_natural, nome_completo, nome_exibicao, data_nascimento,
                                     pais_id, pe_preferido, posicao_principal_id, semente, origem)
                values (?, ?, ?, ?, date '1999-01-01', ?, 'DIREITO', ?, 1, 'REAL')
                """, slug, slug + "|1999-01-01|BRA", slug, slug, paisId, posicaoId);
        var jogadorId = jdbcTemplate.queryForObject(
                "select id from jogador where slug = ?", Long.class, slug);
        var temporadaId = jdbcTemplate.queryForObject(
                "select id from temporada where label = '2041'", Long.class);
        jdbcTemplate.update("""
                insert into jogador_atributo (
                    jogador_id, temporada_id,
                    ritmo, forca, folego, salto, agilidade,
                    passe, drible, cruzamento, frieza,
                    finalizacao, cabeceio, falta, penalti,
                    desarme, marcacao,
                    gol_reflexo, gol_posicionamento, gol_manejo,
                    potencial_base, potencial_variacao, fonte_atributo, coletado_em)
                values (?, ?, 82, 70, 78, 66, 80, 68, 79, 60, 77, ?, 62, 50, 70,
                        35, 30, 10, 10, 10, 90, 5, 'IMPORTADO', now())
                """, jogadorId, temporadaId, finalizacao);
    }
}
