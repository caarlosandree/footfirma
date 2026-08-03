package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.shared.evento.PartidaEncerrada;
import br.com.api.footfirma.treinador.domain.Resultado;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Partidas sintéticas. Nenhuma partida real existe — o módulo {@code partida} ainda não
 * foi escrito, e este módulo inteiro é construído e testado sem ele.
 *
 * <p>Cada rodada anda uma semana, e o clube alterna mando de campo. Isso não é enfeite:
 * o instante é o que separa uma rodada da outra na chave de idempotência, e a alternância
 * exercita os dois lados de {@code processar}.
 */
final class EventoFactory {

    static final long EDICAO = 1L;

    /** Depois de {@code inicio} do vínculo (1º de janeiro), como o check de datas exige. */
    private static final Instant PRIMEIRA_RODADA = Instant.parse("2026-02-01T20:00:00Z");

    private EventoFactory() {
    }

    /**
     * 14 vitórias, 10 empates e 14 derrotas em 38 rodadas — campanha de meio de tabela.
     *
     * <p>Intercaladas em blocos de {@code V·D·E·V·D} para que a moral suba e desça ao
     * longo do ano em vez de despencar de uma vez: uma sequência ordenada faria a
     * demissão do gigante dizer mais sobre a ordem dos resultados que sobre a régua.
     */
    static List<Resultado> campanhaMediana() {
        var campanha = new ArrayList<Resultado>(38);
        for (var bloco = 0; bloco < 7; bloco++) {
            campanha.addAll(List.of(Resultado.VITORIA, Resultado.DERROTA, Resultado.EMPATE,
                    Resultado.VITORIA, Resultado.DERROTA));
        }
        campanha.addAll(List.of(Resultado.EMPATE, Resultado.EMPATE, Resultado.EMPATE));
        return List.copyOf(campanha);
    }

    /** A campanha inteira vista do lado de {@code clubeId}, uma rodada por resultado. */
    static List<PartidaEncerrada> campanha(long clubeId, int reputacaoPropria,
                                           long adversarioId, int reputacaoAdversario,
                                           List<Resultado> resultados) {
        var partidas = new ArrayList<PartidaEncerrada>(resultados.size());
        for (var rodada = 0; rodada < resultados.size(); rodada++) {
            partidas.add(partida(clubeId, reputacaoPropria, adversarioId, reputacaoAdversario,
                    resultados.get(rodada), rodada));
        }
        return List.copyOf(partidas);
    }

    static PartidaEncerrada vitoriaDoMandante(long mandanteId, int reputacaoMandante,
                                              long visitanteId, int reputacaoVisitante) {
        return partida(mandanteId, reputacaoMandante, visitanteId, reputacaoVisitante,
                Resultado.VITORIA, 0);
    }

    static PartidaEncerrada derrotaDoMandante(long mandanteId, int reputacaoMandante,
                                              long visitanteId, int reputacaoVisitante) {
        return partida(mandanteId, reputacaoMandante, visitanteId, reputacaoVisitante,
                Resultado.DERROTA, 0);
    }

    static PartidaEncerrada empate(long mandanteId, int reputacaoMandante,
                                   long visitanteId, int reputacaoVisitante) {
        return partida(mandanteId, reputacaoMandante, visitanteId, reputacaoVisitante,
                Resultado.EMPATE, 0);
    }

    /** A mesma partida, agora com minutagem apurada. */
    static PartidaEncerrada com(PartidaEncerrada partida, Map<Long, Integer> minutosPorJogador) {
        return new PartidaEncerrada(partida.edicaoId(), partida.clubeMandanteId(),
                partida.clubeVisitanteId(), partida.golsMandante(), partida.golsVisitante(),
                partida.reputacaoMandante(), partida.reputacaoVisitante(),
                minutosPorJogador, partida.ocorridoEm());
    }

    private static PartidaEncerrada partida(long clubeId, int reputacaoPropria,
                                            long adversarioId, int reputacaoAdversario,
                                            Resultado meuResultado, int rodada) {
        var meusGols = gols(meuResultado);
        var golsDele = gols(meuResultado.invertido());
        var quando = PRIMEIRA_RODADA.plus(Duration.ofDays(rodada * 7L));

        return rodada % 2 == 0
                ? new PartidaEncerrada(EDICAO, clubeId, adversarioId, meusGols, golsDele,
                        reputacaoPropria, reputacaoAdversario, Map.of(), quando)
                : new PartidaEncerrada(EDICAO, adversarioId, clubeId, golsDele, meusGols,
                        reputacaoAdversario, reputacaoPropria, Map.of(), quando);
    }

    private static int gols(Resultado resultado) {
        return switch (resultado) {
            case VITORIA -> 2;
            case EMPATE -> 1;
            case DERROTA -> 0;
        };
    }
}
