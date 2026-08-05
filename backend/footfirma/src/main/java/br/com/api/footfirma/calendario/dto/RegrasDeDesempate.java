package br.com.api.footfirma.calendario.dto;

/**
 * As regras que decidem o confronto, lidas da fase.
 *
 * <p>Vão junto do jogo para que a partida simule tudo de uma vez, em vez de perguntar
 * entre um tempo e outro. A alternativa seriam três viagens onde cabe uma, com a regra de
 * desempate duplicada nos dois lados.
 *
 * @param decisivo o jogo é o último do confronto — só nele prorrogação e pênaltis valem.
 */
public record RegrasDeDesempate(boolean temGolFora, boolean temProrrogacao,
                                boolean temPenaltis, boolean decisivo) {
}
