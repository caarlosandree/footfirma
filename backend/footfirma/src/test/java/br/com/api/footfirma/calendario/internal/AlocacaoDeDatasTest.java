package br.com.api.footfirma.calendario.internal;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlocacaoDeDatasTest {

    static final LocalDate INICIO = LocalDate.of(2026, 7, 3);   // sexta
    static final LocalDate FIM = LocalDate.of(2026, 7, 7);      // terça
    static final LocalDate SABADO = LocalDate.of(2026, 7, 4);
    static final LocalDate DOMINGO = LocalDate.of(2026, 7, 5);
    static final LocalDate SEGUNDA = LocalDate.of(2026, 7, 6);

    static final List<DayOfWeek> DOMINGO_PRIMEIRO =
            List.of(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY, DayOfWeek.MONDAY);

    static final int DESCANSO = 3;

    @Test
    void devePegarOPrimeiroDiaDaOrdemQuandoAAgendaEstaLivre() {
        var data = AlocadorDeDatas.alocar(
                INICIO, FIM, DOMINGO_PRIMEIRO, List.of(), List.of(), DESCANSO);
        assertThat(data).isEqualTo(DOMINGO);
    }

    @Test
    void deveRespeitarODescansoDoMandanteAindaQueCustoOPrimeiroDiaDaOrdem() {
        // O mandante jogou no domingo em outra competição. Nenhum dia do leque dista 3
        // dias dele — sábado e segunda estão a 1 dia —, então o jogo sai da janela.
        var data = AlocadorDeDatas.alocar(
                INICIO, FIM, DOMINGO_PRIMEIRO, List.of(DOMINGO), List.of(), DESCANSO);

        assertThat(data).isNotEqualTo(DOMINGO);
        assertThat(Math.abs(ChronoUnit.DAYS.between(DOMINGO, data)))
                .isGreaterThanOrEqualTo(DESCANSO);
    }

    @Test
    void deveRespeitarODescansoDosDoisClubes() {
        var data = AlocadorDeDatas.alocar(
                INICIO, FIM, DOMINGO_PRIMEIRO, List.of(DOMINGO), List.of(SABADO), DESCANSO);

        assertThat(Math.abs(ChronoUnit.DAYS.between(DOMINGO, data)))
                .as("descanso do mandante").isGreaterThanOrEqualTo(DESCANSO);
        assertThat(Math.abs(ChronoUnit.DAYS.between(SABADO, data)))
                .as("descanso do visitante").isGreaterThanOrEqualTo(DESCANSO);
    }

    @Test
    void deveDescerNaOrdemDoLequeEmVezDeSairDaJanela() {
        // O mandante jogou na quarta anterior (1/7): domingo dista 4 dias e serve, mas
        // vamos ocupá-lo — sábado dista 3 do dia 1/7 e continua dentro da janela.
        var data = AlocadorDeDatas.alocar(
                INICIO, FIM, DOMINGO_PRIMEIRO,
                List.of(LocalDate.of(2026, 7, 2)), List.of(), DESCANSO);

        assertThat(data).isBetween(INICIO, FIM);
        assertThat(data).isEqualTo(DOMINGO);
    }

    @Test
    void deveEmpurrarParaForaDaJanelaQuandoNenhumDiaDoLequeServe() {
        var ocupado = List.of(SABADO, DOMINGO, SEGUNDA);
        var data = AlocadorDeDatas.alocar(INICIO, FIM, DOMINGO_PRIMEIRO, ocupado, List.of(), DESCANSO);

        assertThat(data).isAfter(FIM);
        for (var ocupada : ocupado) {
            assertThat(Math.abs(ChronoUnit.DAYS.between(ocupada, data)))
                    .isGreaterThanOrEqualTo(DESCANSO);
        }
    }

    @Test
    void deveFalharQuandoNemMesmoOEmpurraoResolve() {
        var lotado = new ArrayList<LocalDate>();
        for (var i = 0; i < 60; i++) {
            lotado.add(INICIO.plusDays(i));
        }
        assertThatThrownBy(() -> AlocadorDeDatas.alocar(
                INICIO, FIM, DOMINGO_PRIMEIRO, lotado, List.of(), DESCANSO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("descanso");
    }

    @Test
    void deveSerDeterministicoParaAMesmaEntrada() {
        var primeira = AlocadorDeDatas.alocar(
                INICIO, FIM, DOMINGO_PRIMEIRO, List.of(DOMINGO), List.of(), DESCANSO);
        var segunda = AlocadorDeDatas.alocar(
                INICIO, FIM, DOMINGO_PRIMEIRO, List.of(DOMINGO), List.of(), DESCANSO);
        assertThat(segunda).isEqualTo(primeira);
    }
}
