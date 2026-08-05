package br.com.api.footfirma.calendario.internal;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Ordena os dias do leque por sorteio ponderado, sem repetição.
 *
 * <p>Devolve a ordem inteira, e não um dia só: o alocador tenta o primeiro e desce a lista
 * quando o descanso não fecha. Sortear de novo a cada tentativa quebraria o determinismo.
 */
final class SorteadorDeDia {

    private SorteadorDeDia() {
    }

    static List<DayOfWeek> ordenarPorPeso(Map<DayOfWeek, Integer> pesos,
                                          SplittableRandom aleatorio) {
        // Ordem estável antes do sorteio: Map.of não garante iteração reprodutível, e sem
        // isso a mesma semente daria calendários diferentes entre execuções.
        var candidatos = new ArrayList<>(pesos.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList());

        var ordem = new ArrayList<DayOfWeek>(candidatos.size());
        var restante = candidatos.stream().mapToInt(Map.Entry::getValue).sum();

        while (!candidatos.isEmpty()) {
            var sorteio = aleatorio.nextInt(Math.max(1, restante));
            var acumulado = 0;
            var escolhido = candidatos.size() - 1;
            for (var i = 0; i < candidatos.size(); i++) {
                acumulado += candidatos.get(i).getValue();
                if (sorteio < acumulado) {
                    escolhido = i;
                    break;
                }
            }
            var vencedor = candidatos.remove(escolhido);
            restante -= vencedor.getValue();
            ordem.add(vencedor.getKey());
        }
        return List.copyOf(ordem);
    }
}
