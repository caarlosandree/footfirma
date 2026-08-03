package br.com.api.footfirma.treinador.internal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CalculadoraDeExpectativaTest {

    private static final List<Integer> DIVISAO = List.of(88, 75, 62, 50, 35);

    @Test
    void deveDarMetaDePrimeiroAoClubeMaisReputadoDaDivisao() {
        assertThat(CalculadoraDeExpectativa.metaPosicao(88, DIVISAO)).isEqualTo(1);
    }

    @Test
    void deveDarMetaDeUltimoAoClubeMenosReputadoDaDivisao() {
        assertThat(CalculadoraDeExpectativa.metaPosicao(35, DIVISAO)).isEqualTo(5);
    }

    @Test
    void deveDarMetaIntermediariaAoClubeDoMeioDaTabela() {
        assertThat(CalculadoraDeExpectativa.metaPosicao(62, DIVISAO)).isEqualTo(3);
    }

    @Test
    void deveDarAMesmaMetaAClubesDeReputacaoEmpatada() {
        var comEmpate = List.of(88, 60, 60, 35);

        assertThat(CalculadoraDeExpectativa.metaPosicao(60, comEmpate)).isEqualTo(2);
    }

    @Test
    void deveDarMoralAltaQuandoVencedorAssumeClubeMedio() {
        assertThat(CalculadoraDeExpectativa.moralInicialDoVinculo(85, 40))
                .isCloseTo(68.0, within(0.1));
    }

    @Test
    void deveDarMoralBaixaQuandoDesconhecidoAssumeGigante() {
        assertThat(CalculadoraDeExpectativa.moralInicialDoVinculo(40, 85))
                .isCloseTo(32.0, within(0.1));
    }

    @Test
    void deveDarMoralNeutraQuandoReputacoesSaoParelhas() {
        assertThat(CalculadoraDeExpectativa.moralInicialDoVinculo(60, 60))
                .isCloseTo(50.0, within(0.1));
    }

    @Test
    void deveLimitarMoralInicialDoVinculoEntreVinteCincoEOitentaECinco() {
        assertThat(CalculadoraDeExpectativa.moralInicialDoVinculo(99, 0)).isEqualTo(85.0);
        assertThat(CalculadoraDeExpectativa.moralInicialDoVinculo(0, 99)).isEqualTo(25.0);
    }

    @Test
    void deveAdiantarMoralDoJogadorQuandoJaFoiPupilo() {
        assertThat(CalculadoraDeExpectativa.moralInicialDoJogador(90.0))
                .isCloseTo(74.0, within(0.1));
    }

    @Test
    void deveDarMoralNeutraQuandoJogadorNuncaFoiDirigido() {
        assertThat(CalculadoraDeExpectativa.moralInicialDoJogador(50.0))
                .isCloseTo(50.0, within(0.1));
    }

    @Test
    void devePenalizarMoralDoJogadorQueFoiQueimadoNoBanco() {
        assertThat(CalculadoraDeExpectativa.moralInicialDoJogador(20.0))
                .isCloseTo(32.0, within(0.1));
    }

    @Test
    void deveLimitarMoralInicialDoJogadorEntreVinteENoventa() {
        assertThat(CalculadoraDeExpectativa.moralInicialDoJogador(99.0)).isLessThanOrEqualTo(90.0);
        assertThat(CalculadoraDeExpectativa.moralInicialDoJogador(0.0)).isGreaterThanOrEqualTo(20.0);
    }
}
