package br.com.api.footfirma.clube.internal;

import br.com.api.footfirma.clube.ClubeService;
import br.com.api.footfirma.clube.domain.Clube;
import br.com.api.footfirma.clube.domain.ClubeAlias;
import br.com.api.footfirma.clube.domain.Estadio;
import br.com.api.footfirma.clube.domain.FonteExterna;
import br.com.api.footfirma.clube.dto.ClubeDetalhe;
import br.com.api.footfirma.clube.dto.ClubeResumo;
import br.com.api.footfirma.clube.dto.DadosDeAlias;
import br.com.api.footfirma.clube.dto.DadosDeClube;
import br.com.api.footfirma.clube.dto.DadosDeEstadio;
import br.com.api.footfirma.clube.mapper.ClubeMapper;
import br.com.api.footfirma.clube.repository.ClubeAliasRepository;
import br.com.api.footfirma.clube.repository.ClubeRepository;
import br.com.api.footfirma.clube.repository.EstadioRepository;
import br.com.api.footfirma.shared.dto.ResultadoDeSincronizacao;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ClubeServiceImpl implements ClubeService {

    private final ClubeRepository clubeRepository;
    private final ClubeAliasRepository clubeAliasRepository;
    private final EstadioRepository estadioRepository;
    private final ClubeMapper clubeMapper;

    @Override
    public Optional<ClubeDetalhe> buscarPorSlug(String slug) {
        return clubeRepository.findBySlug(slug).map(clubeMapper::paraDetalhe);
    }

    @Override
    public Page<ClubeResumo> listar(Pageable paginacao) {
        return clubeRepository.findAllByOrderByNomeCurto(paginacao).map(clubeMapper::paraResumo);
    }

    @Override
    public Optional<ClubeResumo> resolverPorAlias(String alias) {
        return clubeAliasRepository.findByAlias(alias)
                .map(clubeAlias -> clubeMapper.paraResumo(clubeAlias.getClube()));
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarEstadio(DadosDeEstadio dados) {
        var existente = estadioRepository.findByNomeAndCidade(dados.nome(), dados.cidade());
        var estadio = existente.orElseGet(() -> new Estadio(dados.nome(), dados.cidade()));
        estadio.setEstadoId(dados.estadoId());
        estadio.setCapacidade(dados.capacidade());
        estadio.setAnoInauguracao(dados.anoInauguracao());
        var salvo = estadioRepository.save(estadio);
        return existente.isPresent()
                ? ResultadoDeSincronizacao.atualizado(salvo.getId())
                : ResultadoDeSincronizacao.criado(salvo.getId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarClube(DadosDeClube dados) {
        var existente = clubeRepository.findBySlug(dados.slug());
        var clube = existente.orElseGet(() -> new Clube(
                dados.slug(), dados.nomeOficial(), dados.nomeCurto(), dados.paisId()));
        clube.setNomeOficial(dados.nomeOficial());
        clube.setNomeCurto(dados.nomeCurto());
        clube.setApelido(dados.apelido());
        clube.setAnoFundacao(dados.anoFundacao());
        clube.setPaisId(dados.paisId());
        clube.setEstadoId(dados.estadoId());
        // getReferenceById: o alvo é gravar a chave estrangeira, e um SELECT por
        // clube seria desperdício numa carga de dezenas de linhas.
        clube.setEstadio(dados.estadioId() == null
                ? null
                : estadioRepository.getReferenceById(dados.estadioId()));
        clube.setCorPrimaria(dados.corPrimaria());
        clube.setCorSecundaria(dados.corSecundaria());
        clube.setReputacao(dados.reputacao());
        clube.setQualidadeBase(dados.qualidadeBase());
        clube.setEstadoBaseId(dados.estadoBaseId());
        if (existente.isPresent()) {
            clube.setAtualizadoEm(OffsetDateTime.now());
        }
        var salvo = clubeRepository.save(clube);
        return existente.isPresent()
                ? ResultadoDeSincronizacao.atualizado(salvo.getId())
                : ResultadoDeSincronizacao.criado(salvo.getId());
    }

    @Override
    @Transactional
    public ResultadoDeSincronizacao sincronizarAlias(DadosDeAlias dados) {
        var fonte = FonteExterna.valueOf(dados.fonte());
        var existente = clubeAliasRepository.findByAliasAndFonte(dados.alias(), fonte);
        if (existente.isPresent()) {
            return ResultadoDeSincronizacao.atualizado(existente.get().getId());
        }
        var alias = clubeAliasRepository.save(new ClubeAlias(
                clubeRepository.getReferenceById(dados.clubeId()), dados.alias(), fonte));
        return ResultadoDeSincronizacao.criado(alias.getId());
    }
}
