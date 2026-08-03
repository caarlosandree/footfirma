package br.com.api.footfirma.treinador.mapper;

import br.com.api.footfirma.treinador.domain.Proposta;
import br.com.api.footfirma.treinador.domain.Skill;
import br.com.api.footfirma.treinador.domain.Treinador;
import br.com.api.footfirma.treinador.domain.VinculoTreinador;
import br.com.api.footfirma.treinador.dto.PropostaResumo;
import br.com.api.footfirma.treinador.dto.TreinadorDetalhe;
import br.com.api.footfirma.treinador.dto.TreinadorResumo;
import br.com.api.footfirma.treinador.dto.VinculoResumo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Map;

@Mapper
public interface TreinadorMapper {

    TreinadorResumo paraResumo(Treinador treinador);

    /** As skills entram por fora porque vivem em outra tabela, versionadas por temporada. */
    @Mapping(target = "skills", source = "skills")
    TreinadorDetalhe paraDetalhe(Treinador treinador, Map<Skill, Integer> skills);

    @Mapping(target = "treinadorId", source = "treinador.id")
    PropostaResumo paraResumo(Proposta proposta);

    @Mapping(target = "treinadorId", source = "treinador.id")
    VinculoResumo paraResumo(VinculoTreinador vinculo);
}
