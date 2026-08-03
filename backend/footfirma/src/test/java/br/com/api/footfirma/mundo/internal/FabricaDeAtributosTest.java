package br.com.api.footfirma.mundo.internal;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class FabricaDeAtributosTest {

    @Test
    void deveManterTodaSkillNaFaixaValida() {
        var atributos = FabricaDeAtributos.gerar("MEC", 74, new SplittableRandom(1L));

        assertThat(atributos.todas().values()).allSatisfy(valor ->
                assertThat(valor).isBetween(1, 99));
    }

    @Test
    void deveConcentrarQualidadeNasSkillsDeGoleiroQuandoAPosicaoEGol() {
        var atributos = FabricaDeAtributos.gerar("GOL", 80, new SplittableRandom(1L));

        assertThat(atributos.golReflexo()).isBetween(77, 83);
        assertThat(atributos.golPosicionamento()).isBetween(77, 83);
        assertThat(atributos.finalizacao()).isLessThan(40);
    }

    @Test
    void deveDeixarSkillDeGoleiroBaixaEmJogadorDeLinha() {
        var atributos = FabricaDeAtributos.gerar("ATA", 82, new SplittableRandom(1L));

        assertThat(atributos.golReflexo()).isLessThan(30);
        assertThat(atributos.finalizacao()).isBetween(79, 85);
    }

    @Test
    void deveSerDeterministica() {
        var primeira = FabricaDeAtributos.gerar("ZAG", 70, new SplittableRandom(9L));
        var segunda = FabricaDeAtributos.gerar("ZAG", 70, new SplittableRandom(9L));

        assertThat(primeira).isEqualTo(segunda);
    }
}
