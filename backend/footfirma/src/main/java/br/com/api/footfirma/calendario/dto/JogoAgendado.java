package br.com.api.footfirma.calendario.dto;

import java.time.LocalDate;

/**
 * Um jogo do ponto de vista de quem lê o calendário.
 *
 * <p>Mandante, visitante, estádio e data são nulos enquanto o confronto de eliminatória
 * não conhece seus dois clubes.
 */
public record JogoAgendado(long jogoId, long confrontoId, long rodadaId, int ordemNoConfronto,
                           Long mandanteId, Long visitanteId, Long estadioId,
                           LocalDate dataJogo, SituacaoDoJogo situacao,
                           Integer golsMandante, Integer golsVisitante,
                           RegrasDeDesempate desempate) {
}
