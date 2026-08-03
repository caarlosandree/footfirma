package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.treinador.domain.StatusConfianca;

/**
 * O que aconteceu com um jogador numa partida, do ponto de vista da promessa que lhe foi
 * feita.
 */
record FatoDeMinutagem(StatusConfianca status, int minutosRecebidos, int lideranca) {

    /** Negativa quando jogou menos do que o status prometia. */
    int diferenca() {
        return minutosRecebidos - status.minutosEsperados();
    }
}
