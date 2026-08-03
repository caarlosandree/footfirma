package br.com.api.footfirma.competicao.dto;

/** Entrada de ingestão. {@code posicaoFinal} é nulo em edição ainda não disputada. */
public record DadosDeParticipante(Long edicaoId, Long clubeId, Integer posicaoFinal) {
}
