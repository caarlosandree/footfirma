package br.com.api.footfirma.clube.mapper;

import br.com.api.footfirma.clube.domain.Clube;
import br.com.api.footfirma.clube.dto.ClubeDetalhe;
import br.com.api.footfirma.clube.dto.ClubeResumo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface ClubeMapper {

    ClubeResumo paraResumo(Clube clube);

    @Mapping(target = "estadio", source = "estadio.nome")
    @Mapping(target = "capacidadeEstadio", source = "estadio.capacidade")
    ClubeDetalhe paraDetalhe(Clube clube);
}
