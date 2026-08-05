package br.com.api.footfirma.calendario.dto;

/**
 * O que aconteceu no jogo, tudo de uma vez.
 *
 * <p>Prorrogação e pênaltis nulos quando não houve — e o serviço recusa os que a fase não
 * permite, em vez de tolerar dado que a competição não produz.
 */
public record ResultadoDoJogo(int golsMandante, int golsVisitante,
                              Integer golsMandanteProrrogacao, Integer golsVisitanteProrrogacao,
                              Integer penaltisMandante, Integer penaltisVisitante) {

    /** O caso comum: jogo decidido no tempo normal. */
    public static ResultadoDoJogo noTempoNormal(int golsMandante, int golsVisitante) {
        return new ResultadoDoJogo(golsMandante, golsVisitante, null, null, null, null);
    }
}
