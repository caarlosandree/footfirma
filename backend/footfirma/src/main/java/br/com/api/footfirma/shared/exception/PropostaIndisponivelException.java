package br.com.api.footfirma.shared.exception;

/**
 * A proposta existe, mas não está mais em jogo — já foi respondida ou o prazo passou.
 *
 * <p>Distinta de {@link RecursoNaoEncontradoException} de propósito: quem recebeu duas
 * propostas e demorou a responder precisa saber que perdeu a janela, não que a proposta
 * nunca existiu.
 */
public class PropostaIndisponivelException extends RuntimeException {

    public PropostaIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
