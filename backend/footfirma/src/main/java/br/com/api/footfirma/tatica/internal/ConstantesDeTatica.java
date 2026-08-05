package br.com.api.footfirma.tatica.internal;

/**
 * Números de balanceamento do módulo.
 *
 * <p>Nenhum tem verdade de referência: são julgamento de domínio, como os coeficientes
 * de {@code ConstantesDeTreinador}. Os testes travam a forma das regras e a ordem
 * relativa dos casos, não estes valores. Rebalancear é editar aqui.
 */
final class ConstantesDeTatica {

    private ConstantesDeTatica() {
    }

    static final int TITULARES = 11;

    static final int BANCO_MINIMO = 5;
    static final int BANCO_MAXIMO = 12;

    /** Repertório do escalador de IA: {@code 1 + TATICA / DIVISOR_DE_REPERTORIO}. */
    static final int REPERTORIO_BASE = 1;
    static final int DIVISOR_DE_REPERTORIO = 2;

    /** Distância da média de reputação que separa uma mentalidade da seguinte. */
    static final int FAIXA_DE_MENTALIDADE = 10;
}
