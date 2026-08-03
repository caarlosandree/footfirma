package br.com.api.footfirma.jogador.mapper;

import br.com.api.footfirma.jogador.domain.JogadorAtributo;
import br.com.api.footfirma.jogador.dto.AtributosJogador;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface JogadorMapper {

    @Mapping(target = "fonteAtributo", expression = "java(atributo.getFonteAtributo().name())")
    AtributosJogador paraAtributos(JogadorAtributo atributo);
}
