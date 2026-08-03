package br.com.api.footfirma.treinador;

/**
 * O clube perdeu a paciência: a moral caiu abaixo do limiar depois da carência.
 *
 * <p>Vem sempre acompanhado de {@link VinculoEncerrado} — este diz <i>o que</i>
 * aconteceu com a carreira, aquele diz que o clube ficou vago.
 */
public record TreinadorDemitido(long treinadorId, long clubeId, long vinculoId, double moralFinal) {
}
