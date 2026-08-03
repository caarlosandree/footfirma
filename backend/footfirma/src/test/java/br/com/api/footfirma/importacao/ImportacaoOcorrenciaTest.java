package br.com.api.footfirma.importacao;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ImportacaoOcorrenciaTest {

    private static final Path FIXTURES = Path.of(System.getProperty("footfirma.fixtures"));
    private static final String VINCULO_ORFAO =
            "jogador-inexistente,clube-inexistente,2026,CONTRATO,7,2026-01-01,2026-12-31,1000000.00";

    @Autowired
    ImportacaoService importacaoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @TempDir
    Path diretorio;

    @BeforeEach
    void esvaziarCatalogo() {
        LimpezaDoCatalogo.executar(jdbcTemplate);
    }

    @Test
    void deveConcluirACargaQuandoUmaLinhaApontaParaClubeInexistente() throws IOException {
        CopiaDeDataset.copiarComLinhaExtra(FIXTURES, diretorio, "jogador_vinculo.csv", VINCULO_ORFAO);

        var relatorio = importacaoService.importar(diretorio);

        assertThat(relatorio.status()).isEqualTo("CONCLUIDA");
    }

    @Test
    void deveRegistrarExatamenteUmaOcorrenciaDeVinculo() throws IOException {
        CopiaDeDataset.copiarComLinhaExtra(FIXTURES, diretorio, "jogador_vinculo.csv", VINCULO_ORFAO);

        var relatorio = importacaoService.importar(diretorio);

        assertThat(relatorio.ocorrencias()).hasSize(1);
        assertThat(relatorio.ocorrencias().getFirst().entidade()).isEqualTo("jogador_vinculo");
        assertThat(relatorio.ocorrencias().getFirst().severidade()).isEqualTo("ERRO");
    }

    @Test
    void deveGravarOsDemaisVinculosApesarDaLinhaRecusada() throws IOException {
        CopiaDeDataset.copiarComLinhaExtra(FIXTURES, diretorio, "jogador_vinculo.csv", VINCULO_ORFAO);

        importacaoService.importar(diretorio);

        var vinculos = jdbcTemplate.queryForObject(
                "select count(*) from jogador_vinculo", Integer.class);
        assertThat(vinculos).isEqualTo(880);
    }

    @Test
    void deveContarALinhaRecusadaNaContagemDaEntidade() throws IOException {
        CopiaDeDataset.copiarComLinhaExtra(FIXTURES, diretorio, "jogador_vinculo.csv", VINCULO_ORFAO);

        var relatorio = importacaoService.importar(diretorio);

        var contagem = relatorio.contagens().stream()
                .filter(candidata -> candidata.entidade().equals("jogador_vinculo"))
                .findFirst()
                .orElseThrow();
        assertThat(contagem.lidos()).isEqualTo(881);
        assertThat(contagem.recusados()).isEqualTo(1);
    }

    @Test
    void devePersistirAOcorrenciaComONumeroDaLinha() throws IOException {
        CopiaDeDataset.copiarComLinhaExtra(FIXTURES, diretorio, "jogador_vinculo.csv", VINCULO_ORFAO);

        var relatorio = importacaoService.importar(diretorio);

        var linha = jdbcTemplate.queryForObject(
                "select linha from importacao_ocorrencia where execucao_id = ?", Integer.class,
                relatorio.execucaoId());
        assertThat(linha).isEqualTo(882);
    }

    @Test
    void deveAbortarSemTocarNoBancoQuandoChecksumDiverge() throws IOException {
        CopiaDeDataset.copiarSemRegerarManifesto(FIXTURES, diretorio, "clube.csv",
                "sinc-intruso,Intruso FC,Intruso,,1950,BRA,SP,,#000000,#FFFFFF,50,50,SP");

        assertThatThrownBy(() -> importacaoService.importar(diretorio))
                .hasMessageContaining("checksum");

        assertThat(jdbcTemplate.queryForObject("select count(*) from clube", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from importacao_execucao", Integer.class)).isZero();
    }
}
