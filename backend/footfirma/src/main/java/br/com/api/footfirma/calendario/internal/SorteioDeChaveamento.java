package br.com.api.footfirma.calendario.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Monta a árvore inteira do mata-mata no sorteio: a primeira fase com clubes, as seguintes
 * vazias apontando para quem as alimenta.
 *
 * <p>A árvore existe em banco antes dos classificados existirem. A alternativa — criar os
 * jogos de uma fase só quando a anterior termina — deixaria o calendário com buracos, e
 * não daria para exibir o chaveamento nem dizer quando será a final.
 *
 * <p>Índices, não ids: quem traduz é o serviço, e é isso que mantém o sorteio testável sem
 * banco.
 */
final class SorteioDeChaveamento {

    private SorteioDeChaveamento() {
    }

    static List<ConfrontoDoChaveamento> montar(int participantes, SplittableRandom aleatorio) {
        if (participantes < 2 || Integer.bitCount(participantes) != 1) {
            throw new IllegalArgumentException(
                    "Participantes de eliminatória precisam ser potência de dois: " + participantes);
        }

        var sorteados = embaralhar(participantes, aleatorio);
        var chave = new ArrayList<ConfrontoDoChaveamento>(participantes - 1);
        var ordem = 1;

        // Primeira fase: pares consecutivos da lista sorteada.
        for (var i = 0; i < participantes; i += 2) {
            chave.add(new ConfrontoDoChaveamento(
                    ordem++, sorteados.get(i), sorteados.get(i + 1), null, null));
        }

        // Fases seguintes: cada confronto consome dois consecutivos da fase anterior. As
        // ordens crescem da primeira fase até a final, que é sempre a última.
        var inicioDaFaseAnterior = 1;
        var confrontosNaFaseAnterior = participantes / 2;
        while (confrontosNaFaseAnterior > 1) {
            for (var i = 0; i < confrontosNaFaseAnterior; i += 2) {
                chave.add(new ConfrontoDoChaveamento(ordem++, null, null,
                        inicioDaFaseAnterior + i, inicioDaFaseAnterior + i + 1));
            }
            inicioDaFaseAnterior += confrontosNaFaseAnterior;
            confrontosNaFaseAnterior /= 2;
        }

        return List.copyOf(chave);
    }

    /**
     * Fisher-Yates sobre a lista ordenada.
     *
     * <p>{@code Collections.shuffle} exige {@code Random}, e {@code SplittableRandom} é o
     * que dá reprodutibilidade no resto do sistema.
     */
    private static List<Integer> embaralhar(int quantidade, SplittableRandom aleatorio) {
        var lista = new ArrayList<Integer>(quantidade);
        for (var i = 0; i < quantidade; i++) {
            lista.add(i);
        }
        for (var i = quantidade - 1; i > 0; i--) {
            var j = aleatorio.nextInt(i + 1);
            var troca = lista.get(i);
            lista.set(i, lista.get(j));
            lista.set(j, troca);
        }
        return lista;
    }
}
