package br.com.api.footfirma.importacao.internal;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Valida o dataset inteiro antes de qualquer escrita. A ordem das quatro regras
 * importa: sem schema conhecido, as demais checagens não significam nada.
 */
final class ValidadorDeDataset {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ARQUIVO_DO_MANIFESTO = "manifest.json";

    private ValidadorDeDataset() {
    }

    static Manifesto validar(Path diretorio) {
        var manifesto = lerManifesto(diretorio);

        if (!Manifesto.SCHEMA_SUPORTADO.equals(manifesto.schemaVersao())) {
            throw new DatasetInvalidoException(
                    "schema versão '%s' não suportada; esperada '%s'"
                            .formatted(manifesto.schemaVersao(), Manifesto.SCHEMA_SUPORTADO));
        }

        var listados = new HashSet<String>();
        for (var arquivo : manifesto.arquivos()) {
            listados.add(arquivo.nome());
            var caminho = diretorio.resolve(arquivo.nome());
            if (!Files.isRegularFile(caminho)) {
                throw new DatasetInvalidoException(
                        "manifesto lista %s, que não existe no diretório".formatted(arquivo.nome()));
            }
            var checksum = sha256(caminho);
            if (!checksum.equals(arquivo.sha256())) {
                throw new DatasetInvalidoException(
                        "checksum de %s não confere: manifesto diz %s, arquivo tem %s"
                                .formatted(arquivo.nome(), arquivo.sha256(), checksum));
            }
            var linhas = contarLinhasDeDados(caminho);
            if (linhas != arquivo.linhas()) {
                throw new DatasetInvalidoException(
                        "%s tem %d linhas de dados; manifesto declara %d"
                                .formatted(arquivo.nome(), linhas, arquivo.linhas()));
            }
        }

        for (var csv : csvsDoDiretorio(diretorio)) {
            if (!listados.contains(csv)) {
                throw new DatasetInvalidoException(
                        "%s existe no diretório mas não está no manifesto".formatted(csv));
            }
        }
        return manifesto;
    }

    static String sha256(Path arquivo) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(arquivo));
            return HexFormat.of().formatHex(digest);
        } catch (IOException erro) {
            throw new DatasetInvalidoException("Não foi possível ler " + arquivo.getFileName(), erro);
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 é obrigatório em toda JVM", erro);
        }
    }

    private static Manifesto lerManifesto(Path diretorio) {
        var caminho = diretorio.resolve(ARQUIVO_DO_MANIFESTO);
        if (!Files.isRegularFile(caminho)) {
            throw new DatasetInvalidoException(
                    "%s não encontrado em %s".formatted(ARQUIVO_DO_MANIFESTO, diretorio));
        }
        try {
            return JSON.readValue(Files.readString(caminho, StandardCharsets.UTF_8), Manifesto.class);
        } catch (IOException erro) {
            throw new DatasetInvalidoException(ARQUIVO_DO_MANIFESTO + " é inválido", erro);
        }
    }

    private static int contarLinhasDeDados(Path arquivo) {
        try (var linhas = Files.lines(arquivo, StandardCharsets.UTF_8)) {
            return (int) linhas.skip(1).filter(linha -> !linha.isBlank()).count();
        } catch (IOException erro) {
            throw new DatasetInvalidoException("Não foi possível ler " + arquivo.getFileName(), erro);
        }
    }

    private static List<String> csvsDoDiretorio(Path diretorio) {
        try (Stream<Path> arquivos = Files.list(diretorio)) {
            return arquivos
                    .map(caminho -> caminho.getFileName().toString())
                    .filter(nome -> nome.endsWith(".csv"))
                    .sorted()
                    .toList();
        } catch (IOException erro) {
            throw new DatasetInvalidoException("Não foi possível listar " + diretorio, erro);
        }
    }
}
