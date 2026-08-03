package br.com.api.footfirma.shared.evento;

import java.time.Instant;
import java.util.Map;

/**
 * Uma partida terminou.
 *
 * <p>Publicado pelo módulo {@code partida}, que ainda não existe. Vive em {@code shared}
 * (módulo OPEN) porque o produtor não foi escrito; migra para {@code partida} quando ele
 * existir.
 *
 * <p><b>As reputações viajam no evento</b> em vez de serem consultadas no banco. Sem isso,
 * reprocessar uma campanha depois que a reputação do clube mudou devolveria uma moral
 * diferente da que aconteceu, e o determinismo se perderia em silêncio. Hoje
 * {@code clube.reputacao} é estática e o problema não aparece — o spec de progressão vai
 * torná-la dinâmica, e o contrato precisa estar pronto antes.
 *
 * @param minutosPorJogador minutos em campo por jogador. Vazio quando o produtor não
 *                          apura minutagem — o que é diferente de "ninguém jogou", e por
 *                          isso o consumidor precisa distinguir os dois casos.
 */
public record PartidaEncerrada(
        long edicaoId,
        long clubeMandanteId,
        long clubeVisitanteId,
        int golsMandante,
        int golsVisitante,
        int reputacaoMandante,
        int reputacaoVisitante,
        Map<Long, Integer> minutosPorJogador,
        Instant ocorridoEm) {

    public PartidaEncerrada {
        minutosPorJogador = Map.copyOf(minutosPorJogador);
    }

    /**
     * Identidade do fato, para quem precisa consumir o evento uma vez só.
     *
     * <p>É derivada do conteúdo porque não há produtor para fornecer um id de partida:
     * duas partidas distintas não compartilham edição, mandante, visitante e instante. No
     * dia em que o módulo {@code partida} existir, esta chave vira o id dele.
     */
    public String chave() {
        return edicaoId + ":" + clubeMandanteId + "x" + clubeVisitanteId
                + "@" + ocorridoEm.toEpochMilli();
    }
}
