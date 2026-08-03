package br.com.api.footfirma.treinador.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PoliticaDeDemissaoTest {

    private static final int CLUBE_MEDIO = 50;
    private static final int GIGANTE = 88;
    private static final int CLUBE_PEQUENO = 35;
    private static final int APOS_A_CARENCIA = 20;

    @Test
    void deveManterSeguroQuandoMoralEstaConfortavel() {
        assertThat(PoliticaDeDemissao.avaliar(60.0, CLUBE_MEDIO, APOS_A_CARENCIA))
                .isEqualTo(Veredito.SEGURO);
    }

    @Test
    void deveSinalizarPressaoQuandoMoralEntraNaZonaIntermediaria() {
        assertThat(PoliticaDeDemissao.avaliar(20.0, CLUBE_MEDIO, APOS_A_CARENCIA))
                .isEqualTo(Veredito.SOB_PRESSAO);
    }

    @Test
    void deveDemitirQuandoMoralCaiAbaixoDoLimiar() {
        assertThat(PoliticaDeDemissao.avaliar(10.0, CLUBE_MEDIO, APOS_A_CARENCIA))
                .isEqualTo(Veredito.DEMITIDO);
    }

    @Test
    void naoDeveDemitirDentroDaCarenciaDeCincoPartidas() {
        assertThat(PoliticaDeDemissao.avaliar(2.0, CLUBE_MEDIO, 4))
                .isNotEqualTo(Veredito.DEMITIDO);
    }

    @Test
    void deveVoltarADemitirNaPrimeiraPartidaAposACarencia() {
        assertThat(PoliticaDeDemissao.avaliar(2.0, CLUBE_MEDIO, 5))
                .isEqualTo(Veredito.DEMITIDO);
    }

    @Test
    void deveDemitirNoGiganteEManterNoClubePequenoComAMesmaMoral() {
        var moral = 16.0;

        assertThat(PoliticaDeDemissao.avaliar(moral, GIGANTE, APOS_A_CARENCIA))
                .isEqualTo(Veredito.DEMITIDO);
        assertThat(PoliticaDeDemissao.avaliar(moral, CLUBE_PEQUENO, APOS_A_CARENCIA))
                .isNotEqualTo(Veredito.DEMITIDO);
    }
}
