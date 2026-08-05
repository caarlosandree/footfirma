package br.com.api.footfirma.tatica.internal;

import br.com.api.footfirma.tatica.dto.NovoPlano;
import br.com.api.footfirma.tatica.dto.TitularEscalado;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Monta um plano completo sem intervenção humana: formação pelo repertório da skill
 * TATICA, onze pelo guloso de escassez, instruções pela reputação do clube e banco pelos
 * melhores restantes na posição principal.
 *
 * <p>Determinístico de ponta a ponta — nenhum sorteio, nenhum relógio. É o que
 * {@code EscaladorDeterministicoTest} trava.
 */
final class EscaladorAutomatico {

    private EscaladorAutomatico() {
    }

    static Optional<NovoPlano> montar(List<FormacaoCandidata> catalogo, int tatica,
                                      List<JogadorDisponivel> elenco, TabelaDeOveralls overalls,
                                      long posicaoDeGoleiroId,
                                      int reputacaoDoClube, double mediaDeReputacao) {
        var escolha = EscolhedorDeFormacao.escolher(catalogo, tatica, elenco, overalls);
        if (escolha.isEmpty()) {
            return Optional.empty();
        }

        var onze = escolha.get().onze();
        var titulares = onze.slots().stream()
                .map(slot -> new TitularEscalado(slot.jogadorId(), slot.slotOrdem()))
                .toList();

        var escalados = onze.slots().stream()
                .map(SlotPreenchido::jogadorId)
                .collect(Collectors.toSet());

        var banco = montarBanco(elenco, overalls, escalados, posicaoDeGoleiroId);
        if (banco.size() < ConstantesDeTatica.BANCO_MINIMO) {
            return Optional.empty();
        }

        var instrucoes = EscolhedorDeInstrucoes.instrucoesDe(
                EscolhedorDeInstrucoes.mentalidadeDe(reputacaoDoClube, mediaDeReputacao));

        return Optional.of(new NovoPlano(escolha.get().formacao().formacaoId(),
                EscolhedorDeInstrucoes.capitaoDe(onze),
                instrucoes.mentalidade(), instrucoes.ritmo(), instrucoes.linhaDefensiva(),
                instrucoes.pressao(), instrucoes.largura(), titulares, banco));
    }

    /**
     * Os melhores restantes pelo overall na posição principal, com ao menos um goleiro.
     *
     * <p>O goleiro entra antes do laço para nunca ser cortado pelo teto: sem isso, um
     * elenco com muitos jogadores de linha melhores empurraria o goleiro reserva para
     * fora, e {@code ValidadorDeEscalacao} reprovaria o plano que o próprio escalador
     * montou.
     */
    private static List<Long> montarBanco(List<JogadorDisponivel> elenco,
                                          TabelaDeOveralls overalls, Set<Long> jaEscalados,
                                          long posicaoDeGoleiroId) {
        var sobra = elenco.stream()
                .filter(jogador -> !jaEscalados.contains(jogador.jogadorId()))
                .sorted(Comparator
                        .comparingInt((JogadorDisponivel jogador) ->
                                -overalls.overall(jogador.jogadorId(), jogador.posicaoPrincipalId()))
                        .thenComparingLong(JogadorDisponivel::jogadorId))
                .toList();

        var banco = new ArrayList<Long>();
        sobra.stream()
                .filter(jogador -> jogador.posicaoPrincipalId() == posicaoDeGoleiroId)
                .findFirst()
                .ifPresent(goleiro -> banco.add(goleiro.jogadorId()));

        for (var jogador : sobra) {
            if (banco.size() >= ConstantesDeTatica.BANCO_MAXIMO) {
                break;
            }
            if (!banco.contains(jogador.jogadorId())) {
                banco.add(jogador.jogadorId());
            }
        }

        return List.copyOf(banco);
    }
}
