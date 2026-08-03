package br.com.api.footfirma.shared.evento;

import java.time.Instant;

/**
 * A temporada acabou: é a hora de fechar contas — evolução de skills, reputação de
 * carreira, consolidação de afinidade e a janela de assédio do mercado.
 *
 * <p>Mesmo estatuto de {@link PartidaEncerrada}: hospedado em {@code shared} porque o
 * produtor ainda não existe.
 */
public record TemporadaEncerrada(long temporadaId, Instant ocorridoEm) {
}
