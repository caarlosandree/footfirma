package br.com.api.footfirma.temporada.dto;

/** Entrada de ingestão do importador. Espelha uma linha de {@code temporada.csv}. */
public record DadosDeTemporada(String label, Integer anoInicio, Integer anoFim) {
}
