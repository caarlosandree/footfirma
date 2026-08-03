package br.com.api.footfirma.treinador.mapper;

import br.com.api.footfirma.treinador.domain.Skill;
import br.com.api.footfirma.treinador.domain.Treinador;
import br.com.api.footfirma.treinador.dto.TreinadorDetalhe;
import br.com.api.footfirma.treinador.dto.TreinadorResumo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Map;

@Mapper
public interface TreinadorMapper {

    TreinadorResumo paraResumo(Treinador treinador);

    /** As skills entram por fora porque vivem em outra tabela, versionadas por temporada. */
    @Mapping(target = "skills", source = "skills")
    TreinadorDetalhe paraDetalhe(Treinador treinador, Map<Skill, Integer> skills);
}
