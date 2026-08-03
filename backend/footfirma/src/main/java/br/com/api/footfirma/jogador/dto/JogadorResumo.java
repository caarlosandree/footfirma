package br.com.api.footfirma.jogador.dto;

public record JogadorResumo(
        Long id,
        String slug,
        String nomeExibicao,
        Integer idade,
        String posicao,
        Integer numeroCamisa
) {
}
