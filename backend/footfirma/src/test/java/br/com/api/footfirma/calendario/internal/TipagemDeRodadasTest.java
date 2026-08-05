package br.com.api.footfirma.calendario.internal;

import br.com.api.footfirma.calendario.dto.TipoDeRodada;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class TipagemDeRodadasTest {

    // 4/4/2026 a 6/12/2026 são 246 dias, ou 35 semanas inteiras.
    static final LocalDate INICIO = LocalDate.of(2026, 4, 4);
    static final LocalDate FIM = LocalDate.of(2026, 12, 6);
    static final int JANELA_DO_MUNDO = 246;

    @Test
    void deveProduzirTresRodadasDeMeioDeSemanaQuandoTrintaEOitoNaoCabemEmTrintaECinco() {
        var tipos = TipadorDeRodadas.tipar(38, JANELA_DO_MUNDO);
        assertThat(tipos).hasSize(38);
        assertThat(tipos.stream().filter(t -> t == TipoDeRodada.MEIO_DE_SEMANA)).hasSize(3);
    }

    @Test
    void naoDeveProduzirMeioDeSemanaQuandoAsRodadasCabemEmSemanas() {
        assertThat(TipadorDeRodadas.tipar(20, JANELA_DO_MUNDO))
                .containsOnly(TipoDeRodada.FIM_DE_SEMANA);
    }

    @Test
    void deveEspalharAsRodadasDeMeioDeSemanaEmVezDeAgrupaLasNoFim() {
        var tipos = TipadorDeRodadas.tipar(38, JANELA_DO_MUNDO);
        var posicoes = new ArrayList<Integer>();
        for (var i = 0; i < tipos.size(); i++) {
            if (tipos.get(i) == TipoDeRodada.MEIO_DE_SEMANA) {
                posicoes.add(i);
            }
        }
        assertThat(posicoes.getFirst()).isGreaterThan(2);
        assertThat(posicoes.getLast()).isLessThan(35);
        for (var i = 1; i < posicoes.size(); i++) {
            assertThat(posicoes.get(i) - posicoes.get(i - 1)).isGreaterThan(3);
        }
    }

    @Test
    void deveAncorarFimDeSemanaNoDomingoEMeioDeSemanaNaQuarta() {
        var tipos = TipadorDeRodadas.tipar(38, JANELA_DO_MUNDO);
        var datas = TipadorDeRodadas.datasAlvo(INICIO, tipos);

        assertThat(datas).hasSize(38);
        for (var i = 0; i < 38; i++) {
            var esperado = tipos.get(i) == TipoDeRodada.FIM_DE_SEMANA
                    ? DayOfWeek.SUNDAY : DayOfWeek.WEDNESDAY;
            assertThat(datas.get(i).getDayOfWeek()).as("rodada %d", i + 1).isEqualTo(esperado);
        }
    }

    @Test
    void deveManterAsDatasEmOrdemCrescente() {
        var datas = TipadorDeRodadas.datasAlvo(INICIO, TipadorDeRodadas.tipar(38, JANELA_DO_MUNDO));
        for (var i = 1; i < datas.size(); i++) {
            assertThat(datas.get(i)).as("rodada %d", i + 1).isAfter(datas.get(i - 1));
        }
    }

    @Test
    void deveCaberTrintaEOitoRodadasDentroDaJanelaDaEdicao() {
        // É a razão de a rodada de meio de semana existir: ela ocupa a quarta da mesma
        // semana de um domingo, em vez de consumir uma semana só para si.
        var datas = TipadorDeRodadas.datasAlvo(INICIO, TipadorDeRodadas.tipar(38, JANELA_DO_MUNDO));
        assertThat(datas.getLast()).isBeforeOrEqualTo(FIM);
    }

    @Test
    void deveDarTresDiasDeFolgaAoRedorDaRodadaDeMeioDeSemana() {
        var tipos = TipadorDeRodadas.tipar(38, JANELA_DO_MUNDO);
        var datas = TipadorDeRodadas.datasAlvo(INICIO, tipos);

        for (var i = 1; i < datas.size(); i++) {
            assertThat(ChronoUnit.DAYS.between(datas.get(i - 1), datas.get(i)))
                    .as("intervalo antes da rodada %d", i + 1)
                    .isGreaterThanOrEqualTo(ConstantesDeCalendario.DESCANSO_MINIMO_EM_DIAS);
        }
    }
}
