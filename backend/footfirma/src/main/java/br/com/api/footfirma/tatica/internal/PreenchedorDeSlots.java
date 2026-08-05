package br.com.api.footfirma.tatica.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Monta o melhor onze que esta formação permite, por guloso por escassez.
 *
 * <p>A cada rodada mede, para cada slot ainda vazio, a distância entre a melhor e a
 * segunda melhor aptidão disponível, e preenche primeiro o slot onde essa distância é
 * maior — é lá que errar custa mais caro. Não é ótimo: a atribuição húngara acertaria
 * casos que este erra. A troca está registrada no risco 3 do spec.
 *
 * <p>Jogador da base só é considerado quando os profissionais não fecham o time. A
 * decisão é por rodada, não global: basta faltar profissional para o slot da vez.
 */
final class PreenchedorDeSlots {

    private PreenchedorDeSlots() {
    }

    static Optional<Onze> preencher(FormacaoCandidata formacao,
                                    List<JogadorDisponivel> elenco,
                                    TabelaDeOveralls overalls) {
        if (elenco.size() < formacao.posicaoPorSlot().size()) {
            return Optional.empty();
        }

        var usados = new LinkedHashSet<Long>();
        var preenchidos = new ArrayList<SlotPreenchido>();
        var pendentes = new ArrayList<Integer>();
        for (int i = 1; i <= formacao.posicaoPorSlot().size(); i++) {
            pendentes.add(i);
        }

        while (!pendentes.isEmpty()) {
            var maisEscasso = pendentes.stream()
                    .max(Comparator.comparingInt(slot ->
                            escassez(formacao, slot, elenco, overalls, usados)))
                    .orElseThrow();

            var posicaoId = posicaoDoSlot(formacao, maisEscasso);
            var escolhido = melhorPara(posicaoId, elenco, overalls, usados);
            if (escolhido.isEmpty()) {
                return Optional.empty();
            }

            var jogadorId = escolhido.get().jogadorId();
            usados.add(jogadorId);
            preenchidos.add(new SlotPreenchido(maisEscasso, posicaoId, jogadorId,
                    overalls.overall(jogadorId, posicaoId)));
            pendentes.remove(Integer.valueOf(maisEscasso));
        }

        preenchidos.sort(Comparator.comparingInt(SlotPreenchido::slotOrdem));
        var soma = preenchidos.stream().mapToInt(SlotPreenchido::aptidao).sum();
        return Optional.of(new Onze(List.copyOf(preenchidos), soma));
    }

    /** Quanto se perde ao não dar a este slot o seu melhor jogador. */
    private static int escassez(FormacaoCandidata formacao, int slot,
                                List<JogadorDisponivel> elenco, TabelaDeOveralls overalls,
                                Set<Long> usados) {
        var posicaoId = posicaoDoSlot(formacao, slot);
        var aptidoes = candidatos(elenco, usados)
                .map(jogador -> overalls.overall(jogador.jogadorId(), posicaoId))
                .sorted(Comparator.reverseOrder())
                .limit(2)
                .toList();

        if (aptidoes.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        if (aptidoes.size() == 1) {
            return aptidoes.getFirst();
        }
        return aptidoes.get(0) - aptidoes.get(1);
    }

    private static Optional<JogadorDisponivel> melhorPara(long posicaoId,
                                                          List<JogadorDisponivel> elenco,
                                                          TabelaDeOveralls overalls,
                                                          Set<Long> usados) {
        var profissionais = candidatos(elenco, usados).filter(jogador -> !jogador.daBase()).toList();
        var pool = profissionais.isEmpty() ? candidatos(elenco, usados).toList() : profissionais;

        // max devolve o último máximo; invertendo a ordem por id, o último máximo passa a
        // ser o de menor id — que é o desempate que o spec pede.
        return pool.stream().max(Comparator
                .comparingInt((JogadorDisponivel jogador) ->
                        overalls.overall(jogador.jogadorId(), posicaoId))
                .thenComparing(Comparator.comparingLong(JogadorDisponivel::jogadorId).reversed()));
    }

    private static Stream<JogadorDisponivel> candidatos(List<JogadorDisponivel> elenco,
                                                        Set<Long> usados) {
        return elenco.stream().filter(jogador -> !usados.contains(jogador.jogadorId()));
    }

    private static long posicaoDoSlot(FormacaoCandidata formacao, int slotOrdem) {
        return formacao.posicaoPorSlot().get(slotOrdem - 1);
    }
}
