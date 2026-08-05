package br.com.api.footfirma.calendario.dto;

/** O que a geração produziu, para o relatório de quem a chamou. */
public record RelatorioDeCalendario(int rodadas, int confrontos, int jogos) {
}
