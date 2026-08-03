package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.treinador.domain.Resultado;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * O Celeiro vale 35 de reputação, o Gigante 88. Os valores esperados são os aprovados no
 * design — se mudarem, foi decisão de balanceamento, não refactor.
 */
class MotorDeMoralTest {

    private static final int CELEIRO = 35;
    private static final int GIGANTE = 88;

    private static FatoDeResultado fato(int meuClube, int adversario, Resultado resultado) {
        return new FatoDeResultado(meuClube, adversario, resultado, 10, 10);
    }

    @Test
    void deveDarMoralAltaQuandoClubePequenoVenceGigante() {
        var delta = MotorDeMoral.delta(fato(CELEIRO, GIGANTE, Resultado.VITORIA));

        assertThat(delta).isCloseTo(5.9, within(0.1));
    }

    @Test
    void deveDarMoralBaixaQuandoGiganteVenceClubePequeno() {
        var delta = MotorDeMoral.delta(fato(GIGANTE, CELEIRO, Resultado.VITORIA));

        assertThat(delta).isCloseTo(0.8, within(0.1));
    }

    @Test
    void devePunirGiganteQuandoEmpataComClubePequeno() {
        var delta = MotorDeMoral.delta(fato(GIGANTE, CELEIRO, Resultado.EMPATE));

        assertThat(delta).isCloseTo(-3.5, within(0.1));
    }

    @Test
    void devePremiarClubePequenoQuandoEmpataComGigante() {
        var delta = MotorDeMoral.delta(fato(CELEIRO, GIGANTE, Resultado.EMPATE));

        assertThat(delta).isCloseTo(1.7, within(0.1));
    }

    @Test
    void devePunirPoucoQuandoClubePequenoPerdeParaGigante() {
        var delta = MotorDeMoral.delta(fato(CELEIRO, GIGANTE, Resultado.DERROTA));

        assertThat(delta).isCloseTo(-0.8, within(0.1));
    }

    @Test
    void devePunirMuitoQuandoGigantePerdeParaClubePequeno() {
        var delta = MotorDeMoral.delta(fato(GIGANTE, CELEIRO, Resultado.DERROTA));

        assertThat(delta).isCloseTo(-5.9, within(0.1));
    }

    @Test
    void deveAmplificarApenasDeltaNegativoQuandoCampanhaEstaAbaixoDaMeta() {
        var atrasado = new FatoDeResultado(50, 50, Resultado.DERROTA, 12, 4);
        var noAlvo = new FatoDeResultado(50, 50, Resultado.DERROTA, 4, 4);

        assertThat(MotorDeMoral.delta(atrasado)).isLessThan(MotorDeMoral.delta(noAlvo));
    }

    @Test
    void naoDeveAmplificarDeltaPositivoQuandoCampanhaEstaAbaixoDaMeta() {
        var atrasado = new FatoDeResultado(50, 50, Resultado.VITORIA, 12, 4);
        var noAlvo = new FatoDeResultado(50, 50, Resultado.VITORIA, 4, 4);

        assertThat(MotorDeMoral.delta(atrasado)).isEqualTo(MotorDeMoral.delta(noAlvo));
    }

    @Test
    void deveDarCreditoQuandoCampanhaEstaAcimaDaMeta() {
        var liderando = new FatoDeResultado(50, 50, Resultado.DERROTA, 1, 4);
        var noAlvo = new FatoDeResultado(50, 50, Resultado.DERROTA, 4, 4);

        assertThat(liderando.posicaoAtual()).isLessThan(noAlvo.posicaoAtual());
        assertThat(MotorDeMoral.delta(liderando)).isGreaterThan(MotorDeMoral.delta(noAlvo));
    }

    @Test
    void deveManterMoralDentroDaFaixaZeroNoventaENove() {
        var goleada = fato(GIGANTE, CELEIRO, Resultado.DERROTA);

        assertThat(MotorDeMoral.aplicar(2.0, goleada)).isBetween(0.0, 99.0);
        assertThat(MotorDeMoral.aplicar(98.0, fato(CELEIRO, GIGANTE, Resultado.VITORIA)))
                .isBetween(0.0, 99.0);
    }

    @Test
    void deveSerSimetricoEntreVitoriaEDerrotaDoMesmoConfronto() {
        var vitoriaDoPequeno = MotorDeMoral.delta(fato(CELEIRO, GIGANTE, Resultado.VITORIA));
        var derrotaDoGrande = MotorDeMoral.delta(fato(GIGANTE, CELEIRO, Resultado.DERROTA));

        assertThat(vitoriaDoPequeno).isCloseTo(-derrotaDoGrande, within(0.01));
    }
}
