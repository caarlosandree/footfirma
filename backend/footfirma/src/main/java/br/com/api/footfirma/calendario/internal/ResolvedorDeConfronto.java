package br.com.api.footfirma.calendario.internal;

import br.com.api.footfirma.calendario.dto.RegrasDeDesempate;

import java.util.List;
import java.util.Optional;

/**
 * Decide quem avançou, na ordem agregado → gol fora → prorrogação → pênaltis.
 *
 * <p>Cada etapa só roda quando a anterior empatou <strong>e</strong> a fase declara a
 * regra. Vazio significa "ainda não decidido" — o que em pontos corridos e grupos é o
 * estado permanente, porque ali quem avança sai da classificação.
 */
final class ResolvedorDeConfronto {

    private ResolvedorDeConfronto() {
    }

    static Optional<Long> resolver(List<PlacarDoConfronto> jogos, RegrasDeDesempate regras,
                                   long clubeA, long clubeB) {
        if (jogos.isEmpty()) {
            return Optional.empty();
        }

        var golsDeA = 0;
        var golsDeB = 0;
        var foraDeA = 0;
        var foraDeB = 0;

        for (var jogo : jogos) {
            var deCasa = jogo.golsMandante();
            var deFora = jogo.golsVisitante();
            if (jogo.mandanteId() == clubeA) {
                golsDeA += deCasa;
                golsDeB += deFora;
                foraDeB += deFora;
            } else {
                golsDeB += deCasa;
                golsDeA += deFora;
                foraDeA += deFora;
            }
        }

        if (golsDeA != golsDeB) {
            return Optional.of(golsDeA > golsDeB ? clubeA : clubeB);
        }
        // Gol fora só desempata confronto de ida e volta. Num jogo único apenas um dos
        // dois jogou fora, e comparar seria dar a vitória a quem visitou.
        if (regras.temGolFora() && jogos.size() > 1 && foraDeA != foraDeB) {
            return Optional.of(foraDeA > foraDeB ? clubeA : clubeB);
        }

        var ultimo = jogos.getLast();
        if (regras.temProrrogacao() && ultimo.prorrogacaoMandante() != null
                && !ultimo.prorrogacaoMandante().equals(ultimo.prorrogacaoVisitante())) {
            var mandanteVenceu = ultimo.prorrogacaoMandante() > ultimo.prorrogacaoVisitante();
            return Optional.of(mandanteVenceu ? ultimo.mandanteId() : ultimo.visitanteId());
        }
        if (regras.temPenaltis() && ultimo.penaltisMandante() != null
                && !ultimo.penaltisMandante().equals(ultimo.penaltisVisitante())) {
            var mandanteVenceu = ultimo.penaltisMandante() > ultimo.penaltisVisitante();
            return Optional.of(mandanteVenceu ? ultimo.mandanteId() : ultimo.visitanteId());
        }
        return Optional.empty();
    }
}
