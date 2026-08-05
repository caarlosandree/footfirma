package br.com.api.footfirma.tatica.internal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PreenchimentoDeSlotsTest {

    static final long GOL = 1L;
    static final long ZAG = 2L;
    static final long ATA = 9L;

    /** Formação de três slots, um por posição. Suficiente para provar a regra. */
    static final FormacaoCandidata TRES_SLOTS =
            new FormacaoCandidata(1L, 1, List.of(GOL, ZAG, ATA));

    @Test
    void devePreencherAntesOSlotMaisEscasso() {
        // O jogador 1 é o melhor tanto em ZAG (80) quanto em ATA (79). Em ZAG o segundo
        // melhor é 40 — escassez 40; em ATA é 78 — escassez 1. O guloso por escassez
        // precisa dar o jogador 1 ao ZAG, não ao ATA.
        var elenco = List.of(
                disponivel(1, ZAG), disponivel(2, ZAG), disponivel(3, ATA), disponivel(4, GOL));
        var overalls = tabela(Map.of(
                1L, Map.of(GOL, 10, ZAG, 80, ATA, 79),
                2L, Map.of(GOL, 10, ZAG, 40, ATA, 20),
                3L, Map.of(GOL, 10, ZAG, 30, ATA, 78),
                4L, Map.of(GOL, 70, ZAG, 10, ATA, 10)));

        var onze = PreenchedorDeSlots.preencher(TRES_SLOTS, elenco, overalls).orElseThrow();

        assertThat(jogadorNoSlot(onze, 2)).as("slot ZAG").isEqualTo(1L);
        assertThat(jogadorNoSlot(onze, 3)).as("slot ATA").isEqualTo(3L);
        assertThat(onze.somaDeAptidao()).isEqualTo(70 + 80 + 78);
    }

    @Test
    void deveDesempatarPeloMenorIdQuandoAsAptidoesEmpatam() {
        var elenco = List.of(
                disponivel(9, ZAG), disponivel(4, ZAG), disponivel(7, GOL), disponivel(8, ATA));
        var overalls = tabela(Map.of(
                9L, Map.of(GOL, 10, ZAG, 50, ATA, 10),
                4L, Map.of(GOL, 10, ZAG, 50, ATA, 10),
                7L, Map.of(GOL, 60, ZAG, 10, ATA, 10),
                8L, Map.of(GOL, 10, ZAG, 10, ATA, 60)));

        var onze = PreenchedorDeSlots.preencher(TRES_SLOTS, elenco, overalls).orElseThrow();

        assertThat(jogadorNoSlot(onze, 2)).isEqualTo(4L);
    }

    @Test
    void naoDeveUsarJogadorDaBaseQuandoOsProfissionaisFechamOTime() {
        var elenco = List.of(
                disponivel(1, GOL), disponivel(2, ZAG), disponivel(3, ATA), daBase(4, ATA));
        var overalls = tabela(Map.of(
                1L, Map.of(GOL, 60, ZAG, 10, ATA, 10),
                2L, Map.of(GOL, 10, ZAG, 60, ATA, 10),
                3L, Map.of(GOL, 10, ZAG, 10, ATA, 50),
                4L, Map.of(GOL, 10, ZAG, 10, ATA, 99)));

        var onze = PreenchedorDeSlots.preencher(TRES_SLOTS, elenco, overalls).orElseThrow();

        assertThat(jogadorNoSlot(onze, 3))
                .as("o garoto de 99 fica fora porque os profissionais fecham o time")
                .isEqualTo(3L);
    }

    @Test
    void deveUsarJogadorDaBaseQuandoOsProfissionaisNaoFecham() {
        var elenco = List.of(disponivel(1, GOL), disponivel(2, ZAG), daBase(4, ATA));
        var overalls = tabela(Map.of(
                1L, Map.of(GOL, 60, ZAG, 10, ATA, 10),
                2L, Map.of(GOL, 10, ZAG, 60, ATA, 10),
                4L, Map.of(GOL, 10, ZAG, 10, ATA, 55)));

        var onze = PreenchedorDeSlots.preencher(TRES_SLOTS, elenco, overalls).orElseThrow();

        assertThat(jogadorNoSlot(onze, 3)).isEqualTo(4L);
    }

    @Test
    void deveVoltarVazioQuandoNaoHaJogadoresSuficientes() {
        var elenco = List.of(disponivel(1, GOL), disponivel(2, ZAG));
        var overalls = tabela(Map.of(
                1L, Map.of(GOL, 60, ZAG, 10, ATA, 10),
                2L, Map.of(GOL, 10, ZAG, 60, ATA, 10)));

        assertThat(PreenchedorDeSlots.preencher(TRES_SLOTS, elenco, overalls)).isEmpty();
    }

    private static long jogadorNoSlot(Onze onze, int slotOrdem) {
        return onze.slots().stream()
                .filter(slot -> slot.slotOrdem() == slotOrdem)
                .findFirst().orElseThrow().jogadorId();
    }

    private static JogadorDisponivel disponivel(long id, long posicaoPrincipal) {
        return new JogadorDisponivel(id, posicaoPrincipal, false);
    }

    private static JogadorDisponivel daBase(long id, long posicaoPrincipal) {
        return new JogadorDisponivel(id, posicaoPrincipal, true);
    }

    private static TabelaDeOveralls tabela(Map<Long, Map<Long, Integer>> valores) {
        return new TabelaDeOveralls(valores);
    }
}
