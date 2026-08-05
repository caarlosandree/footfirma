package br.com.api.footfirma.calendario.internal;

/**
 * Um slot da árvore de mata-mata.
 *
 * <p>{@code ladoA}/{@code ladoB} são índices de participante na primeira fase;
 * {@code origemA}/{@code origemB} são ordens de confronto nas fases seguintes. Nunca os
 * quatro ao mesmo tempo — ou o clube já se conhece, ou ele virá de outro confronto.
 */
record ConfrontoDoChaveamento(int ordem, Integer ladoA, Integer ladoB,
                              Integer origemA, Integer origemB) {
}
