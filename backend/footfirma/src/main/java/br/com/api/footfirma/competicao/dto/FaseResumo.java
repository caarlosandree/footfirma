package br.com.api.footfirma.competicao.dto;

/**
 * @param id         quem gera calendário grava {@code rodada.fase_id} a partir dele.
 * @param temGolFora entrou junto com o {@code id}: as outras três regras de desempate já
 *                   estavam aqui, e sem ela o resolvedor de confronto não fecha a regra.
 */
public record FaseResumo(
        Long id,
        Integer ordem,
        String nome,
        String tipo,
        Integer jogosPorConfronto,
        Boolean temGolFora,
        Boolean temProrrogacao,
        Boolean temPenaltis
) {
}
