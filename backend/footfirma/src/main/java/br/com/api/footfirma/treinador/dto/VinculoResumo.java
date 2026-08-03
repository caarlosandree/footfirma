package br.com.api.footfirma.treinador.dto;

import java.time.LocalDate;

/**
 * @param moral        semeada na assinatura pela distância entre a reputação do treinador
 *                     e a do clube. Nunca 50 fixo.
 * @param metaPosicao  o que vai ser cobrado, congelado no dia da assinatura.
 */
public record VinculoResumo(Long id, Long treinadorId, Long clubeId, Long temporadaId,
                            LocalDate inicio, Double moral, Integer metaPosicao) {
}
