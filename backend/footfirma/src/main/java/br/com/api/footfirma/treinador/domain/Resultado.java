package br.com.api.footfirma.treinador.domain;

public enum Resultado {

    VITORIA,
    EMPATE,
    DERROTA;

    /** O resultado visto do outro lado do campo. */
    public Resultado invertido() {
        return switch (this) {
            case VITORIA -> DERROTA;
            case DERROTA -> VITORIA;
            case EMPATE -> EMPATE;
        };
    }

    public static Resultado de(int golsProprios, int golsAdversarios) {
        if (golsProprios > golsAdversarios) {
            return VITORIA;
        }
        return golsProprios < golsAdversarios ? DERROTA : EMPATE;
    }
}
