package br.com.api.footfirma.treinador.internal;

import java.util.OptionalInt;

/**
 * O ano de um treinador num clube, reduzido ao que decide a evolução da carreira.
 *
 * @param posicaoFinal vazia quando o clube não veio na classificação do evento. Não é o
 *                     mesmo que ter ido mal: sem saber onde terminou, não há o que
 *                     premiar nem o que cobrar.
 */
record FatoDeTemporada(boolean demitido, int metaPosicao, OptionalInt posicaoFinal) {

    boolean bateuAMeta() {
        return posicaoFinal.isPresent() && posicaoFinal.getAsInt() <= metaPosicao;
    }
}
