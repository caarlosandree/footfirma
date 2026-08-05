package br.com.api.footfirma.avaliacao.dto;

/**
 * Uma linha de {@code jogador_overall} por id, sem passar por slug.
 *
 * <p>Existe porque montar um onze exige as nove posições de ~30 jogadores, e
 * {@code buscarPorSlug} devolve as nove de um só.
 */
public record OverallDeJogador(Long jogadorId, Long posicaoId, Integer overall) {
}
