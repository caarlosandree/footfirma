package br.com.api.footfirma.treinador.internal;

/**
 * Coeficientes de balanceamento do módulo.
 *
 * <p>Nenhum deles tem verdade de referência: são julgamento de domínio, como as faixas
 * dos arquétipos no gerador de mundo. Os testes travam a <b>forma</b> das fórmulas e a
 * ordem relativa dos casos, não estes valores — rebalancear é editar aqui e rodar de
 * novo. Um teste que quebre com um ajuste destes estava medindo a coisa errada.
 */
final class ConstantesDeTreinador {

    private ConstantesDeTreinador() {
    }

    /** Ponto médio de toda escala 0-99 do módulo. */
    static final double NEUTRO = 50.0;

    static final double MORAL_MIN = 0.0;
    static final double MORAL_MAX = 99.0;

    // --- Moral do treinador por partida ---

    static final double BASE_VITORIA = 3.0;
    static final double BASE_EMPATE = -1.0;
    static final double BASE_DERROTA = -3.0;

    /** Normaliza a diferença de reputação entre os dois clubes para a faixa aproximada [-2, 2]. */
    static final double ESCALA_DE_FORCA = 50.0;

    static final double PESO_ADVERSARIO = 0.9;
    static final double PESO_ADVERSARIO_MIN = 0.25;
    static final double PESO_ADVERSARIO_MAX = 2.5;

    static final double EMPATE_INCLINACAO = 2.5;
    static final double EMPATE_MIN = -3.5;
    static final double EMPATE_MAX = 2.0;

    static final double META_INCLINACAO = 0.05;
    static final double META_MIN = 0.7;
    static final double META_MAX = 1.5;

    // --- Morais iniciais ---

    static final double MORAL_VINCULO_INCLINACAO = 0.4;
    static final double MORAL_VINCULO_MIN = 25.0;
    static final double MORAL_VINCULO_MAX = 85.0;

    static final double MORAL_JOGADOR_INCLINACAO = 0.6;
    static final double MORAL_JOGADOR_MIN = 20.0;
    static final double MORAL_JOGADOR_MAX = 90.0;

    // --- Demissão ---

    static final double LIMIAR_BASE = 15.0;
    static final double LIMIAR_INCLINACAO = 0.1;
    static final double FAIXA_DE_PRESSAO = 10.0;
    static final int CARENCIA_PARTIDAS = 5;

    // --- Confiança do jogador ---

    static final double MINUTOS_INCLINACAO = 0.04;
    static final double CONFIANCA_MIN = -4.0;
    static final double CONFIANCA_MAX = 2.0;
    static final double AMORTECIMENTO_POR_LIDERANCA = 0.06;
    static final double CUSTO_POR_DEGRAU_DE_STATUS = 3.0;

    /** Ao cruzar este piso para baixo, o módulo publica {@code JogadorInsatisfeito}. */
    static final double MORAL_JOGADOR_INSATISFEITO = 25.0;

    // --- Skills ---

    /**
     * Piso da escala. Vale como padrão quando o treinador ainda não tem a linha da skill
     * na temporada: ninguém é pior que 1, e assumir 0 daria ao motor um valor que a
     * distribuição jamais produz.
     */
    static final int SKILL_MINIMA = 1;

    static final int SKILL_MAXIMA = 10;

    /** Especialista gasta {@code 10·5·2·1·1·1}; generalista, {@code 4·4·3·3·3·3}. */
    static final int PONTOS_INICIAIS = 20;

    static double clamp(double valor, double minimo, double maximo) {
        return Math.max(minimo, Math.min(maximo, valor));
    }
}
