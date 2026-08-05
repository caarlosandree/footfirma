package br.com.api.footfirma.avaliacao.internal;

import br.com.api.footfirma.avaliacao.AvaliacaoService;
import br.com.api.footfirma.avaliacao.domain.JogadorOverall;
import br.com.api.footfirma.avaliacao.dto.AvaliacaoDePosicao;
import br.com.api.footfirma.avaliacao.dto.AvaliacoesDoJogador;
import br.com.api.footfirma.avaliacao.dto.ItemDeRanking;
import br.com.api.footfirma.avaliacao.dto.OverallDeJogador;
import br.com.api.footfirma.avaliacao.dto.ResultadoMaterializacao;
import br.com.api.footfirma.avaliacao.repository.JogadorOverallRepository;
import br.com.api.footfirma.avaliacao.repository.PerfilAvaliacaoRepository;
import br.com.api.footfirma.jogador.JogadorService;
import br.com.api.footfirma.jogador.dto.JogadorResumo;
import br.com.api.footfirma.jogador.dto.PosicaoCatalogo;
import br.com.api.footfirma.temporada.TemporadaService;
import br.com.api.footfirma.temporada.dto.TemporadaResumo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class AvaliacaoServiceImpl implements AvaliacaoService {

    private static final int TAMANHO_DO_LOTE = 200;

    private final PerfilAvaliacaoRepository perfilAvaliacaoRepository;
    private final JogadorOverallRepository jogadorOverallRepository;
    private final MaterializadorDeLote materializadorDeLote;
    private final JogadorService jogadorService;
    private final TemporadaService temporadaService;

    @Override
    public List<OverallDeJogador> listarOveralls(Collection<Long> jogadorIds, long temporadaId) {
        if (jogadorIds.isEmpty()) {
            return List.of();
        }
        return jogadorOverallRepository
                .findByTemporadaIdAndJogadorIdIn(temporadaId, jogadorIds).stream()
                .map(registro -> new OverallDeJogador(registro.getJogadorId(),
                        registro.getPosicaoId(), registro.getOverall()))
                .toList();
    }

    @Override
    public Optional<AvaliacoesDoJogador> buscarPorSlug(String slug, String labelTemporada) {
        var temporadaId = temporadaService.buscarPorLabel(labelTemporada).map(TemporadaResumo::id);
        var jogadorId = jogadorService.buscarIdPorSlug(slug);
        if (temporadaId.isEmpty() || jogadorId.isEmpty()) {
            return Optional.empty();
        }
        var registros = jogadorOverallRepository
                .findByJogadorIdAndTemporadaIdOrderByOverallDesc(jogadorId.get(), temporadaId.get());
        if (registros.isEmpty()) {
            return Optional.empty();
        }
        var codigoPorId = codigoPorIdDePosicao();
        var avaliacoes = registros.stream()
                .map(registro -> new AvaliacaoDePosicao(
                        codigoPorId.get(registro.getPosicaoId()),
                        registro.getOverall(),
                        registro.getPerfilVersao()))
                .toList();
        return Optional.of(new AvaliacoesDoJogador(slug, labelTemporada, avaliacoes));
    }

    @Override
    public Page<ItemDeRanking> ranquear(String labelTemporada, String codigoPosicao,
                                        Integer overallMinimo, Pageable pageable) {
        var temporadaId = temporadaService.buscarPorLabel(labelTemporada).map(TemporadaResumo::id);
        var posicao = jogadorService.listarPosicoes().stream()
                .filter(candidata -> candidata.codigo().equals(codigoPosicao))
                .findFirst();
        if (temporadaId.isEmpty() || posicao.isEmpty()) {
            return Page.empty(pageable);
        }
        var pagina = jogadorOverallRepository.ranquear(
                temporadaId.get(), posicao.get().id(),
                overallMinimo == null ? 0 : overallMinimo, pageable);

        var ids = pagina.getContent().stream().map(JogadorOverall::getJogadorId).toList();
        // Uma chamada para a página inteira; listarResumosPorIds não garante ordem,
        // então a ordem do ranking é reimposta abaixo.
        var resumoPorId = jogadorService.listarResumosPorIds(ids, labelTemporada).stream()
                .collect(Collectors.toMap(JogadorResumo::id, Function.identity()));

        return pagina.map(registro -> {
            var resumo = resumoPorId.get(registro.getJogadorId());
            return new ItemDeRanking(
                    resumo == null ? null : resumo.slug(),
                    resumo == null ? null : resumo.nomeExibicao(),
                    resumo == null ? null : resumo.idade(),
                    resumo == null ? null : resumo.posicao(),
                    codigoPosicao,
                    registro.getOverall());
        });
    }

    private Map<Long, String> codigoPorIdDePosicao() {
        return jogadorService.listarPosicoes().stream()
                .collect(Collectors.toMap(PosicaoCatalogo::id, PosicaoCatalogo::codigo));
    }

    // NOT_SUPPORTED de propósito: este método não é uma unidade transacional, cada
    // lote é. Sem isso, a transação readOnly da classe englobaria a escrita e
    // seguraria uma conexão do início ao fim da carga.
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResultadoMaterializacao materializar(String labelTemporada) {
        var temporada = temporadaService.buscarPorLabel(labelTemporada);
        if (temporada.isEmpty()) {
            return new ResultadoMaterializacao(labelTemporada, 0, 0);
        }
        var temporadaId = temporada.map(TemporadaResumo::id).orElseThrow();
        var perfis = perfilAvaliacaoRepository.buscarAtivosComPesos();

        var jogadores = 0;
        var linhas = 0;
        var pagina = 0;
        boolean temProxima;
        do {
            // Ordenação total e estável: sem ela a paginação entre commits de lote
            // pode pular uma página, e o upsert não protege contra omissão.
            var lote = jogadorService.listarAtributosPorTemporada(labelTemporada,
                    PageRequest.of(pagina, TAMANHO_DO_LOTE, Sort.by(Sort.Direction.ASC, "jogador.id")));
            if (!lote.isEmpty()) {
                linhas += materializadorDeLote.gravarLote(lote.getContent(), temporadaId, perfis);
                jogadores += lote.getNumberOfElements();
            }
            temProxima = lote.hasNext();
            pagina++;
        } while (temProxima);

        return new ResultadoMaterializacao(labelTemporada, jogadores, linhas);
    }
}
