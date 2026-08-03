package br.com.api.footfirma.treinador;

/**
 * A moral entrou na faixa entre o limiar de demissão e dez pontos acima dele: o emprego
 * ainda existe, mas o próximo tropeço decide.
 */
public record TreinadorSobPressao(long treinadorId, long clubeId, long vinculoId, double moral) {
}
