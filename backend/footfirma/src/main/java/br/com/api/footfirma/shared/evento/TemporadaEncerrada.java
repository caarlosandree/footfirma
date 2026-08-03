package br.com.api.footfirma.shared.evento;

import java.time.Instant;
import java.util.Map;

/**
 * A temporada acabou: é a hora de fechar contas — evolução de skills, reputação de
 * carreira, consolidação de afinidade e o encerramento dos vínculos do ano.
 *
 * <p>Mesmo estatuto de {@link PartidaEncerrada}: hospedado em {@code shared} porque o
 * produtor ainda não existe.
 *
 * @param posicaoFinalPorClube onde cada clube terminou. Viaja no evento pelo motivo da
 *                             decisão 8 — a classificação de 2026 não pode mudar porque
 *                             2027 aconteceu. Clube ausente do mapa é posição
 *                             desconhecida, e não vira nem prêmio nem punição.
 */
public record TemporadaEncerrada(long temporadaId,
                                 Map<Long, Integer> posicaoFinalPorClube,
                                 Instant ocorridoEm) {

    public TemporadaEncerrada {
        posicaoFinalPorClube = Map.copyOf(posicaoFinalPorClube);
    }

    /**
     * Identidade do fato, para quem precisa consumi-lo uma vez só.
     *
     * <p>Só o id da temporada: uma temporada acaba uma vez, e incluir o instante faria
     * uma republicação com relógio diferente ser tratada como um segundo fim de ano.
     */
    public String chave() {
        return "temporada:" + temporadaId;
    }
}
