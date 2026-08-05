package br.com.api.footfirma.shared.exception;

/**
 * O resultado não cabe no jogo — desempate que a fase não permite, jogo já encerrado, ou
 * jogo cujos clubes ainda não se definiram.
 *
 * <p>Mora aqui pelo mesmo motivo de {@link CalendarioInvalidoException}.
 */
public class ResultadoInvalidoException extends RuntimeException {

    public ResultadoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
