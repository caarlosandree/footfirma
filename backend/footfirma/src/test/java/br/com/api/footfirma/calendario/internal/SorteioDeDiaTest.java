package br.com.api.footfirma.calendario.internal;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.Map;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class SorteioDeDiaTest {

    static final Map<DayOfWeek, Integer> FIM_DE_SEMANA_SERIE_A = Map.of(
            DayOfWeek.SUNDAY, 45, DayOfWeek.SATURDAY, 40, DayOfWeek.MONDAY, 15);

    @Test
    void deveDevolverTodosOsDiasDoLequeEmUmaOrdem() {
        var ordem = SorteadorDeDia.ordenarPorPeso(FIM_DE_SEMANA_SERIE_A, new SplittableRandom(7));
        assertThat(ordem).containsExactlyInAnyOrder(
                DayOfWeek.SUNDAY, DayOfWeek.SATURDAY, DayOfWeek.MONDAY);
    }

    @Test
    void naoDeveIncluirDiaForaDoLeque() {
        var ordem = SorteadorDeDia.ordenarPorPeso(FIM_DE_SEMANA_SERIE_A, new SplittableRandom(7));
        assertThat(ordem).doesNotContain(DayOfWeek.FRIDAY, DayOfWeek.WEDNESDAY);
    }

    @Test
    void deveRepetirASequenciaComAMesmaSemente() {
        assertThat(SorteadorDeDia.ordenarPorPeso(FIM_DE_SEMANA_SERIE_A, new SplittableRandom(42)))
                .isEqualTo(SorteadorDeDia.ordenarPorPeso(
                        FIM_DE_SEMANA_SERIE_A, new SplittableRandom(42)));
    }

    @Test
    void devePreferirODeMaiorPesoNaMaioriaDasVezes() {
        var aleatorio = new SplittableRandom(1);
        var domingoPrimeiro = 0;
        for (var i = 0; i < 1_000; i++) {
            if (SorteadorDeDia.ordenarPorPeso(FIM_DE_SEMANA_SERIE_A, aleatorio)
                    .getFirst() == DayOfWeek.SUNDAY) {
                domingoPrimeiro++;
            }
        }
        // 45% de peso: a faixa é generosa de propósito, o teste trava a tendência e não
        // o percentual — rebalancear os pesos não pode quebrar a suíte.
        assertThat(domingoPrimeiro).isBetween(380, 520);
    }

    @Test
    void deveFuncionarComUmDiaSo() {
        var ordem = SorteadorDeDia.ordenarPorPeso(
                Map.of(DayOfWeek.WEDNESDAY, 100), new SplittableRandom(3));
        assertThat(ordem).containsExactly(DayOfWeek.WEDNESDAY);
    }
}
