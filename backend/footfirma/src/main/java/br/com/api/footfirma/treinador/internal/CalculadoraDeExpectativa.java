package br.com.api.footfirma.treinador.internal;

import java.util.Collection;

import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.MORAL_JOGADOR_INCLINACAO;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.MORAL_JOGADOR_MAX;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.MORAL_JOGADOR_MIN;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.MORAL_VINCULO_INCLINACAO;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.MORAL_VINCULO_MAX;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.MORAL_VINCULO_MIN;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.NEUTRO;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.clamp;

/**
 * Onde a memória vira ponto de partida: a reputação do treinador semeia a moral do vínculo
 * novo, e a afinidade com o jogador semeia a moral dele na chegada.
 */
final class CalculadoraDeExpectativa {

    private CalculadoraDeExpectativa() {
    }

    /**
     * Rank do clube por reputação entre os participantes da edição — o mais reputado recebe
     * meta 1.
     *
     * <p>Sem coeficiente arbitrário, e adapta-se sozinho a divisão de qualquer tamanho: a
     * Série B com 20 clubes e uma futura copa com 64 usam esta mesma regra.
     */
    static int metaPosicao(int reputacaoDoClube, Collection<Integer> reputacoesDosParticipantes) {
        var acima = reputacoesDosParticipantes.stream()
                .filter(reputacao -> reputacao > reputacaoDoClube)
                .count();
        return (int) acima + 1;
    }

    /**
     * O que decide a moral de estreia não é a reputação sozinha, é a distância entre a do
     * treinador e a do clube: vencedor assumindo clube médio ganha lua de mel longa, nome
     * pequeno assumindo gigante começa sob desconfiança.
     */
    static double moralInicialDoVinculo(int reputacaoTreinador, int reputacaoClube) {
        var bruto = NEUTRO + (reputacaoTreinador - reputacaoClube) * MORAL_VINCULO_INCLINACAO;
        return clamp(bruto, MORAL_VINCULO_MIN, MORAL_VINCULO_MAX);
    }

    /** Ex-pupilo chega adiantado; quem foi queimado no banco chega atrás. */
    static double moralInicialDoJogador(double afinidade) {
        var bruto = NEUTRO + (afinidade - NEUTRO) * MORAL_JOGADOR_INCLINACAO;
        return clamp(bruto, MORAL_JOGADOR_MIN, MORAL_JOGADOR_MAX);
    }
}
