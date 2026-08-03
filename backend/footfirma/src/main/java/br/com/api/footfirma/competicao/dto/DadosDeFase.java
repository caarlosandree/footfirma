package br.com.api.footfirma.competicao.dto;

/** Entrada de ingestão. Chave de upsert: {@code (edicaoId, ordem)}. */
public record DadosDeFase(Long edicaoId, Integer ordem, String nome, String tipo,
                          Integer jogosPorConfronto, boolean temGolFora,
                          boolean temProrrogacao, boolean temPenaltis) {
}
