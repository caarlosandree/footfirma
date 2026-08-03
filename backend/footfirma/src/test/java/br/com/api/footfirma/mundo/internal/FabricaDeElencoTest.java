package br.com.api.footfirma.mundo.internal;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class FabricaDeElencoTest {

    private static final ClubeGerado CELEIRO = new ClubeGerado(
            "celeiro", "Celeiro Futebol Clube", "Celeiro", "Fábrica", 1930,
            "Sorocaba", "SP", "Arena Celeiro", 20_000, 1970,
            "#C62828", "#FFFFFF", 56, 38, 91, 2);

    private static final ClubeGerado POTENCIA = new ClubeGerado(
            "potencia", "Potência Futebol Clube", "Potência", "Gigante", 1910,
            "São Paulo", "SP", "Arena Potência", 60_000, 1980,
            "#1565C0", "#FFFFFF", 86, 90, 62, 1);

    @Test
    void deveGerarVinteESeisProfissionaisEDozeDaBase() {
        var elenco = gerar(POTENCIA);

        assertThat(elenco).hasSize(38);
        assertThat(elenco).filteredOn(j -> "PROFISSIONAL".equals(j.categoria())).hasSize(26);
        assertThat(elenco).filteredOn(j -> "BASE".equals(j.categoria())).hasSize(12);
    }

    @Test
    void deveCobrirAsNovePosicoesNoElencoProfissional() {
        var profissionais = gerar(POTENCIA).stream()
                .filter(j -> "PROFISSIONAL".equals(j.categoria()))
                .toList();

        assertThat(profissionais).extracting(JogadorGerado::posicao)
                .contains("GOL", "ZAG", "LTD", "LTE", "VOL", "MEC", "MEA", "PTA", "ATA");
        assertThat(profissionais).filteredOn(j -> "GOL".equals(j.posicao())).hasSize(3);
        assertThat(profissionais).filteredOn(j -> "ZAG".equals(j.posicao())).hasSize(5);
    }

    @Test
    void deveDarCamisasUnicasAosProfissionaisENenhumaAosDaBase() {
        var elenco = gerar(POTENCIA);

        assertThat(elenco).filteredOn(j -> "PROFISSIONAL".equals(j.categoria()))
                .extracting(JogadorGerado::numeroCamisa).doesNotHaveDuplicates();
        assertThat(elenco).filteredOn(j -> "BASE".equals(j.categoria()))
                .allSatisfy(j -> assertThat(j.numeroCamisa()).isNull());
    }

    @Test
    void deveTerEstrelaAcimaDaMediaDoProprioElenco() {
        var profissionais = gerar(CELEIRO).stream()
                .filter(j -> "PROFISSIONAL".equals(j.categoria()))
                .toList();
        var media = profissionais.stream().mapToInt(JogadorGerado::alvoOverall).average().orElseThrow();
        var melhor = profissionais.stream().mapToInt(JogadorGerado::alvoOverall).max().orElseThrow();

        assertThat(melhor).isGreaterThanOrEqualTo((int) media + 8);
    }

    @Test
    void deveEsconderJoiaDePotencialAltoNaBaseDeClubePobre() {
        var base = gerar(CELEIRO).stream()
                .filter(j -> "BASE".equals(j.categoria()))
                .toList();

        assertThat(base).filteredOn(j -> j.potencialBase() >= 88).isNotEmpty();
    }

    @Test
    void devePorPotencialNuncaAbaixoDoOverallAtual() {
        assertThat(gerar(POTENCIA)).allSatisfy(jogador ->
                assertThat(jogador.potencialBase()).isGreaterThanOrEqualTo(jogador.alvoOverall()));
    }

    @Test
    void deveGerarChavesNaturaisUnicasEntreClubes() {
        var chavesUsadas = new HashSet<String>();
        var aleatorio = new SplittableRandom(7L);
        var primeiro = FabricaDeElenco.gerar(POTENCIA, aleatorio, chavesUsadas);
        var segundo = FabricaDeElenco.gerar(CELEIRO, aleatorio, chavesUsadas);

        assertThat(chavesUsadas).hasSize(primeiro.size() + segundo.size());
    }

    private List<JogadorGerado> gerar(ClubeGerado clube) {
        return FabricaDeElenco.gerar(clube, new SplittableRandom(42L), new HashSet<>());
    }
}
