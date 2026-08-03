package br.com.api.footfirma.shared.exception;

/**
 * A distribuição de pontos entre as skills violou a invariante do agregado.
 *
 * <p>Mora aqui, e não no módulo {@code treinador}, pelo mesmo motivo de
 * {@link RecursoNaoEncontradoException}: quem a traduz para HTTP é o
 * {@code TratadorDeErros} de {@code shared}, e ele não pode enxergar tipo interno de
 * módulo de domínio.
 */
public class DistribuicaoInvalidaException extends RuntimeException {

    public DistribuicaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
