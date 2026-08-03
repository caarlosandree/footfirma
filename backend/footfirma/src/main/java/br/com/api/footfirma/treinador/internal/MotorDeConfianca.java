package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.treinador.domain.StatusConfianca;

import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.AMORTECIMENTO_POR_LIDERANCA;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.CONFIANCA_MAX;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.CONFIANCA_MIN;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.CUSTO_POR_DEGRAU_DE_STATUS;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.MINUTOS_INCLINACAO;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.MORAL_MAX;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.MORAL_MIN;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.clamp;

/**
 * A moral do jogador reage à distância entre o que foi prometido e o que foi entregue.
 *
 * <p>O clamp é assimétrico de propósito ({@code -4} contra {@code +2}): decepção pesa mais
 * que satisfação. E {@code FORA_DOS_PLANOS} chegando a zero sem punição é o que torna a
 * honestidade viável — dizer "você não joga" custa menos que prometer e não cumprir.
 */
final class MotorDeConfianca {

    private MotorDeConfianca() {
    }

    static double delta(FatoDeMinutagem fato) {
        var bruto = clamp(fato.diferenca() * MINUTOS_INCLINACAO, CONFIANCA_MIN, CONFIANCA_MAX);
        return bruto < 0 ? bruto * amortecimento(fato.lideranca()) : bruto;
    }

    /**
     * Preço imediato de mudar o papel do jogador no elenco. Sem ele, bastaria promover todo
     * mundo antes da partida e rebaixar depois.
     */
    static double deltaPorMudancaDeStatus(StatusConfianca anterior, StatusConfianca novo) {
        return (novo.escala() - anterior.escala()) * CUSTO_POR_DEGRAU_DE_STATUS;
    }

    static double aplicar(double moralAtual, FatoDeMinutagem fato) {
        return clamp(moralAtual + delta(fato), MORAL_MIN, MORAL_MAX);
    }

    /** Liderança 10 corta 60% da queda; Liderança 1, apenas 6%. */
    private static double amortecimento(int lideranca) {
        return 1 - lideranca * AMORTECIMENTO_POR_LIDERANCA;
    }
}
