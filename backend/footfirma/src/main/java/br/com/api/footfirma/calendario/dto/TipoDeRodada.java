package br.com.api.footfirma.calendario.dto;

/**
 * Decide o leque de dias em que os jogos da rodada podem cair.
 *
 * <p>É propriedade da rodada, e não da competição: na tabela real, as rodadas 23, 24, 25
 * e 27 da Série A são de fim de semana e a 26 concentra quarta e quinta. O que separa as
 * divisões é o peso de cada dia dentro do leque, não o leque.
 */
public enum TipoDeRodada {
    FIM_DE_SEMANA, MEIO_DE_SEMANA
}
