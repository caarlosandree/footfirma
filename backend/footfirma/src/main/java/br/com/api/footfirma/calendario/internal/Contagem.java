package br.com.api.footfirma.calendario.internal;

/** O que uma fase produziu. Somado por edição, vira o relatório da temporada. */
record Contagem(int rodadas, int confrontos, int jogos) {

    static final Contagem VAZIA = new Contagem(0, 0, 0);

    Contagem mais(Contagem outra) {
        return new Contagem(rodadas + outra.rodadas,
                confrontos + outra.confrontos, jogos + outra.jogos);
    }
}
