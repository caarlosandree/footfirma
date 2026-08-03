package br.com.api.footfirma.jogador.dto;

/**
 * Identifica o jogador junto de suas skills. Distinto de {@link AtributosJogador},
 * que traz apenas os valores. O id vem junto porque quem consome grava chave
 * estrangeira; em resposta REST o identificador continua sendo o slug.
 */
public record JogadorComAtributos(Long jogadorId, AtributosJogador atributos) {
}
