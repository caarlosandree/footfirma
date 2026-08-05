package br.com.api.footfirma.calendario.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SorteioDeChaveamentoTest {

    @ParameterizedTest(name = "{0} clubes geram {1} confrontos")
    @CsvSource({"2, 1", "4, 3", "8, 7", "16, 15"})
    void deveGerarUmConfrontoAMenosQueParticipantes(int participantes, int confrontos) {
        assertThat(SorteioDeChaveamento.montar(participantes, new SplittableRandom(1)))
                .hasSize(confrontos);
    }

    @Test
    void devePreencherApenasAPrimeiraFaseComClubes() {
        var chave = SorteioDeChaveamento.montar(8, new SplittableRandom(1));
        assertThat(chave.stream().filter(c -> c.ladoA() != null)).hasSize(4);
        assertThat(chave.stream().filter(c -> c.origemA() != null)).hasSize(3);
    }

    @Test
    void deveApontarCadaConfrontoPosteriorParaDoisAnteriores() {
        var chave = SorteioDeChaveamento.montar(8, new SplittableRandom(1));
        for (var confronto : chave.stream().filter(c -> c.origemA() != null).toList()) {
            assertThat(confronto.origemA()).isLessThan(confronto.ordem());
            assertThat(confronto.origemB()).isLessThan(confronto.ordem());
            assertThat(confronto.origemA()).isNotEqualTo(confronto.origemB());
        }
    }

    @Test
    void deveUsarCadaClubeUmaVezSoNaPrimeiraFase() {
        var chave = SorteioDeChaveamento.montar(8, new SplittableRandom(1));
        var usados = new HashSet<Integer>();
        chave.stream().filter(c -> c.ladoA() != null).forEach(confronto -> {
            assertThat(usados.add(confronto.ladoA())).isTrue();
            assertThat(usados.add(confronto.ladoB())).isTrue();
        });
        assertThat(usados).hasSize(8);
    }

    @Test
    void deveConsumirCadaConfrontoAnteriorExatamenteUmaVez() {
        // Sem isso, dois confrontos poderiam esperar o mesmo vencedor e a árvore não
        // seria uma árvore.
        var chave = SorteioDeChaveamento.montar(16, new SplittableRandom(5));
        var consumidos = new HashSet<Integer>();
        chave.stream().filter(c -> c.origemA() != null).forEach(confronto -> {
            assertThat(consumidos.add(confronto.origemA())).isTrue();
            assertThat(consumidos.add(confronto.origemB())).isTrue();
        });
        assertThat(consumidos).hasSize(14);
    }

    @Test
    void deveTerExatamenteUmConfrontoQueNinguemConsome() {
        // A final: nenhum confronto aponta para ela.
        var chave = SorteioDeChaveamento.montar(8, new SplittableRandom(2));
        var consumidos = new HashSet<Integer>();
        chave.forEach(confronto -> {
            if (confronto.origemA() != null) {
                consumidos.add(confronto.origemA());
                consumidos.add(confronto.origemB());
            }
        });
        var finais = chave.stream().filter(c -> !consumidos.contains(c.ordem())).toList();
        assertThat(finais).hasSize(1);
        assertThat(finais.getFirst().ordem()).isEqualTo(7);
    }

    @Test
    void deveRecusarNumeroQueNaoEPotenciaDeDois() {
        assertThatThrownBy(() -> SorteioDeChaveamento.montar(6, new SplittableRandom(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("potência");
    }

    @Test
    void deveRepetirOChaveamentoComAMesmaSemente() {
        assertThat(SorteioDeChaveamento.montar(8, new SplittableRandom(9)))
                .isEqualTo(SorteioDeChaveamento.montar(8, new SplittableRandom(9)));
    }

    @Test
    void deveProduzirChaveamentoDiferenteComSementeDiferente() {
        assertThat(SorteioDeChaveamento.montar(16, new SplittableRandom(1)))
                .isNotEqualTo(SorteioDeChaveamento.montar(16, new SplittableRandom(2)));
    }
}
