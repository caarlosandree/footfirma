package br.com.api.footfirma.jogador.dto;

/**
 * O elenco cru para quem escala: ids em vez de slugs, e a categoria que
 * {@link JogadorResumo} não carrega.
 *
 * <p>{@code categoria} é {@code String} porque {@code CategoriaDeElenco} vive em
 * {@code jogador.domain}, que é interno — mesma travessia que {@link DadosDeVinculo} já
 * faz na entrada.
 */
public record JogadorDoElenco(Long jogadorId, Long posicaoPrincipalId,
                              String categoria, Integer numeroCamisa) {
}
