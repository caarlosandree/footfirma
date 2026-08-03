package br.com.api.footfirma.competicao.dto;

import java.time.LocalDate;

/** Entrada de ingestão. Chave de upsert: {@code (competicaoId, temporadaId)}. */
public record DadosDeEdicao(Long competicaoId, Long temporadaId, String nome,
                            LocalDate dataInicio, LocalDate dataFim) {
}
