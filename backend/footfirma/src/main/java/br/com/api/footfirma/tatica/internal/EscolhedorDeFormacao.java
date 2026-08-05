package br.com.api.footfirma.tatica.internal;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Escolhe a formação do treinador de IA.
 *
 * <p>A skill TATICA entra como <b>repertório</b>, não como multiplicador: o treinador
 * avalia as {@code 1 + TATICA/2} primeiras formações do catálogo e fica com a de maior
 * soma de aptidão. Um multiplicador exigiria calibrar um coeficiente contra um motor de
 * partida que não existe; repertório não exige calibragem nenhuma — treinador de TATICA
 * baixa simplesmente só conhece o 4-4-2, e isso custa caro quando o elenco tem três
 * pontas.
 */
final class EscolhedorDeFormacao {

    private EscolhedorDeFormacao() {
    }

    record Escolha(FormacaoCandidata formacao, Onze onze) {
    }

    static int tamanhoDoRepertorio(int tatica) {
        return ConstantesDeTatica.REPERTORIO_BASE
                + Math.max(0, tatica) / ConstantesDeTatica.DIVISOR_DE_REPERTORIO;
    }

    /**
     * @param catalogo formações em ordem de catálogo — a primeira é a mais genérica.
     */
    static Optional<Escolha> escolher(List<FormacaoCandidata> catalogo, int tatica,
                                      List<JogadorDisponivel> elenco,
                                      TabelaDeOveralls overalls) {
        return catalogo.stream()
                .sorted(Comparator.comparingInt(FormacaoCandidata::ordem))
                .limit(tamanhoDoRepertorio(tatica))
                .flatMap(formacao -> PreenchedorDeSlots.preencher(formacao, elenco, overalls)
                        .map(onze -> new Escolha(formacao, onze))
                        .stream())
                // max devolve o último máximo; invertendo a ordem, o último máximo é o de
                // menor ordem de catálogo — que é o desempate que o spec pede.
                .max(Comparator.comparingInt((Escolha escolha) -> escolha.onze().somaDeAptidao())
                        .thenComparing(Comparator.comparingInt(
                                (Escolha escolha) -> escolha.formacao().ordem()).reversed()));
    }
}
