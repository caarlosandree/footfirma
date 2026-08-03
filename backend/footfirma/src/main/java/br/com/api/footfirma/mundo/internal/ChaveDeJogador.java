package br.com.api.footfirma.mundo.internal;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Define a identidade de um jogador. A semente derivada daqui é a origem de todo
 * atributo oculto — SHA-256 em vez de {@code hashCode()} porque 32 bits colidem com
 * frequência observável em milhares de jogadores, e colisão significa dois jogadores
 * com a mesma personalidade.
 *
 * <p>O sinal é descartado com {@code & Long.MAX_VALUE} porque semente negativa
 * complica todo uso em aritmética modular a jusante.
 */
final class ChaveDeJogador {

    private ChaveDeJogador() {
    }

    static String de(String nomeCompleto, LocalDate nascimento, String isoPais) {
        return normalizar(nomeCompleto) + "|" + nascimento + "|" + isoPais.toUpperCase(Locale.ROOT);
    }

    static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    static long semente(String chaveNatural) {
        var digest = digestSha256(chaveNatural.getBytes(StandardCharsets.UTF_8));
        return ByteBuffer.wrap(digest, 0, Long.BYTES).getLong() & Long.MAX_VALUE;
    }

    private static byte[] digestSha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 é obrigatório em toda JVM", erro);
        }
    }
}
