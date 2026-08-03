package br.com.api.footfirma.treinador;

/**
 * Um clube procurou um treinador. Para {@code HUMANO} a proposta fica aberta esperando
 * resposta; para {@code IA}, a política decide na hora.
 */
public record PropostaEnviada(long propostaId, long clubeId, long treinadorId, int metaPosicao) {
}
