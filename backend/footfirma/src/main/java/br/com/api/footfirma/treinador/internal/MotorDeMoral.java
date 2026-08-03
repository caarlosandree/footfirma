package br.com.api.footfirma.treinador.internal;

import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.BASE_DERROTA;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.BASE_EMPATE;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.BASE_VITORIA;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.EMPATE_INCLINACAO;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.EMPATE_MAX;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.EMPATE_MIN;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.META_INCLINACAO;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.META_MAX;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.META_MIN;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.MORAL_MAX;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.MORAL_MIN;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.PESO_ADVERSARIO;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.PESO_ADVERSARIO_MAX;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.PESO_ADVERSARIO_MIN;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.clamp;

/**
 * A moral do treinador reage ao resultado <b>comparado com o que se esperava</b>, nunca ao
 * resultado bruto.
 *
 * <p>Num mundo de clubes com reputação entre 35 e 88, a régua absoluta premiaria quem
 * pegou clube grande e demitiria quem foi bem com clube pequeno. Aqui vencer o líder vale
 * mais que vencer o lanterna, e empatar com o lanterna sendo gigante custa caro.
 */
final class MotorDeMoral {

    private MotorDeMoral() {
    }

    static double delta(FatoDeResultado fato) {
        var base = base(fato);
        return base < 0 ? base * fatorMeta(fato) : base;
    }

    static double aplicar(double moralAtual, FatoDeResultado fato) {
        return clamp(moralAtual + delta(fato), MORAL_MIN, MORAL_MAX);
    }

    private static double base(FatoDeResultado fato) {
        var forca = fato.diferencaDeForca();
        return switch (fato.resultado()) {
            case VITORIA -> BASE_VITORIA * peso(1 + forca * PESO_ADVERSARIO);
            case DERROTA -> BASE_DERROTA * peso(1 - forca * PESO_ADVERSARIO);
            case EMPATE -> clamp(BASE_EMPATE + forca * EMPATE_INCLINACAO, EMPATE_MIN, EMPATE_MAX);
        };
    }

    private static double peso(double bruto) {
        return clamp(bruto, PESO_ADVERSARIO_MIN, PESO_ADVERSARIO_MAX);
    }

    /**
     * Campanha atrasada faz cada tropeço doer mais; campanha adiantada dá crédito.
     *
     * <p>Só multiplica delta negativo — se amplificasse o positivo também, uma campanha
     * ruim passaria a valorizar vitórias, que é o oposto do efeito pretendido.
     */
    private static double fatorMeta(FatoDeResultado fato) {
        var bruto = 1 + (fato.posicaoAtual() - fato.metaPosicao()) * META_INCLINACAO;
        return clamp(bruto, META_MIN, META_MAX);
    }
}
