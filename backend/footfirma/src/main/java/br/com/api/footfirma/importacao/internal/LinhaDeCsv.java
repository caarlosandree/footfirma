package br.com.api.footfirma.importacao.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;

/**
 * Uma linha já dividida, com acesso por nome de coluna. Carrega arquivo e número
 * para que toda mensagem de erro aponte o lugar exato — sem isso, "valor inválido"
 * em 352 linhas de atributo é inútil.
 */
record LinhaDeCsv(String arquivo, int numero, List<String> cabecalho, List<String> campos) {

    String texto(String coluna) {
        var valor = campos.get(indice(coluna));
        return valor.isEmpty() ? null : valor;
    }

    String textoObrigatorio(String coluna) {
        var valor = texto(coluna);
        if (valor == null) {
            throw new DatasetInvalidoException(
                    "%s, linha %d: coluna '%s' é obrigatória e está vazia".formatted(arquivo, numero, coluna));
        }
        return valor;
    }

    Integer inteiro(String coluna) {
        var valor = texto(coluna);
        return valor == null ? null : converter(coluna, valor, Integer::valueOf);
    }

    Long numeroLongo(String coluna) {
        var valor = texto(coluna);
        return valor == null ? null : converter(coluna, valor, Long::valueOf);
    }

    BigDecimal decimal(String coluna) {
        var valor = texto(coluna);
        return valor == null ? null : converter(coluna, valor, BigDecimal::new);
    }

    LocalDate data(String coluna) {
        var valor = texto(coluna);
        return valor == null ? null : converter(coluna, valor, LocalDate::parse);
    }

    OffsetDateTime dataHora(String coluna) {
        var valor = texto(coluna);
        return valor == null ? null : converter(coluna, valor, OffsetDateTime::parse);
    }

    boolean booleano(String coluna) {
        return "true".equalsIgnoreCase(texto(coluna));
    }

    private <T> T converter(String coluna, String valor, Function<String, T> conversao) {
        try {
            return conversao.apply(valor);
        } catch (RuntimeException erro) {
            throw new DatasetInvalidoException(
                    "%s, linha %d: valor '%s' inválido na coluna '%s'".formatted(arquivo, numero, valor, coluna),
                    erro);
        }
    }

    private int indice(String coluna) {
        var indice = cabecalho.indexOf(coluna);
        if (indice < 0) {
            throw new DatasetInvalidoException(
                    "%s: coluna '%s' não existe no cabeçalho %s".formatted(arquivo, coluna, cabecalho));
        }
        return indice;
    }
}
