package br.com.api.footfirma.geografia.mapper;

import br.com.api.footfirma.geografia.domain.Estado;
import br.com.api.footfirma.geografia.domain.Pais;
import br.com.api.footfirma.geografia.dto.EstadoResumo;
import br.com.api.footfirma.geografia.dto.PaisResumo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface GeografiaMapper {

    @Mapping(target = "confederacao", expression = "java(pais.getConfederacao().name())")
    PaisResumo paraResumo(Pais pais);

    EstadoResumo paraResumo(Estado estado);
}
