package br.com.api.footfirma.treinador;

/**
 * A passagem terminou e o clube está sem treinador — é o gatilho do mercado.
 *
 * <p>Não carrega o motivo do fim de propósito: {@code MotivoFim} é tipo de
 * {@code treinador.domain}, interno ao módulo, e uma vaga aberta é uma vaga aberta
 * independentemente de ter sido demissão, pedido ou fim de contrato. Quem precisa do
 * motivo consulta o vínculo.
 */
public record VinculoEncerrado(long treinadorId, long clubeId, long vinculoId, long temporadaId) {
}
