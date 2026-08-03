package br.com.api.footfirma.clube.internal;

import br.com.api.footfirma.clube.ClubeService;
import br.com.api.footfirma.clube.dto.ClubeDetalhe;
import br.com.api.footfirma.clube.dto.ClubeResumo;
import br.com.api.footfirma.clube.mapper.ClubeMapper;
import br.com.api.footfirma.clube.repository.ClubeAliasRepository;
import br.com.api.footfirma.clube.repository.ClubeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ClubeServiceImpl implements ClubeService {

    private final ClubeRepository clubeRepository;
    private final ClubeAliasRepository clubeAliasRepository;
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
}
