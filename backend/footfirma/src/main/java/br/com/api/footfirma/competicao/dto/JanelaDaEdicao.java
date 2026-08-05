package br.com.api.footfirma.competicao.dto;

import java.time.LocalDate;

/** O intervalo em que uma edição se disputa. É o que o calendário tem para distribuir. */
public record JanelaDaEdicao(LocalDate inicio, LocalDate fim) {
}
