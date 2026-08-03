package br.com.api.footfirma.clube.dto;

/** Entrada de ingestão. Espelha uma linha de {@code estadio.csv}, com a UF já resolvida em id. */
public record DadosDeEstadio(String nome, String cidade, Long estadoId,
                             Integer capacidade, Integer anoInauguracao) {
}
