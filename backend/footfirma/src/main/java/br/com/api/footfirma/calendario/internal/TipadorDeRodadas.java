package br.com.api.footfirma.calendario.internal;

import br.com.api.footfirma.calendario.dto.TipoDeRodada;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Decide quais rodadas são de meio de semana e onde cada uma cai.
 *
 * <p>A regra não é "o intervalo médio apertou": é aritmética de semanas. Cabem
 * {@code dias / 7} semanas na janela; o que passar disso vira rodada de meio de semana,
 * espalhada uniformemente para não amontoar no fim da temporada.
 */
final class TipadorDeRodadas {

    private static final int DIAS_ENTRE_QUARTA_E_DOMINGO = 4;

    private TipadorDeRodadas() {
    }

    static List<TipoDeRodada> tipar(int rodadas, int diasDaJanela) {
        var semanas = diasDaJanela / 7;
        var meioDeSemana = Math.max(0, rodadas - semanas);

        var tipos = new ArrayList<TipoDeRodada>(rodadas);
        for (var i = 0; i < rodadas; i++) {
            tipos.add(TipoDeRodada.FIM_DE_SEMANA);
        }
        // Posições espaçadas por rodadas/(meioDeSemana+1): com 3 em 38, caem em 10, 19 e
        // 29 — nunca nas pontas, nunca coladas.
        for (var i = 1; i <= meioDeSemana; i++) {
            var posicao = Math.round((float) rodadas * i / (meioDeSemana + 1));
            tipos.set(Math.min(posicao, rodadas - 1), TipoDeRodada.MEIO_DE_SEMANA);
        }
        return List.copyOf(tipos);
    }

    /**
     * Fim de semana ocupa domingos consecutivos; meio de semana ocupa a quarta da mesma
     * semana do próximo domingo, <strong>sem consumir a semana</strong>.
     *
     * <p>É esse detalhe que faz 38 rodadas caberem em 35 semanas. Se a rodada de meio de
     * semana avançasse o calendário como uma de fim de semana, ela custaria uma semana
     * inteira e não resolveria nada — o clube jogaria quarta e só voltaria a jogar dez
     * dias depois, em vez de quarta e domingo.
     */
    static List<LocalDate> datasAlvo(LocalDate inicio, List<TipoDeRodada> tipos) {
        var datas = new ArrayList<LocalDate>(tipos.size());
        var domingo = inicio.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        for (var tipo : tipos) {
            if (tipo == TipoDeRodada.MEIO_DE_SEMANA) {
                datas.add(domingo.minusDays(DIAS_ENTRE_QUARTA_E_DOMINGO));
            } else {
                datas.add(domingo);
                domingo = domingo.plusWeeks(1);
            }
        }
        return List.copyOf(datas);
    }
}
