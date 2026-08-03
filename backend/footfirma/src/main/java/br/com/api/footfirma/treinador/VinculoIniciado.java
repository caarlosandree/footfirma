package br.com.api.footfirma.treinador;

/**
 * Um treinador assumiu um clube. A partir daqui as partidas daquele clube passam a mexer
 * na moral deste vínculo.
 */
public record VinculoIniciado(long treinadorId, long clubeId, long vinculoId, long temporadaId) {
}
