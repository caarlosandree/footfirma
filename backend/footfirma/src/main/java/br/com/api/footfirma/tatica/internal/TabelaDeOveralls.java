package br.com.api.footfirma.tatica.internal;

import java.util.Map;

/**
 * Overall por {@code (jogador, posição)}.
 *
 * <p>Devolve 0 para par ausente em vez de estourar: um jogador sem overall
 * materializado é escalável, só nunca será o escolhido.
 */
record TabelaDeOveralls(Map<Long, Map<Long, Integer>> valores) {

    int overall(long jogadorId, long posicaoId) {
        return valores.getOrDefault(jogadorId, Map.of()).getOrDefault(posicaoId, 0);
    }
}
