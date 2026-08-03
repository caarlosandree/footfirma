package br.com.api.footfirma.competicao.mapper;

import br.com.api.footfirma.competicao.domain.Competicao;
import br.com.api.footfirma.competicao.domain.Fase;
import br.com.api.footfirma.competicao.domain.RegraClassificacao;
import br.com.api.footfirma.competicao.dto.CompeticaoResumo;
import br.com.api.footfirma.competicao.dto.FaseResumo;
import br.com.api.footfirma.competicao.dto.RegraClassificacaoResumo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
public interface CompeticaoMapper {

    @Mapping(target = "tipo", expression = "java(competicao.getTipo().name())")
    CompeticaoResumo paraResumo(Competicao competicao);

    List<CompeticaoResumo> paraResumos(List<Competicao> competicoes);

    @Mapping(target = "tipo", expression = "java(fase.getTipo().name())")
    FaseResumo paraResumo(Fase fase);

    List<FaseResumo> paraFases(List<Fase> fases);

    @Mapping(target = "tipo", expression = "java(regra.getTipo().name())")
    RegraClassificacaoResumo paraResumo(RegraClassificacao regra);

    List<RegraClassificacaoResumo> paraRegras(List<RegraClassificacao> regras);
}
