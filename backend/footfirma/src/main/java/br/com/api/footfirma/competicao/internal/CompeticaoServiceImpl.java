package br.com.api.footfirma.competicao.internal;

import br.com.api.footfirma.competicao.CompeticaoService;
import br.com.api.footfirma.competicao.dto.CompeticaoResumo;
import br.com.api.footfirma.competicao.dto.EdicaoDetalhe;
import br.com.api.footfirma.competicao.mapper.CompeticaoMapper;
import br.com.api.footfirma.competicao.repository.CompeticaoRepository;
import br.com.api.footfirma.competicao.repository.EdicaoRepository;
import br.com.api.footfirma.competicao.repository.RegraClassificacaoRepository;
import br.com.api.footfirma.temporada.TemporadaService;
import br.com.api.footfirma.temporada.dto.TemporadaResumo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class CompeticaoServiceImpl implements CompeticaoService {

    private final CompeticaoRepository competicaoRepository;
    private final EdicaoRepository edicaoRepository;
    private final RegraClassificacaoRepository regraClassificacaoRepository;
    private final TemporadaService temporadaService;
    private final CompeticaoMapper competicaoMapper;

    @Override
    public List<CompeticaoResumo> listarPorPais(String isoPais) {
        return competicaoMapper.paraResumos(competicaoRepository.findByPaisIso(isoPais));
    }

    @Override
    public Optional<EdicaoDetalhe> buscarEdicao(String slugCompeticao, String labelTemporada) {
        return temporadaService.buscarPorLabel(labelTemporada)
                .map(TemporadaResumo::id)
                .flatMap(temporadaId -> edicaoRepository.buscarPorSlugETemporadaId(slugCompeticao, temporadaId))
                .map(edicao -> new EdicaoDetalhe(
                        edicao.getId(),
                        edicao.getNome(),
                        edicao.getCompeticao().getNome(),
                        edicao.getDataInicio(),
                        edicao.getDataFim(),
                        competicaoMapper.paraFases(edicao.getFases()),
                        competicaoMapper.paraRegras(
                                regraClassificacaoRepository.findByEdicaoIdOrderByPosicaoInicio(edicao.getId()))));
    }
}
