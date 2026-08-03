package br.com.api.footfirma.importacao.dto;

/** Linha recusada antes de tocar o banco, com o lugar exato onde ela está. */
public record OcorrenciaRegistrada(String entidade, int linha, String chave,
                                   String severidade, String motivo) {
}
