package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.treinador.domain.Resultado;

/**
 * Tudo que o motor de moral precisa saber sobre uma partida, visto do lado de um clube.
 *
 * <p>As reputações chegam por valor, congeladas no instante do fato — o motor jamais
 * consulta {@code clube.reputacao}. É o que garante que reprocessar uma campanha antiga
 * devolva a moral que realmente aconteceu, mesmo depois que o mundo mudar.
 */
record FatoDeResultado(int reputacaoMeuClube, int reputacaoAdversario,
                       Resultado resultado, int posicaoAtual, int metaPosicao) {

    /** Positivo quando o adversário é mais forte. */
    double diferencaDeForca() {
        return (reputacaoAdversario - reputacaoMeuClube) / ConstantesDeTreinador.ESCALA_DE_FORCA;
    }
}
