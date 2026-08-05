package br.com.api.footfirma.calendario.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Round-robin pelo método do círculo: a primeira posição fica fixa e as demais rodam.
 *
 * <p>Puro de propósito — recebe um número e devolve índices. Quem traduz índice em clube é
 * o serviço, e é isso que permite testar o emparelhamento sem banco.
 *
 * <p>O mando vai para quem mandou menos até ali, desempatando pelo índice. A alternativa
 * óbvia — alternar pela paridade de rodada e posição — degenera: sem o wrap da rotação a
 * posição de um participante cresce junto com a rodada, as duas paridades se cancelam, e
 * quem começa em posição ímpar não manda uma vez sequer no turno.
 */
final class TabelaDeBerger {

    private TabelaDeBerger() {
    }

    static List<List<ConfrontoDaRodada>> gerar(int participantes, boolean returno) {
        if (participantes < 2 || participantes % 2 != 0) {
            throw new IllegalArgumentException(
                    "Número de participantes precisa ser par e ao menos 2: " + participantes);
        }

        var mesa = new ArrayList<Integer>(participantes);
        for (var i = 0; i < participantes; i++) {
            mesa.add(i);
        }

        var turno = new ArrayList<List<ConfrontoDaRodada>>();
        for (var rodada = 0; rodada < participantes - 1; rodada++) {
            var jogos = new ArrayList<ConfrontoDaRodada>(participantes / 2);
            for (var i = 0; i < participantes / 2; i++) {
                var a = mesa.get(i);
                var b = mesa.get(participantes - 1 - i);
                jogos.add(mandaEmCasa(i, rodada)
                        ? new ConfrontoDaRodada(a, b)
                        : new ConfrontoDaRodada(b, a));
            }
            turno.add(List.copyOf(jogos));
            rotacionar(mesa);
        }

        if (!returno) {
            return List.copyOf(turno);
        }

        var completo = new ArrayList<>(turno);
        turno.forEach(rodada -> completo.add(rodada.stream()
                .map(jogo -> new ConfrontoDaRodada(jogo.visitante(), jogo.mandante()))
                .toList()));
        return List.copyOf(completo);
    }

    /**
     * Se o lado esquerdo do par manda.
     *
     * <p>O par {@code i = 0} é o único que envolve o eixo, e por isso é o único que alterna
     * com a rodada. Nos demais, o mando depende só da posição — e como cada participante
     * percorre todas as posições ao longo do turno, o total se equilibra sozinho: quatro
     * mandos pelas posições pares da metade esquerda, cinco pelas ímpares da direita, e o
     * eixo decidindo o desempate.
     */
    private static boolean mandaEmCasa(int i, int rodada) {
        return i == 0 ? rodada % 2 == 0 : i % 2 == 0;
    }

    /** A posição 0 é o eixo; as demais giram uma casa. */
    private static void rotacionar(List<Integer> mesa) {
        var ultimo = mesa.removeLast();
        mesa.add(1, ultimo);
    }
}
