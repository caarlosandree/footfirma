package br.com.api.footfirma.shared.exception;

/**
 * A geração de calendário é impossível com os dados dados — fase sem participante, número
 * ímpar em eliminatória, janela curta demais para as rodadas exigidas.
 *
 * <p>Mora aqui, e não no módulo {@code calendario}, pelo mesmo motivo de
 * {@link EscalacaoInvalidaException}: quem a traduz para HTTP é o {@code TratadorDeErros}
 * de {@code shared}, e ele não pode enxergar tipo interno de módulo de domínio.
 */
public class CalendarioInvalidoException extends RuntimeException {

    public CalendarioInvalidoException(String mensagem) {
        super(mensagem);
    }
}
