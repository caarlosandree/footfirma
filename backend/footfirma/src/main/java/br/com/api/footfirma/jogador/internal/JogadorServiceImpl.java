package br.com.api.footfirma.jogador.internal;

import br.com.api.footfirma.clube.ClubeService;
import br.com.api.footfirma.jogador.JogadorService;
import br.com.api.footfirma.jogador.domain.Jogador;
import br.com.api.footfirma.jogador.dto.JogadorDetalhe;
import br.com.api.footfirma.jogador.dto.JogadorResumo;
import br.com.api.footfirma.jogador.dto.PosicaoCatalogo;
import br.com.api.footfirma.jogador.mapper.JogadorMapper;
import br.com.api.footfirma.jogador.repository.JogadorAtributoRepository;
import br.com.api.footfirma.jogador.repository.JogadorRepository;
import br.com.api.footfirma.jogador.repository.JogadorVinculoRepository;
import br.com.api.footfirma.jogador.repository.PosicaoRepository;
import br.com.api.footfirma.temporada.TemporadaService;
import br.com.api.footfirma.temporada.dto.TemporadaResumo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JogadorServiceImpl implements JogadorService {

    private final JogadorRepository jogadorRepository;
    private final JogadorAtributoRepository jogadorAtributoRepository;
    private final JogadorVinculoRepository jogadorVinculoRepository;
    private final PosicaoRepository posicaoRepository;
    private final TemporadaService temporadaService;
    private final ClubeService clubeService;
    private final JogadorMapper jogadorMapper;

    @Override
    public Optional<JogadorDetalhe> buscarPorSlug(String slug, String labelTemporada) {
        var temporadaId = temporadaService.buscarPorLabel(labelTemporada).map(TemporadaResumo::id);
        if (temporadaId.isEmpty()) {
            return Optional.empty();
        }
        return jogadorRepository.findBySlug(slug)
                .map(jogador -> montarDetalhe(jogador, temporadaId.get()));
    }

    @Override
    public List<JogadorResumo> listarElenco(String slugClube, String labelTemporada) {
        var clube = clubeService.buscarPorSlug(slugClube);
        var temporadaId = temporadaService.buscarPorLabel(labelTemporada).map(TemporadaResumo::id);
        if (clube.isEmpty() || temporadaId.isEmpty()) {
            return List.of();
        }
        return jogadorVinculoRepository.buscarElenco(clube.get().id(), temporadaId.get()).stream()
                .map(vinculo -> new JogadorResumo(
                        vinculo.getJogador().getId(),
                        vinculo.getJogador().getSlug(),
                        vinculo.getJogador().getNomeExibicao(),
                        idadeEm(vinculo.getJogador().getDataNascimento()),
                        vinculo.getJogador().getPosicaoPrincipal().getCodigo(),
                        vinculo.getNumeroCamisa()))
                .toList();
    }

    @Override
    public List<PosicaoCatalogo> listarPosicoes() {
        return posicaoRepository.findAllByOrderByOrdem().stream()
                .map(posicao -> new PosicaoCatalogo(
                        posicao.getId(),
                        posicao.getCodigo(),
                        posicao.getNome(),
                        posicao.getSetor().name()))
                .toList();
    }

    private JogadorDetalhe montarDetalhe(Jogador jogador, Long temporadaId) {
        var atributos = jogadorAtributoRepository
                .findByJogadorIdAndTemporadaId(jogador.getId(), temporadaId)
                .map(jogadorMapper::paraAtributos)
                .orElse(null);
        return new JogadorDetalhe(
                jogador.getId(),
                jogador.getSlug(),
                jogador.getNomeCompleto(),
                jogador.getNomeExibicao(),
                jogador.getDataNascimento(),
                idadeEm(jogador.getDataNascimento()),
                jogador.getAlturaCm(),
                jogador.getPesoKg(),
                jogador.getPePreferido().name(),
                jogador.getPosicaoPrincipal().getCodigo(),
                jogador.getOrigem().name(),
                atributos,
                // Vazia até o importador do Plano 3 preencher jogador_caracteristica.
                // Devolver lista vazia é honesto; inventar dado não seria.
                List.of());
    }

    private Integer idadeEm(LocalDate nascimento) {
        return Period.between(nascimento, LocalDate.now()).getYears();
    }
}
