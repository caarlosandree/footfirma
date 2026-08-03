package br.com.api.footfirma.avaliacao.dto;

/**
 * {@code posicaoPrincipal} é onde o jogador atua; {@code posicaoAvaliada} é a
 * posição do ranking. Um volante ranqueado como zagueiro traz as duas diferentes —
 * carregar ambas evita que a divergência pareça erro.
 */
public record ItemDeRanking(String slug, String nomeExibicao, Integer idade,
                            String posicaoPrincipal, String posicaoAvaliada, Integer overall) {
}
