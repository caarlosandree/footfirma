package br.com.api.footfirma.importacao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.StringJoiner;
import java.util.stream.Stream;

/**
 * Monta variações do dataset em diretório temporário. O manifesto é regerado por
 * padrão: um teste de ocorrência que também quebrasse o checksum provaria a coisa
 * errada — abortaria na validação antes de chegar à ocorrência.
 */
final class CopiaDeDataset {

    private CopiaDeDataset() {
    }

    /** Copia tudo, acrescenta a linha ao arquivo indicado e regera o manifesto. */
    static void copiarComLinhaExtra(Path origem, Path destino, String arquivo, String linha)
            throws IOException {
        copiarArquivos(origem, destino);
        acrescentar(destino.resolve(arquivo), linha);
        regerarManifesto(destino);
    }

    /** Copia tudo e acrescenta a linha SEM regerar o manifesto — o checksum passa a divergir. */
    static void copiarSemRegerarManifesto(Path origem, Path destino, String arquivo, String linha)
            throws IOException {
        copiarArquivos(origem, destino);
        acrescentar(destino.resolve(arquivo), linha);
    }

    private static void acrescentar(Path arquivo, String linha) throws IOException {
        Files.writeString(arquivo,
                Files.readString(arquivo, StandardCharsets.UTF_8) + linha + "\n",
                StandardCharsets.UTF_8);
    }

    private static void copiarArquivos(Path origem, Path destino) throws IOException {
        Files.createDirectories(destino);
        try (Stream<Path> arquivos = Files.list(origem)) {
            for (var arquivo : arquivos.toList()) {
                if (Files.isRegularFile(arquivo)) {
                    Files.copy(arquivo, destino.resolve(arquivo.getFileName()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void regerarManifesto(Path diretorio) throws IOException {
        var entradas = new StringJoiner(",\n    ", "[\n    ", "\n  ]");
        try (Stream<Path> arquivos = Files.list(diretorio)) {
            for (var arquivo : arquivos
                    .filter(caminho -> caminho.toString().endsWith(".csv"))
                    .sorted()
                    .toList()) {
                entradas.add("{ \"nome\": \"%s\", \"linhas\": %d, \"sha256\": \"%s\" }"
                        .formatted(arquivo.getFileName(), contarLinhas(arquivo), sha256(arquivo)));
            }
        }
        Files.writeString(diretorio.resolve("manifest.json"), """
                {
                  "schemaVersao": "1",
                  "datasetVersao": "fixtures-v1",
                  "geradoEm": "2026-08-03",
                  "arquivos": %s
                }
                """.formatted(entradas), StandardCharsets.UTF_8);
    }

    private static long contarLinhas(Path arquivo) throws IOException {
        try (Stream<String> linhas = Files.lines(arquivo, StandardCharsets.UTF_8)) {
            return linhas.skip(1).filter(linha -> !linha.isBlank()).count();
        }
    }

    private static String sha256(Path arquivo) throws IOException {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(arquivo)));
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 é obrigatório em toda JVM", erro);
        }
    }
}
