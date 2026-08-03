package br.com.api.footfirma.temporada.mapper;

import br.com.api.footfirma.temporada.domain.Temporada;
import br.com.api.footfirma.temporada.dto.TemporadaResumo;
import org.mapstruct.Mapper;

@Mapper
public interface TemporadaMapper {

    TemporadaResumo paraResumo(Temporada temporada);
}
