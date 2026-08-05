package br.com.api.footfirma.calendario.internal;

import br.com.api.footfirma.calendario.dto.RegrasDeDesempate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResolucaoDeConfrontoTest {

    static final long A = 10L;
    static final long B = 20L;

    static final RegrasDeDesempate TUDO_LIGADO = new RegrasDeDesempate(true, true, true, true);
    static final RegrasDeDesempate SO_AGREGADO = new RegrasDeDesempate(false, false, false, true);

    @Test
    void deveDarOVencedorPeloAgregadoQuandoNaoHaEmpate() {
        var jogos = List.of(new PlacarDoConfronto(A, B, 2, 1, null, null, null, null),
                            new PlacarDoConfronto(B, A, 1, 1, null, null, null, null));
        assertThat(ResolvedorDeConfronto.resolver(jogos, TUDO_LIGADO, A, B)).contains(A);
    }

    @Test
    void deveDecidirPeloGolForaComAgregadoEmpatado() {
        // Ida: A 2 x 1 B (B marcou 1 fora). Volta: B 1 x 0 A (A marcou 0 fora).
        // Agregado 2 a 2; B fez 1 gol fora, A fez 0. Vence B.
        var jogos = List.of(new PlacarDoConfronto(A, B, 2, 1, null, null, null, null),
                            new PlacarDoConfronto(B, A, 1, 0, null, null, null, null));
        assertThat(ResolvedorDeConfronto.resolver(jogos, TUDO_LIGADO, A, B)).contains(B);
    }

    @Test
    void naoDeveUsarGolForaQuandoARegraEstaDesligada() {
        var jogos = List.of(new PlacarDoConfronto(A, B, 2, 1, null, null, null, null),
                            new PlacarDoConfronto(B, A, 1, 0, null, null, null, null));
        assertThat(ResolvedorDeConfronto.resolver(jogos, SO_AGREGADO, A, B)).isEmpty();
    }

    @Test
    void deveUsarProrrogacaoAntesDosPenaltis() {
        var jogos = List.of(new PlacarDoConfronto(A, B, 1, 1, 1, 0, null, null));
        assertThat(ResolvedorDeConfronto.resolver(jogos, TUDO_LIGADO, A, B)).contains(A);
    }

    @Test
    void deveCairNosPenaltisQuandoAProrrogacaoTambemEmpata() {
        var jogos = List.of(new PlacarDoConfronto(A, B, 1, 1, 1, 1, 4, 5));
        assertThat(ResolvedorDeConfronto.resolver(jogos, TUDO_LIGADO, A, B)).contains(B);
    }

    @Test
    void deveDevolverVazioQuandoOConfrontoEmpataENadaMaisDecide() {
        var jogos = List.of(new PlacarDoConfronto(A, B, 1, 1, null, null, null, null));
        assertThat(ResolvedorDeConfronto.resolver(jogos, SO_AGREGADO, A, B)).isEmpty();
    }

    @Test
    void deveDevolverVazioQuandoAindaFaltaJogo() {
        assertThat(ResolvedorDeConfronto.resolver(List.of(), TUDO_LIGADO, A, B)).isEmpty();
    }
}
