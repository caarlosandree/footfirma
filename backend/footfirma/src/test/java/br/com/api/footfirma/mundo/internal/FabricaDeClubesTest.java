package br.com.api.footfirma.mundo.internal;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class FabricaDeClubesTest {

    private static final long SEMENTE = 20260803L;

    @Test
    void deveGerarQuarentaClubesMetadeEmCadaDivisao() {
        var clubes = FabricaDeClubes.gerar(new SplittableRandom(SEMENTE));

        assertThat(clubes).hasSize(40);
        assertThat(clubes).filteredOn(clube -> clube.divisao() == 1).hasSize(20);
        assertThat(clubes).filteredOn(clube -> clube.divisao() == 2).hasSize(20);
    }

    @Test
    void deveGerarSlugsECidadesUnicos() {
        var clubes = FabricaDeClubes.gerar(new SplittableRandom(SEMENTE));

        assertThat(clubes).extracting(ClubeGerado::slug).doesNotHaveDuplicates();
        assertThat(clubes).extracting(ClubeGerado::cidade).doesNotHaveDuplicates();
    }

    @Test
    void deveRespeitarAsFaixasDoArquetipo() {
        var clubes = FabricaDeClubes.gerar(new SplittableRandom(SEMENTE));

        assertThat(clubes).allSatisfy(clube -> {
            assertThat(clube.reputacao()).isBetween(0, 99);
            assertThat(clube.forcaFinanceira()).isBetween(0, 99);
            assertThat(clube.qualidadeBase()).isBetween(0, 99);
        });
    }

    @Test
    void deveExistirCeleiroPobreComBaseExcelente() {
        var clubes = FabricaDeClubes.gerar(new SplittableRandom(SEMENTE));

        assertThat(clubes)
                .filteredOn(clube -> clube.forcaFinanceira() < 50 && clube.qualidadeBase() >= 85)
                .isNotEmpty();
    }

    @Test
    void deveExistirGiganteEndividadoComReputacaoAltaEDinheiroBaixo() {
        var clubes = FabricaDeClubes.gerar(new SplittableRandom(SEMENTE));

        assertThat(clubes)
                .filteredOn(clube -> clube.reputacao() >= 75 && clube.forcaFinanceira() <= 50)
                .isNotEmpty();
    }

    @Test
    void deveSerDeterministica() {
        var primeira = FabricaDeClubes.gerar(new SplittableRandom(SEMENTE));
        var segunda = FabricaDeClubes.gerar(new SplittableRandom(SEMENTE));

        assertThat(primeira).isEqualTo(segunda);
    }
}
