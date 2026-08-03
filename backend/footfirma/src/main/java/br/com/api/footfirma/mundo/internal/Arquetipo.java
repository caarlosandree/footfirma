package br.com.api.footfirma.mundo.internal;

import java.util.SplittableRandom;

/**
 * Faixas de um perfil de clube. Reputação, dinheiro e base variam de forma
 * independente dentro delas — é o descasamento entre os três que produz um mundo
 * com vantagens e desvantagens em vez de uma escala única de "melhor a pior".
 */
record Arquetipo(String nome,
                 int reputacaoMin, int reputacaoMax,
                 int financasMin, int financasMax,
                 int baseMin, int baseMax) {

    int sortearReputacao(SplittableRandom aleatorio) {
        return aleatorio.nextInt(reputacaoMin, reputacaoMax + 1);
    }

    int sortearFinancas(SplittableRandom aleatorio) {
        return aleatorio.nextInt(financasMin, financasMax + 1);
    }

    int sortearBase(SplittableRandom aleatorio) {
        return aleatorio.nextInt(baseMin, baseMax + 1);
    }
}
