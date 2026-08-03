package br.com.api.footfirma.treinador.domain;

/**
 * O papel que o treinador declara para o jogador no elenco — e, junto com ele, uma
 * promessa quantificada de minutos.
 *
 * <p>É a promessa que dá sentido à moral: sem status declarado não existe promessa
 * quebrada, e é a quebra que move o número. Dizer {@link #FORA_DOS_PLANOS} custa menos
 * que prometer {@link #INDISCUTIVEL} e deixar no banco.
 */
public enum StatusConfianca {

    INDISCUTIVEL(5, 85),
    IMPORTANTE(4, 65),
    ROTACAO(3, 40),
    PROMESSA(2, 20),
    FORA_DOS_PLANOS(1, 0);

    private final int escala;
    private final int minutosEsperados;

    StatusConfianca(int escala, int minutosEsperados) {
        this.escala = escala;
        this.minutosEsperados = minutosEsperados;
    }

    /** Degrau na hierarquia do elenco. A distância entre dois status é o custo de mudar. */
    public int escala() {
        return escala;
    }

    /** Minutos por jogo que este status promete. A moral reage à distância entre isto e o real. */
    public int minutosEsperados() {
        return minutosEsperados;
    }
}
