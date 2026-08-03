package br.com.api.footfirma.mundo.internal;

/**
 * As cotas de papel são o que garante que todo clube tenha alguém para se olhar.
 * Uma distribuição gaussiana pura permitiria um elenco inteiro dentro de seis
 * pontos — tecnicamente correto e sem graça.
 *
 * <p>Os deltas do elenco profissional são relativos ao nível do clube; os da base,
 * à qualidade de formação.
 */
enum PapelNoElenco {

    ESTRELA(1, 10, 14, 24, 30, "PROFISSIONAL"),
    TITULAR(9, 2, 6, 21, 33, "PROFISSIONAL"),
    ROTATIVO(8, -2, 2, 21, 33, "PROFISSIONAL"),
    RESERVA(6, -8, -3, 19, 35, "PROFISSIONAL"),
    CRIA(2, -6, 0, 18, 20, "PROFISSIONAL"),
    JOIA(2, -20, -20, 16, 19, "BASE"),
    PROMESSA(4, -15, -15, 15, 19, "BASE"),
    GAROTO(6, -22, -22, 15, 18, "BASE");

    private final int quantidade;
    private final int deltaMinimo;
    private final int deltaMaximo;
    private final int idadeMinima;
    private final int idadeMaxima;
    private final String categoria;

    PapelNoElenco(int quantidade, int deltaMinimo, int deltaMaximo,
                  int idadeMinima, int idadeMaxima, String categoria) {
        this.quantidade = quantidade;
        this.deltaMinimo = deltaMinimo;
        this.deltaMaximo = deltaMaximo;
        this.idadeMinima = idadeMinima;
        this.idadeMaxima = idadeMaxima;
        this.categoria = categoria;
    }

    int quantidade() {
        return quantidade;
    }

    int deltaMinimo() {
        return deltaMinimo;
    }

    int deltaMaximo() {
        return deltaMaximo;
    }

    int idadeMinima() {
        return idadeMinima;
    }

    int idadeMaxima() {
        return idadeMaxima;
    }

    String categoria() {
        return categoria;
    }

    boolean daBase() {
        return "BASE".equals(categoria);
    }

    /** Potencial das joias: é o que faz um clube pobre valer a pena olhar. */
    int potencialMinimo() {
        return switch (this) {
            case JOIA -> 88;
            case PROMESSA -> 75;
            case GAROTO -> 60;
            default -> 0;
        };
    }

    int potencialMaximo() {
        return switch (this) {
            case JOIA -> 95;
            case PROMESSA -> 85;
            case GAROTO -> 72;
            default -> 0;
        };
    }
}
