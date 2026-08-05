package br.com.api.footfirma.calendario.internal;

/**
 * Números de balanceamento do módulo.
 *
 * <p>Nenhum tem verdade de referência além do descanso, que é o piso que o padrão de fim
 * de semana e meio de semana produz: domingo → quarta são exatamente 3 dias, e quarta →
 * domingo, 4. Baixá-lo permitiria calendários que a realidade não produz; subi-lo tornaria
 * a rodada de meio de semana impossível.
 *
 * <p>Como os coeficientes de {@code ConstantesDeTatica}, os testes travam a forma das
 * regras e não estes valores.
 */
final class ConstantesDeCalendario {

    private ConstantesDeCalendario() {
    }

    static final int DESCANSO_MINIMO_EM_DIAS = 3;

    /** A janela da rodada é o alvo ± este número de dias. */
    static final int RAIO_DA_JANELA_EM_DIAS = 2;

    /** Teto de dias que um jogo pode ser empurrado além da janela antes de falhar. */
    static final int DESLOCAMENTO_MAXIMO_EM_DIAS = 21;

    /**
     * Quantos clubes por grupo numa fase de grupos.
     *
     * <p>Mora aqui e não em {@code Fase} porque nenhuma competição do mundo gerado usa
     * grupos. Quando uma usar, isto vira coluna e esta constante some.
     */
    static final int CLUBES_POR_GRUPO = 4;
}
