package br.com.api.footfirma.competicao.dto;

/**
 * Entrada de ingestão. {@code competicaoDestinoId} pode apontar para competição sem
 * edição carregada — é o caso do destino de rebaixamento.
 */
public record DadosDeRegra(Long edicaoId, Integer posicaoInicio, Integer posicaoFim,
                           String tipo, Long competicaoDestinoId) {
}
