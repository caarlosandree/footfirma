package br.com.api.footfirma.shared.exception;

/**
 * A escalação violou uma invariante de agregado — número de titulares, slots, vínculo,
 * repetição, banco ou capitão.
 *
 * <p>Mora aqui, e não no módulo {@code tatica}, pelo mesmo motivo de
 * {@link DistribuicaoInvalidaException}: quem a traduz para HTTP é o
 * {@code TratadorDeErros} de {@code shared}, e ele não pode enxergar tipo interno de
 * módulo de domínio.
 */
public class EscalacaoInvalidaException extends RuntimeException {

    public EscalacaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
