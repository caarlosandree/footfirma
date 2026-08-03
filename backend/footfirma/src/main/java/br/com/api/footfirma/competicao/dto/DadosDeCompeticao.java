package br.com.api.footfirma.competicao.dto;

/** Entrada de ingestão. {@code paisId} é nulo em competição continental. */
public record DadosDeCompeticao(String slug, String nome, Long paisId, String tipo,
                                Integer nivel, String genero) {
}
