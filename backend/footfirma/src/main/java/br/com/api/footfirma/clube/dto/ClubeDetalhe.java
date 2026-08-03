package br.com.api.footfirma.clube.dto;

public record ClubeDetalhe(
        Long id,
        String slug,
        String nomeOficial,
        String nomeCurto,
        String apelido,
        Integer anoFundacao,
        String estadio,
        Integer capacidadeEstadio,
        Integer reputacao,
        Integer qualidadeBase
) {
}
