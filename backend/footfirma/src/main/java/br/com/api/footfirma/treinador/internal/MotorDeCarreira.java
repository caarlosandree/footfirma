package br.com.api.footfirma.treinador.internal;

import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.PONTOS_DEMITIDO;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.PONTOS_META_BATIDA;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.PONTOS_SOBREVIVEU;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.REPUTACAO_DELTA_MAX;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.REPUTACAO_DELTA_MIN;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.REPUTACAO_MAX;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.REPUTACAO_MIN;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.REPUTACAO_POR_DEMISSAO;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.REPUTACAO_POR_POSICAO;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.clamp;

/**
 * O que uma temporada deixa na carreira: pontos para distribuir entre as skills e um
 * ajuste na reputação.
 *
 * <p>A distribuição inicial é um começo, não uma sentença — é este motor que faz o
 * treinador de dez anos de estrada ser diferente do recém-formado, e é a reputação que
 * ele move que decide quais clubes procuram quem.
 */
final class MotorDeCarreira {

    private MotorDeCarreira() {
    }

    static Desfecho desfecho(FatoDeTemporada fato) {
        if (fato.demitido()) {
            return Desfecho.DEMITIDO;
        }
        return fato.bateuAMeta() ? Desfecho.META_BATIDA : Desfecho.SOBREVIVEU;
    }

    static int pontos(Desfecho desfecho) {
        return switch (desfecho) {
            case META_BATIDA -> PONTOS_META_BATIDA;
            case SOBREVIVEU -> PONTOS_SOBREVIVEU;
            case DEMITIDO -> PONTOS_DEMITIDO;
        };
    }

    /**
     * Quanto a carreira sobe ou desce. Terminar oito posições acima da meta vale mais que
     * terminar oito abaixo custa: o teto é +10 e o piso −8, porque uma temporada ruim não
     * deve apagar anos de estrada.
     */
    static int deltaDeReputacao(FatoDeTemporada fato) {
        if (fato.demitido()) {
            return REPUTACAO_POR_DEMISSAO;
        }
        if (fato.posicaoFinal().isEmpty()) {
            return 0;
        }

        var bruto = (fato.metaPosicao() - fato.posicaoFinal().getAsInt()) * REPUTACAO_POR_POSICAO;
        return (int) Math.round(clamp(bruto, REPUTACAO_DELTA_MIN, REPUTACAO_DELTA_MAX));
    }

    static int aplicarReputacao(int reputacaoAtual, FatoDeTemporada fato) {
        return (int) clamp(reputacaoAtual + deltaDeReputacao(fato), REPUTACAO_MIN, REPUTACAO_MAX);
    }
}
