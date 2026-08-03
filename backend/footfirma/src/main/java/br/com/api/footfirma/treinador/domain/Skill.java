package br.com.api.footfirma.treinador.domain;

/**
 * As seis habilidades do treinador, cada uma agindo num momento distinto do jogo.
 *
 * <p>A separação por momento não é estética: skills que agem no mesmo instante criariam
 * escolha falsa na distribuição de pontos — uma delas seria estritamente melhor e todo
 * treinador acabaria idêntico.
 */
public enum Skill {

    /** Durante a partida: eficácia da substituição e leitura do desgaste real. */
    VISAO_DE_JOGO,

    /** No intervalo e antes do apito: moral do elenco e reação quando se está perdendo. */
    PRELECAO,

    /** No vestiário: amortece a queda de moral de quem recebe menos minutos do que esperava. */
    LIDERANCA,

    /** Entre temporadas: acelera a evolução dos jovens. */
    TREINAMENTO,

    /** Na execução: reduz a penalidade de escalar jogador fora da posição. */
    TATICA,

    /** No mercado: frequência e qualidade das propostas recebidas. */
    NEGOCIACAO
}
