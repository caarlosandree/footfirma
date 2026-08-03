package br.com.api.footfirma.jogador.dto;

/** Entrada de ingestão. O id vem resolvido por {@code buscarIdCaracteristicaPorCodigo}. */
public record DadosDeCaracteristica(Long jogadorId, Long caracteristicaId) {
}
