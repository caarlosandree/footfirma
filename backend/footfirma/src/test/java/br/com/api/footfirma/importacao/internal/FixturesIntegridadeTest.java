package br.com.api.footfirma.importacao.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pega a fixture editada à mão sem regerar o manifesto — a falha real. Não regenera
 * o dataset nem compara com o gerador: acoplar o teste ao gerador faria os dois
 * concordarem enquanto ambos estivessem errados.
 */
class FixturesIntegridadeTest {

    private static final Path FIXTURES = Path.of(System.getProperty("footfirma.fixtures"));

    @Test
    void deveTerManifestoConsistenteComOsArquivosVersionados() {
        var manifesto = ValidadorDeDataset.validar(FIXTURES);

        assertThat(manifesto.datasetVersao()).isEqualTo("fixtures-v1");
        assertThat(manifesto.schemaVersao()).isEqualTo(Manifesto.SCHEMA_SUPORTADO);
    }

    @Test
    void deveConterOsQuinzeArquivosDoFormato() {
        var manifesto = ValidadorDeDataset.validar(FIXTURES);

        assertThat(manifesto.arquivos()).hasSize(15);
    }

    @Test
    void deveTerVinteClubesEQuatrocentosEQuarentaJogadores() throws IOException {
        var clubes = contarLinhas("clube.csv");
        var jogadores = contarLinhas("jogador.csv");

        assertThat(clubes).isEqualTo(20);
        assertThat(jogadores).isEqualTo(440);
    }

    @Test
    void deveTerAtributosEVinculosNasDuasTemporadas() throws IOException {
        var atributos = contarLinhas("jogador_atributo.csv");
        var vinculos = contarLinhas("jogador_vinculo.csv");

        assertThat(atributos).isEqualTo(880);
        assertThat(vinculos).isEqualTo(880);
    }

    @Test
    void deveDeduplicarEstadioCompartilhadoPorDoisClubes() throws IOException {
        // Maracanã serve Flamengo e Fluminense; Arena Castelão serve Fortaleza e
        // Ceará. Vinte clubes, dezoito estádios.
        var estadios = contarLinhas("estadio.csv");

        assertThat(estadios).isEqualTo(18);
    }

    private long contarLinhas(String arquivo) throws IOException {
        try (var linhas = Files.lines(FIXTURES.resolve(arquivo), StandardCharsets.UTF_8)) {
            return linhas.skip(1).filter(linha -> !linha.isBlank()).count();
        }
    }
}
