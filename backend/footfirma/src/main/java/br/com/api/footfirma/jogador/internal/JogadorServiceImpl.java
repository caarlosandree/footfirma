package br.com.api.footfirma.jogador.internal;

import br.com.api.footfirma.clube.ClubeService;
import br.com.api.footfirma.jogador.JogadorService;
import br.com.api.footfirma.jogador.domain.Caracteristica;
import br.com.api.footfirma.jogador.domain.CategoriaDeElenco;
import br.com.api.footfirma.jogador.domain.FonteAtributo;
import br.com.api.footfirma.jogador.domain.Jogador;
import br.com.api.footfirma.jogador.domain.JogadorAtributo;
import br.com.api.footfirma.jogador.domain.JogadorAtributoOculto;
import br.com.api.footfirma.jogador.domain.JogadorCaracteristica;
import br.com.api.footfirma.jogador.domain.JogadorPosicao;
import br.com.api.footfirma.jogador.domain.JogadorVinculo;
import br.com.api.footfirma.jogador.domain.OrigemJogador;
import br.com.api.footfirma.jogador.domain.PeParaChute;
import br.com.api.footfirma.jogador.domain.TipoVinculo;
import br.com.api.footfirma.jogador.dto.DadosDeAtributos;
import br.com.api.footfirma.jogador.dto.DadosDeAtributosOcultos;
import br.com.api.footfirma.jogador.dto.DadosDeCaracteristica;
import br.com.api.footfirma.jogador.dto.DadosDeJogador;
import br.com.api.footfirma.jogador.dto.DadosDePosicaoSecundaria;
import br.com.api.footfirma.jogador.dto.DadosDeVinculo;
import br.com.api.footfirma.jogador.dto.JogadorComAtributos;
import br.com.api.footfirma.jogador.dto.JogadorDetalhe;
import br.com.api.footfirma.jogador.dto.JogadorResumo;
import br.com.api.footfirma.jogador.dto.PosicaoCatalogo;
import br.com.api.footfirma.jogador.mapper.JogadorMapper;
import br.com.api.footfirma.jogador.repository.CaracteristicaRepository;
import br.com.api.footfirma.jogador.repository.JogadorAtributoOcultoRepository;
import br.com.api.footfirma.jogador.repository.JogadorAtributoRepository;
import br.com.api.footfirma.jogador.repository.JogadorCaracteristicaRepository;
import br.com.api.footfirma.jogador.repository.JogadorPosicaoRepository;
import br.com.api.footfirma.jogador.repository.JogadorRepository;
import br.com.api.footfirma.jogador.repository.JogadorVinculoRepository;
import br.com.api.footfirma.jogador.repository.PosicaoRepository;
import br.com.api.footfirma.shared.dto.ResultadoDeSincronizacao;
import br.com.api.footfirma.temporada.TemporadaService;
import br.com.api.footfirma.temporada.dto.TemporadaResumo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JogadorServiceImpl implements JogadorService {

    private final JogadorRepository jogadorRepository;
    private final JogadorAtributoRepository jogadorAtributoRepository;
    private final JogadorAtributoOcultoRepository jogadorAtributoOcultoRepository;
    private final JogadorVinculoRepository jogadorVinculoRepository;
    private final JogadorPosicaoRepository jogadorPosicaoRepository;
    private final JogadorCaracteristicaRepository jogadorCaracteristicaRepository;
    private final CaracteristicaRepository caracteristicaRepository;
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

    @Override
    public Page<JogadorComAtributos> listarAtributosPorTemporada(String labelTemporada, Pageable pageable) {
        var temporadaId = temporadaService.buscarPorLabel(labelTemporada).map(TemporadaResumo::id);
        if (temporadaId.isEmpty()) {
            return Page.empty(pageable);
        }
        // getJogador().getId() lê o id do proxy LAZY sem disparar select: não há N+1 aqui.
        return jogadorAtributoRepository.findByTemporadaId(temporadaId.get(), pageable)
                .map(atributo -> new JogadorComAtributos(
                        atributo.getJogador().getId(),
                        jogadorMapper.paraAtributos(atributo)));
    }

    @Override
    public List<JogadorResumo> listarResumosPorIds(Collection<Long> ids, String labelTemporada) {
        if (ids.isEmpty()) {
            return List.of();
        }
        var temporadaId = temporadaService.buscarPorLabel(labelTemporada).map(TemporadaResumo::id);
        // Duas consultas para a página inteira, nunca uma por jogador.
        var camisas = temporadaId
                .map(id -> jogadorVinculoRepository.buscarPorJogadoresETemporada(ids, id).stream()
                        .filter(vinculo -> vinculo.getNumeroCamisa() != null)
                        .collect(Collectors.toMap(
                                vinculo -> vinculo.getJogador().getId(),
                                JogadorVinculo::getNumeroCamisa,
                                (primeiro, segundo) -> primeiro)))
                .orElseGet(Map::of);
        return jogadorRepository.buscarPorIds(ids).stream()
                .map(jogador -> new JogadorResumo(
                        jogador.getId(),
                        jogador.getSlug(),
                        jogador.getNomeExibicao(),
                        idadeEm(jogador.getDataNascimento()),
                        jogador.getPosicaoPrincipal().getCodigo(),
                        camisas.get(jogador.getId())))
                .toList();
    }

    @Override
    public Optional<Long> buscarIdPorSlug(String slug) {
        return jogadorRepository.findBySlug(slug).map(Jogador::getId);
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
                // Vazia até o gerador de mundo preencher jogador_caracteristica.
                // Devolver lista vazia é honesto; inventar dado não seria.
                List.of());
    }

    private Integer idadeEm(LocalDate nascimento) {
        return Period.between(nascimento, LocalDate.now()).getYears();
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarJogador(DadosDeJogador dados) {
        // Busca pela chave natural, nunca pelo slug: slug é rótulo, chave natural é identidade.
        var existente = jogadorRepository.findByChaveNatural(dados.chaveNatural());
        var jogador = existente.orElseGet(() -> new Jogador(
                dados.slug(), dados.chaveNatural(), dados.nomeCompleto(), dados.nomeExibicao(),
                dados.dataNascimento(), dados.paisId(),
                posicaoRepository.getReferenceById(dados.posicaoPrincipalId()),
                PeParaChute.valueOf(dados.pePreferido()), dados.semente()));
        jogador.setSlug(dados.slug());
        jogador.setNomeCompleto(dados.nomeCompleto());
        jogador.setNomeExibicao(dados.nomeExibicao());
        jogador.setDataNascimento(dados.dataNascimento());
        jogador.setPaisId(dados.paisId());
        jogador.setSegundaNacionalidadeId(dados.segundaNacionalidadeId());
        jogador.setAlturaCm(dados.alturaCm());
        jogador.setPesoKg(dados.pesoKg());
        jogador.setPePreferido(PeParaChute.valueOf(dados.pePreferido()));
        jogador.setPosicaoPrincipal(posicaoRepository.getReferenceById(dados.posicaoPrincipalId()));
        jogador.setSemente(dados.semente());
        jogador.setOrigem(OrigemJogador.valueOf(dados.origem()));
        if (existente.isPresent()) {
            jogador.setAtualizadoEm(OffsetDateTime.now());
        }
        var salvo = jogadorRepository.save(jogador);
        return existente.isPresent()
                ? ResultadoDeSincronizacao.atualizado(salvo.getId())
                : ResultadoDeSincronizacao.criado(salvo.getId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarPosicaoSecundaria(DadosDePosicaoSecundaria dados) {
        var existente = jogadorPosicaoRepository
                .findByJogadorIdAndPosicaoId(dados.jogadorId(), dados.posicaoId());
        if (existente.isPresent()) {
            existente.get().setOrdem(dados.ordem());
            return ResultadoDeSincronizacao.atualizado(dados.jogadorId());
        }
        jogadorPosicaoRepository.save(new JogadorPosicao(
                jogadorRepository.getReferenceById(dados.jogadorId()),
                posicaoRepository.getReferenceById(dados.posicaoId()),
                dados.ordem()));
        // Devolve o jogadorId: a tabela tem chave composta e nenhuma coluna id própria.
        return ResultadoDeSincronizacao.criado(dados.jogadorId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarAtributos(DadosDeAtributos dados) {
        var existente = jogadorAtributoRepository
                .findByJogadorIdAndTemporadaId(dados.jogadorId(), dados.temporadaId());
        var atributo = existente.orElseGet(() -> new JogadorAtributo(
                jogadorRepository.getReferenceById(dados.jogadorId()), dados.temporadaId(),
                FonteAtributo.valueOf(dados.fonteAtributo())));
        atributo.setRitmo(dados.ritmo());
        atributo.setForca(dados.forca());
        atributo.setFolego(dados.folego());
        atributo.setSalto(dados.salto());
        atributo.setAgilidade(dados.agilidade());
        atributo.setPasse(dados.passe());
        atributo.setDrible(dados.drible());
        atributo.setCruzamento(dados.cruzamento());
        atributo.setFrieza(dados.frieza());
        atributo.setFinalizacao(dados.finalizacao());
        atributo.setCabeceio(dados.cabeceio());
        atributo.setFalta(dados.falta());
        atributo.setPenalti(dados.penalti());
        atributo.setDesarme(dados.desarme());
        atributo.setMarcacao(dados.marcacao());
        atributo.setGolReflexo(dados.golReflexo());
        atributo.setGolPosicionamento(dados.golPosicionamento());
        atributo.setGolManejo(dados.golManejo());
        atributo.setPotencialBase(dados.potencialBase());
        atributo.setPotencialVariacao(dados.potencialVariacao());
        atributo.setFonteAtributo(FonteAtributo.valueOf(dados.fonteAtributo()));
        // Vem do dataset, não de now(): é quando o dado foi coletado, não importado.
        atributo.setColetadoEm(dados.coletadoEm());
        var salvo = jogadorAtributoRepository.save(atributo);
        return existente.isPresent()
                ? ResultadoDeSincronizacao.atualizado(salvo.getId())
                : ResultadoDeSincronizacao.criado(salvo.getId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarAtributosOcultos(DadosDeAtributosOcultos dados) {
        var existente = jogadorAtributoOcultoRepository.findById(dados.jogadorId());
        var ocultos = existente.orElseGet(() -> new JogadorAtributoOculto(
                jogadorRepository.getReferenceById(dados.jogadorId())));
        ocultos.setProfissionalismo(dados.profissionalismo());
        ocultos.setAmbicao(dados.ambicao());
        ocultos.setLealdade(dados.lealdade());
        ocultos.setTemperamento(dados.temperamento());
        ocultos.setLideranca(dados.lideranca());
        ocultos.setRegularidade(dados.regularidade());
        ocultos.setPropensaoLesao(dados.propensaoLesao());
        ocultos.setResistenciaPressao(dados.resistenciaPressao());
        jogadorAtributoOcultoRepository.save(ocultos);
        return existente.isPresent()
                ? ResultadoDeSincronizacao.atualizado(dados.jogadorId())
                : ResultadoDeSincronizacao.criado(dados.jogadorId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarCaracteristica(DadosDeCaracteristica dados) {
        var existente = jogadorCaracteristicaRepository
                .findByJogadorIdAndCaracteristicaId(dados.jogadorId(), dados.caracteristicaId());
        if (existente.isPresent()) {
            return ResultadoDeSincronizacao.atualizado(dados.jogadorId());
        }
        jogadorCaracteristicaRepository.save(new JogadorCaracteristica(
                jogadorRepository.getReferenceById(dados.jogadorId()),
                caracteristicaRepository.getReferenceById(dados.caracteristicaId())));
        return ResultadoDeSincronizacao.criado(dados.jogadorId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarVinculo(DadosDeVinculo dados) {
        var existente = jogadorVinculoRepository.findByJogadorIdAndTemporadaIdAndClubeId(
                dados.jogadorId(), dados.temporadaId(), dados.clubeId());
        var vinculo = existente.orElseGet(() -> new JogadorVinculo(
                jogadorRepository.getReferenceById(dados.jogadorId()),
                dados.clubeId(), dados.temporadaId(), TipoVinculo.valueOf(dados.tipo())));
        vinculo.setTipo(TipoVinculo.valueOf(dados.tipo()));
        vinculo.setCategoria(CategoriaDeElenco.valueOf(dados.categoria()));
        vinculo.setNumeroCamisa(dados.numeroCamisa());
        vinculo.setDataInicio(dados.dataInicio());
        vinculo.setDataFim(dados.dataFim());
        vinculo.setValorMercadoEur(dados.valorMercadoEur());
        var salvo = jogadorVinculoRepository.save(vinculo);
        return existente.isPresent()
                ? ResultadoDeSincronizacao.atualizado(salvo.getId())
                : ResultadoDeSincronizacao.criado(salvo.getId());
    }

    @Override
    public Optional<Long> buscarIdCaracteristicaPorCodigo(String codigo) {
        return caracteristicaRepository.findByCodigo(codigo).map(Caracteristica::getId);
    }
}
