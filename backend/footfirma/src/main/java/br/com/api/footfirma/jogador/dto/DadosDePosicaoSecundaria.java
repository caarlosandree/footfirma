package br.com.api.footfirma.jogador.dto;

/** Entrada de ingestão. Espelha uma linha de {@code jogador_posicao.csv}. */
public record DadosDePosicaoSecundaria(Long jogadorId, Long posicaoId, Integer ordem) {
}
