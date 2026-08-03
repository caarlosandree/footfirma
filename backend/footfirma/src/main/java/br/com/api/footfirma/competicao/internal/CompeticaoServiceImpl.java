package br.com.api.footfirma.competicao.internal;

import br.com.api.footfirma.competicao.CompeticaoService;
import br.com.api.footfirma.competicao.domain.Competicao;
import br.com.api.footfirma.competicao.domain.Edicao;
import br.com.api.footfirma.competicao.domain.EdicaoParticipante;
import br.com.api.footfirma.competicao.domain.Fase;
import br.com.api.footfirma.competicao.domain.RegraClassificacao;
import br.com.api.footfirma.competicao.domain.TipoClassificacao;
import br.com.api.footfirma.competicao.domain.TipoCompeticao;
import br.com.api.footfirma.competicao.domain.TipoFase;
import br.com.api.footfirma.competicao.dto.CompeticaoResumo;
import br.com.api.footfirma.competicao.dto.DadosDeCompeticao;
import br.com.api.footfirma.competicao.dto.DadosDeEdicao;
import br.com.api.footfirma.competicao.dto.DadosDeFase;
import br.com.api.footfirma.competicao.dto.DadosDeParticipante;
import br.com.api.footfirma.competicao.dto.DadosDeRegra;
import br.com.api.footfirma.competicao.dto.EdicaoDetalhe;
import br.com.api.footfirma.competicao.mapper.CompeticaoMapper;
import br.com.api.footfirma.competicao.repository.CompeticaoRepository;
import br.com.api.footfirma.competicao.repository.EdicaoParticipanteRepository;
import br.com.api.footfirma.competicao.repository.EdicaoRepository;
import br.com.api.footfirma.competicao.repository.FaseRepository;
import br.com.api.footfirma.competicao.repository.RegraClassificacaoRepository;
import br.com.api.footfirma.shared.dto.ResultadoDeSincronizacao;
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
    private final FaseRepository faseRepository;
    private final EdicaoParticipanteRepository edicaoParticipanteRepository;
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

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarCompeticao(DadosDeCompeticao dados) {
        var existente = competicaoRepository.findBySlug(dados.slug());
        var competicao = existente.orElseGet(() -> new Competicao(
                dados.slug(), dados.nome(), TipoCompeticao.valueOf(dados.tipo())));
        competicao.setNome(dados.nome());
        competicao.setPaisId(dados.paisId());
        competicao.setTipo(TipoCompeticao.valueOf(dados.tipo()));
        competicao.setNivel(dados.nivel());
        competicao.setGenero(dados.genero());
        var salva = competicaoRepository.save(competicao);
        return existente.isPresent()
                ? ResultadoDeSincronizacao.atualizado(salva.getId())
                : ResultadoDeSincronizacao.criado(salva.getId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarEdicao(DadosDeEdicao dados) {
        var existente = edicaoRepository
                .findByCompeticaoIdAndTemporadaId(dados.competicaoId(), dados.temporadaId());
        var edicao = existente.orElseGet(() -> new Edicao(
                competicaoRepository.getReferenceById(dados.competicaoId()),
                dados.temporadaId(), dados.nome()));
        edicao.setNome(dados.nome());
        edicao.setDataInicio(dados.dataInicio());
        edicao.setDataFim(dados.dataFim());
        var salva = edicaoRepository.save(edicao);
        return existente.isPresent()
                ? ResultadoDeSincronizacao.atualizado(salva.getId())
                : ResultadoDeSincronizacao.criado(salva.getId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarFase(DadosDeFase dados) {
        var existente = faseRepository.findByEdicaoIdAndOrdem(dados.edicaoId(), dados.ordem());
        var fase = existente.orElseGet(() -> new Fase(
                edicaoRepository.getReferenceById(dados.edicaoId()),
                dados.ordem(), dados.nome(), TipoFase.valueOf(dados.tipo())));
        fase.setNome(dados.nome());
        fase.setTipo(TipoFase.valueOf(dados.tipo()));
        fase.setJogosPorConfronto(dados.jogosPorConfronto());
        fase.setTemGolFora(dados.temGolFora());
        fase.setTemProrrogacao(dados.temProrrogacao());
        fase.setTemPenaltis(dados.temPenaltis());
        var salva = faseRepository.save(fase);
        return existente.isPresent()
                ? ResultadoDeSincronizacao.atualizado(salva.getId())
                : ResultadoDeSincronizacao.criado(salva.getId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarParticipante(DadosDeParticipante dados) {
        var existente = edicaoParticipanteRepository
                .findByEdicaoIdAndClubeId(dados.edicaoId(), dados.clubeId());
        var participante = existente.orElseGet(() -> new EdicaoParticipante(
                edicaoRepository.getReferenceById(dados.edicaoId()), dados.clubeId()));
        participante.setPosicaoFinal(dados.posicaoFinal());
        var salvo = edicaoParticipanteRepository.save(participante);
        return existente.isPresent()
                ? ResultadoDeSincronizacao.atualizado(salvo.getId())
                : ResultadoDeSincronizacao.criado(salvo.getId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarRegra(DadosDeRegra dados) {
        var tipo = TipoClassificacao.valueOf(dados.tipo());
        var existente = regraClassificacaoRepository
                .findByEdicaoIdAndPosicaoInicioAndPosicaoFimAndTipo(
                        dados.edicaoId(), dados.posicaoInicio(), dados.posicaoFim(), tipo);
        if (existente.isPresent()) {
            existente.get().setCompeticaoDestinoId(dados.competicaoDestinoId());
            return ResultadoDeSincronizacao.atualizado(existente.get().getId());
        }
        var regra = regraClassificacaoRepository.save(new RegraClassificacao(
                edicaoRepository.getReferenceById(dados.edicaoId()),
                dados.posicaoInicio(), dados.posicaoFim(), tipo, dados.competicaoDestinoId()));
        return ResultadoDeSincronizacao.criado(regra.getId());
    }

    @Override
    public Optional<Long> buscarIdPorSlug(String slug) {
        return competicaoRepository.findBySlug(slug).map(Competicao::getId);
    }
}
