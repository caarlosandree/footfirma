package br.com.api.footfirma.importacao.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidadorDeDatasetTest {

    private static final String CSV = "label,ano_inicio,ano_fim\n2025,2025,2025\n";

    @TempDir
    Path diretorio;

    @Test
    void deveAceitarDatasetIntegro() throws IOException {
        montarDatasetValido();

        var manifesto = ValidadorDeDataset.validar(diretorio);

        assertThat(manifesto.datasetVersao()).isEqualTo("fixtures-teste");
        assertThat(manifesto.arquivos()).hasSize(1);
    }

    @Test
    void deveRejeitarSchemaVersaoNaoSuportada() throws IOException {
        escreverCsv();
        escreverManifesto("99", ValidadorDeDataset.sha256(diretorio.resolve("temporada.csv")), 1);

        assertThatThrownBy(() -> ValidadorDeDataset.validar(diretorio))
                .isInstanceOf(DatasetInvalidoException.class)
                .hasMessageContaining("schema");
    }

    @Test
    void deveRejeitarQuandoChecksumDivergeDoArquivo() throws IOException {
        montarDatasetValido();
        Files.writeString(diretorio.resolve("temporada.csv"),
                CSV + "2026,2026,2026\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ValidadorDeDataset.validar(diretorio))
                .isInstanceOf(DatasetInvalidoException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void deveRejeitarQuandoArquivoListadoNaoExiste() throws IOException {
        montarDatasetValido();
        Files.delete(diretorio.resolve("temporada.csv"));

        assertThatThrownBy(() -> ValidadorDeDataset.validar(diretorio))
                .isInstanceOf(DatasetInvalidoException.class)
                .hasMessageContaining("temporada.csv");
    }

    @Test
    void deveRejeitarQuandoContagemDeLinhasDiverge() throws IOException {
        escreverCsv();
        escreverManifesto("1", ValidadorDeDataset.sha256(diretorio.resolve("temporada.csv")), 7);

        assertThatThrownBy(() -> ValidadorDeDataset.validar(diretorio))
                .isInstanceOf(DatasetInvalidoException.class)
                .hasMessageContaining("linhas");
    }

    @Test
    void deveRejeitarCsvPresenteNoDiretorioEAusenteDoManifesto() throws IOException {
        montarDatasetValido();
        Files.writeString(diretorio.resolve("esquecido.csv"), "a\n1\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ValidadorDeDataset.validar(diretorio))
                .isInstanceOf(DatasetInvalidoException.class)
                .hasMessageContaining("esquecido.csv");
    }

    @Test
    void deveRejeitarDiretorioSemManifesto() {
        assertThatThrownBy(() -> ValidadorDeDataset.validar(diretorio))
                .isInstanceOf(DatasetInvalidoException.class)
                .hasMessageContaining("manifest.json");
    }

    private void montarDatasetValido() throws IOException {
        escreverCsv();
        escreverManifesto("1", ValidadorDeDataset.sha256(diretorio.resolve("temporada.csv")), 1);
    }

    private void escreverCsv() throws IOException {
        Files.writeString(diretorio.resolve("temporada.csv"), CSV, StandardCharsets.UTF_8);
    }

    private void escreverManifesto(String schemaVersao, String sha256, int linhas) throws IOException {
        Files.writeString(diretorio.resolve("manifest.json"), """
                {
                  "schemaVersao": "%s",
                  "datasetVersao": "fixtures-teste",
                  "geradoEm": "2026-08-03",
                  "arquivos": [
                    { "nome": "temporada.csv", "linhas": %d, "sha256": "%s" }
                  ]
                }
                """.formatted(schemaVersao, linhas, sha256), StandardCharsets.UTF_8);
    }
}
