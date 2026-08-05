package br.com.api.footfirma.calendario.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TabelaDeBergerTest {

    @ParameterizedTest(name = "{0} participantes geram {0}-1 rodadas de turno")
    @ValueSource(ints = {4, 8, 20})
    void deveGerarUmaRodadaAMenosQueParticipantes(int participantes) {
        assertThat(TabelaDeBerger.gerar(participantes, false)).hasSize(participantes - 1);
    }

    @Test
    void deveDobrarAsRodadasComReturno() {
        assertThat(TabelaDeBerger.gerar(20, true)).hasSize(38);
    }

    @Test
    void deveFazerCadaUmEnfrentarTodosOsOutrosUmaVezNoTurno() {
        var pares = new HashSet<String>();
        for (var rodada : TabelaDeBerger.gerar(20, false)) {
            for (var jogo : rodada) {
                var chave = Math.min(jogo.mandante(), jogo.visitante())
                        + "-" + Math.max(jogo.mandante(), jogo.visitante());
                assertThat(pares.add(chave))
                        .as("par %s repetido no turno", chave).isTrue();
            }
        }
        assertThat(pares).hasSize(20 * 19 / 2);
    }

    @Test
    void naoDeveEscalarNinguemDuasVezesNaMesmaRodada() {
        for (var rodada : TabelaDeBerger.gerar(20, false)) {
            var vistos = new HashSet<Integer>();
            for (var jogo : rodada) {
                assertThat(vistos.add(jogo.mandante())).isTrue();
                assertThat(vistos.add(jogo.visitante())).isTrue();
            }
            assertThat(rodada).hasSize(10);
        }
    }

    @Test
    void deveInverterOMandoNoReturno() {
        var completo = TabelaDeBerger.gerar(20, true);
        var turno = completo.subList(0, 19);
        var returno = completo.subList(19, 38);

        for (var i = 0; i < 19; i++) {
            for (var j = 0; j < 10; j++) {
                var ida = turno.get(i).get(j);
                var volta = returno.get(i).get(j);
                assertThat(volta.mandante()).isEqualTo(ida.visitante());
                assertThat(volta.visitante()).isEqualTo(ida.mandante());
            }
        }
    }

    @Test
    void deveDistribuirOMandoQuaseIgualmenteNoTurno() {
        var mandos = new int[20];
        TabelaDeBerger.gerar(20, false)
                .forEach(rodada -> rodada.forEach(jogo -> mandos[jogo.mandante()]++));
        // 19 rodadas ímpares não dividem por igual; a diferença aceitável é 1.
        for (var mando : mandos) {
            assertThat(mando).isBetween(9, 10);
        }
    }

    @Test
    void deveDarMandoEVisitanteIgualNoTotalComReturno() {
        var mandos = new int[20];
        TabelaDeBerger.gerar(20, true)
                .forEach(rodada -> rodada.forEach(jogo -> mandos[jogo.mandante()]++));
        // Com returno espelhado, todo clube manda exatamente 19 vezes.
        assertThat(mandos).containsOnly(19);
    }

    @Test
    void deveRecusarNumeroImparDeParticipantes() {
        assertThatThrownBy(() -> TabelaDeBerger.gerar(19, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("par");
    }

    @Test
    void deveRecusarMenosDeDoisParticipantes() {
        assertThatThrownBy(() -> TabelaDeBerger.gerar(0, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
