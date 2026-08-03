package br.com.api.footfirma.treinador.internal;

import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.AFINIDADE_JOGOS_DE_REFERENCIA;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.AFINIDADE_PESO_MAX;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.AFINIDADE_PESO_MIN;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.clamp;

/**
 * A afinidade é memória de longo prazo, não humor: consolida uma vez, ao encerrar o
 * vínculo, misturando o que já existia com a moral final da passagem.
 *
 * <p>É o que faz o pupilo chegar adiantado no clube seguinte, sem nenhuma regra especial
 * — apenas porque a linha sobreviveu à troca de clube.
 */
final class MotorDeAfinidade {

    private MotorDeAfinidade() {
    }

    /**
     * @param jogosNaPassagem quantas partidas <b>esta</b> passagem durou, não o total da
     *                        carreira em comum. Se fosse o acumulado, uma passagem de três
     *                        jogos depois de três temporadas juntos pesaria o máximo e
     *                        apagaria a memória que ela mal tocou.
     */
    static double consolidar(double afinidadeAnterior, double moralFinal, int jogosNaPassagem) {
        var peso = clamp(jogosNaPassagem / AFINIDADE_JOGOS_DE_REFERENCIA,
                AFINIDADE_PESO_MIN, AFINIDADE_PESO_MAX);
        return afinidadeAnterior * (1 - peso) + moralFinal * peso;
    }
}
