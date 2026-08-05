package br.com.api.footfirma.calendario.dto;

import java.time.LocalDate;
import java.util.List;

/** @param jogos em ordem de data. */
public record RodadaDetalhe(long rodadaId, int ordem, TipoDeRodada tipo,
                            LocalDate dataAlvo, List<JogoAgendado> jogos) {
}
