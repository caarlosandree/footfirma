package br.com.api.footfirma.tatica.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EscolhaDeFormacaoTest {

    static final long GOL = 1L;
    static final long ZAG = 2L;
    static final long ATA = 9L;

    @ParameterizedTest(name = "TATICA {0} avalia {1} formações")
    @CsvSource({"1, 1", "2, 2", "3, 2", "4, 3", "5, 3", "6, 4", "7, 4", "8, 5", "9, 5", "10, 6"})
    void deveCrescerORepertorioComATatica(int tatica, int esperado) {
        assertThat(EscolhedorDeFormacao.tamanhoDoRepertorio(tatica)).isEqualTo(esperado);
    }

    @Test
    void deveCairNoRepertorioMinimoQuandoATaticaEZero() {
        assertThat(EscolhedorDeFormacao.tamanhoDoRepertorio(0)).isEqualTo(1);
    }

    @Test
    void deveIgnorarFormacaoForaDoRepertorio() {
        // A segunda formação é estritamente melhor para este elenco, mas TATICA 1 só
        // enxerga a primeira.
        var elenco = List.of(disponivel(1, GOL), disponivel(2, ZAG), disponivel(3, ATA));
        var overalls = new TabelaDeOveralls(Map.of(
                1L, Map.of(GOL, 60, ZAG, 10, ATA, 10),
                2L, Map.of(GOL, 10, ZAG, 20, ATA, 90),
                3L, Map.of(GOL, 10, ZAG, 20, ATA, 90)));

        var escolha = EscolhedorDeFormacao.escolher(
                List.of(defensiva(), ofensiva()), 1, elenco, overalls).orElseThrow();

        assertThat(escolha.formacao().formacaoId()).isEqualTo(defensiva().formacaoId());
    }

    @Test
    void deveEscolherAFormacaoDeMaiorSomaDentroDoRepertorio() {
        var elenco = List.of(disponivel(1, GOL), disponivel(2, ZAG), disponivel(3, ATA));
        var overalls = new TabelaDeOveralls(Map.of(
                1L, Map.of(GOL, 60, ZAG, 10, ATA, 10),
                2L, Map.of(GOL, 10, ZAG, 20, ATA, 90),
                3L, Map.of(GOL, 10, ZAG, 20, ATA, 90)));

        var escolha = EscolhedorDeFormacao.escolher(
                List.of(defensiva(), ofensiva()), 10, elenco, overalls).orElseThrow();

        assertThat(escolha.formacao().formacaoId()).isEqualTo(ofensiva().formacaoId());
    }

    @Test
    void deveDesempatarPelaOrdemDoCatalogo() {
        var elenco = List.of(disponivel(1, GOL), disponivel(2, ZAG), disponivel(3, ATA));
        // Simétrico: as duas formações somam o mesmo.
        var overalls = new TabelaDeOveralls(Map.of(
                1L, Map.of(GOL, 50, ZAG, 50, ATA, 50),
                2L, Map.of(GOL, 50, ZAG, 50, ATA, 50),
                3L, Map.of(GOL, 50, ZAG, 50, ATA, 50)));

        var escolha = EscolhedorDeFormacao.escolher(
                List.of(defensiva(), ofensiva()), 10, elenco, overalls).orElseThrow();

        assertThat(escolha.formacao().ordem()).isEqualTo(1);
    }

    @Test
    void deveVoltarVazioQuandoNenhumaFormacaoFecha() {
        var elenco = List.of(disponivel(1, GOL));
        var overalls = new TabelaDeOveralls(Map.of(1L, Map.of(GOL, 60, ZAG, 10, ATA, 10)));

        assertThat(EscolhedorDeFormacao.escolher(
                List.of(defensiva(), ofensiva()), 10, elenco, overalls)).isEmpty();
    }

    private static FormacaoCandidata defensiva() {
        return new FormacaoCandidata(1L, 1, List.of(GOL, ZAG, ZAG));
    }

    private static FormacaoCandidata ofensiva() {
        return new FormacaoCandidata(2L, 2, List.of(GOL, ATA, ATA));
    }

    private static JogadorDisponivel disponivel(long id, long posicaoPrincipal) {
        return new JogadorDisponivel(id, posicaoPrincipal, false);
    }
}
