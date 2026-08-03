package br.com.api.footfirma.competicao.dto;

public record FaseResumo(
        Integer ordem,
        String nome,
        String tipo,
        Integer jogosPorConfronto,
        Boolean temProrrogacao,
        Boolean temPenaltis
) {
}
