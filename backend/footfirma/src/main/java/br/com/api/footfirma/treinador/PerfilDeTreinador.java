package br.com.api.footfirma.treinador;

/**
 * O que outro módulo precisa saber sobre quem dirige um clube.
 *
 * <p>Mora no pacote raiz, e não em {@code dto/}, porque {@code treinador.dto} não é uma
 * interface nomeada do Modulith — referenciá-lo de fora reprova em
 * {@code ModularidadeTest}. Três inteiros não justificam publicar os seis DTOs e os onze
 * tipos de domínio do módulo.
 *
 * @param tatica valor da skill {@code TATICA} na temporada consultada; 0 quando o
 *               treinador ainda não tem skills gravadas naquele ano.
 */
public record PerfilDeTreinador(long treinadorId, int reputacao, int tatica) {
}
