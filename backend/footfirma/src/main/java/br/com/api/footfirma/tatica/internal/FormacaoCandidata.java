package br.com.api.footfirma.tatica.internal;

import java.util.List;

/**
 * Uma formação do catálogo, achatada para o preenchimento.
 *
 * @param posicaoPorSlot índice 0 é o slot de ordem 1.
 */
record FormacaoCandidata(long formacaoId, int ordem, List<Long> posicaoPorSlot) {
}
