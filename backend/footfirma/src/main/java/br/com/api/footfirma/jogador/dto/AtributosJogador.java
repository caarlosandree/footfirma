package br.com.api.footfirma.jogador.dto;

public record AtributosJogador(
        Integer ritmo, Integer forca, Integer folego, Integer salto, Integer agilidade,
        Integer passe, Integer drible, Integer cruzamento, Integer frieza,
        Integer finalizacao, Integer cabeceio, Integer falta, Integer penalti,
        Integer desarme, Integer marcacao,
        Integer golReflexo, Integer golPosicionamento, Integer golManejo,
        Integer potencialBase, String fonteAtributo
) {
}
