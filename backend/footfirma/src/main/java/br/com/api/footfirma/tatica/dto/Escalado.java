package br.com.api.footfirma.tatica.dto;

/**
 * Uma linha da escalação, do ponto de vista de quem lê o plano.
 *
 * @param posicaoId        em que posição o jogador foi escalado — do slot, se titular;
 *                         da posição principal, se reserva. Lida da linha gravada.
 * @param aptidaoNoMomento o overall congelado na gravação — o que o treinador viu.
 * @param aptidaoAtual     o overall de hoje na mesma posição — o que vale em campo.
 * @param irregular        o jogador não tem mais vínculo com o clube na temporada.
 */
public record Escalado(long jogadorId, long posicaoId, Integer slotOrdem, Integer ordemBanco,
                       int aptidaoNoMomento, int aptidaoAtual, boolean irregular) {
}
