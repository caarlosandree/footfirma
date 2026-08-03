package br.com.api.footfirma.treinador.internal;

/** Como a temporada terminou para um treinador, do ponto de vista do que foi cobrado dele. */
enum Desfecho {

    META_BATIDA,

    /** Não alcançou o que se esperava, mas chegou ao fim do ano no cargo. */
    SOBREVIVEU,

    DEMITIDO
}
