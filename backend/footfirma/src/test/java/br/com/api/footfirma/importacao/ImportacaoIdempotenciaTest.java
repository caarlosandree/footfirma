package br.com.api.footfirma.importacao;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ImportacaoIdempotenciaTest {

    private static final Path FIXTURES = Path.of(System.getProperty("footfirma.fixtures"));

    @Autowired
    ImportacaoService importacaoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void esvaziarCatalogo() {
        LimpezaDoCatalogo.executar(jdbcTemplate);
    }

    @Test
    void deveConcluirSemOcorrenciasNaPrimeiraExecucao() {
        var relatorio = importacaoService.importar(FIXTURES);

        assertThat(relatorio.status()).isEqualTo("CONCLUIDA");
        assertThat(relatorio.ocorrencias()).isEmpty();
        assertThat(relatorio.motivoDaFalha()).isNull();
    }

    @Test
    void deveCarregarVinteClubesEQuatrocentosEQuarentaJogadores() {
        importacaoService.importar(FIXTURES);

        assertThat(contar("clube")).isEqualTo(20);
        assertThat(contar("jogador")).isEqualTo(440);
        assertThat(contar("jogador_atributo")).isEqualTo(880);
        assertThat(contar("jogador_vinculo")).isEqualTo(880);
    }

    @Test
    void deveReaproveitarOEstadioCompartilhadoEntreDoisClubes() {
        importacaoService.importar(FIXTURES);

        assertThat(contar("estadio")).isEqualTo(18);
        var clubesNoMaracana = jdbcTemplate.queryForObject("""
                select count(*) from clube c join estadio e on e.id = c.estadio_id
                where e.nome = 'Maracanã'
                """, Integer.class);
        assertThat(clubesNoMaracana).isEqualTo(2);
    }

    @Test
    void deveCriarZeroRegistrosNaSegundaExecucao() {
        importacaoService.importar(FIXTURES);

        var segunda = importacaoService.importar(FIXTURES);

        assertThat(segunda.status()).isEqualTo("CONCLUIDA");
        assertThat(segunda.contagens()).allSatisfy(contagem ->
                assertThat(contagem.criados())
                        .describedAs("entidade %s criou registros na segunda execução", contagem.entidade())
                        .isZero());
    }

    @Test
    void deveManterAsMesmasContagensDeTabelaNaSegundaExecucao() {
        importacaoService.importar(FIXTURES);
        var jogadoresDepoisDaPrimeira = contar("jogador");
        var vinculosDepoisDaPrimeira = contar("jogador_vinculo");

        importacaoService.importar(FIXTURES);

        assertThat(contar("jogador")).isEqualTo(jogadoresDepoisDaPrimeira);
        assertThat(contar("jogador_vinculo")).isEqualTo(vinculosDepoisDaPrimeira);
    }

    @Test
    void deveMaterializarOverallNasNovePosicoesParaAsDuasTemporadas() {
        importacaoService.importar(FIXTURES);

        assertThat(contar("jogador_overall")).isEqualTo(440 * 9 * 2);
        var zerados = jdbcTemplate.queryForObject(
                "select count(*) from jogador_overall where overall = 0", Integer.class);
        assertThat(zerados).isZero();
    }

    @Test
    void deveRegistrarAExecucaoComODatasetEOStatus() {
        var relatorio = importacaoService.importar(FIXTURES);

        var status = jdbcTemplate.queryForObject(
                "select status from importacao_execucao where id = ?", String.class,
                relatorio.execucaoId());
        var dataset = jdbcTemplate.queryForObject(
                "select dataset_versao from importacao_execucao where id = ?", String.class,
                relatorio.execucaoId());
        var finalizada = jdbcTemplate.queryForObject(
                "select finalizada_em from importacao_execucao where id = ?",
                OffsetDateTime.class, relatorio.execucaoId());
        assertThat(status).isEqualTo("CONCLUIDA");
        assertThat(dataset).isEqualTo("fixtures-v1");
        assertThat(finalizada).isNotNull();
    }

    @Test
    void deveGravarUmaContagemPorArquivoDoDataset() {
        var relatorio = importacaoService.importar(FIXTURES);

        var contagens = jdbcTemplate.queryForObject(
                "select count(*) from importacao_contagem where execucao_id = ?", Integer.class,
                relatorio.execucaoId());
        assertThat(contagens).isEqualTo(15);
    }

    // Concatenação em query é aceitável aqui porque o valor vem de literal do próprio
    // teste, nunca de entrada externa. Em código de produção continua proibida.
    private Integer contar(String tabela) {
        return jdbcTemplate.queryForObject("select count(*) from " + tabela, Integer.class);
    }
}
