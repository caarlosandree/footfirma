package br.com.api.footfirma.importacao.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeitorDeCsvTest {

    private static final List<String> CABECALHO = List.of("slug", "nome", "reputacao");

    @TempDir
    Path diretorio;

    @Test
    void deveLerAsLinhasDeUmArquivoSimples() throws IOException {
        var arquivo = escrever("""
                slug,nome,reputacao
                alfa,Alfa,70
                beta,Beta,64
                """);

        var linhas = LeitorDeCsv.ler(arquivo, CABECALHO);

        assertThat(linhas).hasSize(2);
        assertThat(linhas.getFirst().texto("nome")).isEqualTo("Alfa");
        assertThat(linhas.getLast().inteiro("reputacao")).isEqualTo(64);
    }

    @Test
    void deveLerCampoEntreAspasContendoVirgula() throws IOException {
        var arquivo = escrever("""
                slug,nome,reputacao
                alfa,"Alfa, Beta e Gama",70
                """);

        var linhas = LeitorDeCsv.ler(arquivo, CABECALHO);

        assertThat(linhas.getFirst().texto("nome")).isEqualTo("Alfa, Beta e Gama");
    }

    @Test
    void deveInterpretarAspasDuplicadasComoAspaLiteral() throws IOException {
        var arquivo = escrever("""
                slug,nome,reputacao
                alfa,"O ""Grande"" Alfa",70
                """);

        var linhas = LeitorDeCsv.ler(arquivo, CABECALHO);

        assertThat(linhas.getFirst().texto("nome")).isEqualTo("O \"Grande\" Alfa");
    }

    @Test
    void deveTratarCampoVazioComoNulo() throws IOException {
        var arquivo = escrever("""
                slug,nome,reputacao
                alfa,,70
                """);

        var linhas = LeitorDeCsv.ler(arquivo, CABECALHO);

        assertThat(linhas.getFirst().texto("nome")).isNull();
        assertThat(linhas.getFirst().inteiro("nome")).isNull();
    }

    @Test
    void deveDevolverListaVaziaQuandoArquivoSoTemCabecalho() throws IOException {
        var arquivo = escrever("slug,nome,reputacao\n");

        var linhas = LeitorDeCsv.ler(arquivo, CABECALHO);

        assertThat(linhas).isEmpty();
    }

    @Test
    void deveIgnorarBomNoInicioDoArquivo() throws IOException {
        var arquivo = escrever("﻿slug,nome,reputacao\nalfa,Alfa,70\n");

        var linhas = LeitorDeCsv.ler(arquivo, CABECALHO);

        assertThat(linhas.getFirst().texto("slug")).isEqualTo("alfa");
    }

    @Test
    void deveRejeitarArquivoComCabecalhoDivergente() throws IOException {
        var arquivo = escrever("""
                slug,nome
                alfa,Alfa
                """);

        assertThatThrownBy(() -> LeitorDeCsv.ler(arquivo, CABECALHO))
                .isInstanceOf(DatasetInvalidoException.class)
                .hasMessageContaining("cabeçalho");
    }

    @Test
    void deveRejeitarLinhaComNumeroDeCamposDiferenteDoCabecalho() throws IOException {
        var arquivo = escrever("""
                slug,nome,reputacao
                alfa,Alfa
                """);

        assertThatThrownBy(() -> LeitorDeCsv.ler(arquivo, CABECALHO))
                .isInstanceOf(DatasetInvalidoException.class)
                .hasMessageContaining("linha 2");
    }

    @Test
    void deveIndicarONumeroDaLinhaContandoOCabecalho() throws IOException {
        var arquivo = escrever("""
                slug,nome,reputacao
                alfa,Alfa,70
                beta,Beta,64
                """);

        var linhas = LeitorDeCsv.ler(arquivo, CABECALHO);

        assertThat(linhas.getFirst().numero()).isEqualTo(2);
        assertThat(linhas.getLast().numero()).isEqualTo(3);
    }

    @Test
    void deveRejeitarAcessoAColunaInexistente() throws IOException {
        var arquivo = escrever("""
                slug,nome,reputacao
                alfa,Alfa,70
                """);
        var linha = LeitorDeCsv.ler(arquivo, CABECALHO).getFirst();

        assertThatThrownBy(() -> linha.texto("inexistente"))
                .isInstanceOf(DatasetInvalidoException.class)
                .hasMessageContaining("inexistente");
    }

    @Test
    void deveRejeitarCampoObrigatorioVazio() throws IOException {
        var arquivo = escrever("""
                slug,nome,reputacao
                ,Alfa,70
                """);
        var linha = LeitorDeCsv.ler(arquivo, CABECALHO).getFirst();

        assertThatThrownBy(() -> linha.textoObrigatorio("slug"))
                .isInstanceOf(DatasetInvalidoException.class)
                .hasMessageContaining("slug");
    }

    private Path escrever(String conteudo) throws IOException {
        var arquivo = diretorio.resolve("teste.csv");
        Files.writeString(arquivo, conteudo, StandardCharsets.UTF_8);
        return arquivo;
    }
}
