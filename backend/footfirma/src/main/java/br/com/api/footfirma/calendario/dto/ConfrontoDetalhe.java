package br.com.api.footfirma.calendario.dto;

import java.util.List;

/**
 * Um slot do chaveamento, para quem quer exibir o mata-mata inteiro.
 *
 * @param vencedorClubeId nulo até o confronto se decidir — e para sempre, em pontos
 *                        corridos e grupos.
 * @param jogos           em ordem no confronto: ida, depois volta.
 */
public record ConfrontoDetalhe(long confrontoId, int ordem, String chave,
                               Long clubeAId, Long clubeBId, Long vencedorClubeId,
                               List<JogoAgendado> jogos) {
}
