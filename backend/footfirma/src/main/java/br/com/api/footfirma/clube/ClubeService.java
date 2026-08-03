package br.com.api.footfirma.clube;

import br.com.api.footfirma.clube.dto.ClubeDetalhe;
import br.com.api.footfirma.clube.dto.ClubeResumo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ClubeService {

    Optional<ClubeDetalhe> buscarPorSlug(String slug);

    Page<ClubeResumo> listar(Pageable paginacao);

    Optional<ClubeResumo> resolverPorAlias(String alias);
}
