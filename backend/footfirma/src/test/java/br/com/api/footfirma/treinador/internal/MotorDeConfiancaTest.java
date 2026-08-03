package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.treinador.domain.StatusConfianca;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MotorDeConfiancaTest {

    private static final int SEM_LIDERANCA = 1;
    private static final int LIDERANCA_MAXIMA = 10;

    @Test
    void devePunirForteIndiscutivelNoBancoComTreinadorSemLideranca() {
        var delta = MotorDeConfianca.delta(
                new FatoDeMinutagem(StatusConfianca.INDISCUTIVEL, 0, SEM_LIDERANCA));

        assertThat(delta).isCloseTo(-3.2, within(0.1));
    }

    @Test
    void deveAmortecerQuedaQuandoTreinadorTemLiderancaMaxima() {
        var delta = MotorDeConfianca.delta(
                new FatoDeMinutagem(StatusConfianca.INDISCUTIVEL, 0, LIDERANCA_MAXIMA));

        assertThat(delta).isCloseTo(-1.4, within(0.1));
    }

    @Test
    void devePremiarPromessaQueJogouAPartidaInteira() {
        var delta = MotorDeConfianca.delta(
                new FatoDeMinutagem(StatusConfianca.PROMESSA, 90, SEM_LIDERANCA));

        assertThat(delta).isEqualTo(2.0);
    }

    @Test
    void naoDeveMexerNaMoralDeQuemFoiAvisadoQueNaoJoga() {
        var delta = MotorDeConfianca.delta(
                new FatoDeMinutagem(StatusConfianca.FORA_DOS_PLANOS, 0, SEM_LIDERANCA));

        assertThat(delta).isEqualTo(0.0);
    }

    @Test
    void naoDeveAmortecerDeltaPositivoComLideranca() {
        var comLideranca = MotorDeConfianca.delta(
                new FatoDeMinutagem(StatusConfianca.PROMESSA, 60, LIDERANCA_MAXIMA));
        var semLideranca = MotorDeConfianca.delta(
                new FatoDeMinutagem(StatusConfianca.PROMESSA, 60, SEM_LIDERANCA));

        assertThat(comLideranca).isEqualTo(semLideranca);
    }

    @Test
    void deveCobrarDozePontosAoRebaixarIndiscutivelParaForaDosPlanos() {
        var delta = MotorDeConfianca.deltaPorMudancaDeStatus(
                StatusConfianca.INDISCUTIVEL, StatusConfianca.FORA_DOS_PLANOS);

        assertThat(delta).isEqualTo(-12.0);
    }

    @Test
    void devePagarDozePontosAoPromoverForaDosPlanosParaIndiscutivel() {
        var delta = MotorDeConfianca.deltaPorMudancaDeStatus(
                StatusConfianca.FORA_DOS_PLANOS, StatusConfianca.INDISCUTIVEL);

        assertThat(delta).isEqualTo(12.0);
    }

    @Test
    void naoDeveCobrarNadaQuandoOStatusNaoMuda() {
        var delta = MotorDeConfianca.deltaPorMudancaDeStatus(
                StatusConfianca.ROTACAO, StatusConfianca.ROTACAO);

        assertThat(delta).isEqualTo(0.0);
    }

    @Test
    void devePunirMaisDoQuePremiarNaMesmaDistanciaDeMinutos() {
        var frustracao = MotorDeConfianca.delta(
                new FatoDeMinutagem(StatusConfianca.INDISCUTIVEL, 0, SEM_LIDERANCA));
        var satisfacao = MotorDeConfianca.delta(
                new FatoDeMinutagem(StatusConfianca.FORA_DOS_PLANOS, 85, SEM_LIDERANCA));

        assertThat(Math.abs(frustracao)).isGreaterThan(Math.abs(satisfacao));
    }

    @Test
    void deveManterMoralDentroDaFaixaZeroNoventaENove() {
        var abandono = new FatoDeMinutagem(StatusConfianca.INDISCUTIVEL, 0, SEM_LIDERANCA);

        assertThat(MotorDeConfianca.aplicar(1.0, abandono)).isBetween(0.0, 99.0);
        assertThat(MotorDeConfianca.aplicar(98.5,
                new FatoDeMinutagem(StatusConfianca.PROMESSA, 90, SEM_LIDERANCA)))
                .isBetween(0.0, 99.0);
    }
}
