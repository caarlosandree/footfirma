package br.com.api.footfirma.jogador.dto;

import java.time.LocalDate;
import java.util.List;

public record JogadorDetalhe(
        Long id,
        String slug,
        String nomeCompleto,
        String nomeExibicao,
        LocalDate dataNascimento,
        Integer idade,
        Integer alturaCm,
        Integer pesoKg,
        String pePreferido,
        String posicaoPrincipal,
        String origem,
        AtributosJogador atributos,
        List<String> caracteristicas
) {
}
