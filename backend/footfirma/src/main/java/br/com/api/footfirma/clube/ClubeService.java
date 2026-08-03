package br.com.api.footfirma.clube;

import br.com.api.footfirma.clube.dto.ClubeDetalhe;
import br.com.api.footfirma.clube.dto.ClubeResumo;
import br.com.api.footfirma.clube.dto.DadosDeAlias;
import br.com.api.footfirma.clube.dto.DadosDeClube;
import br.com.api.footfirma.clube.dto.DadosDeEstadio;
import br.com.api.footfirma.shared.dto.ResultadoDeSincronizacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ClubeService {

    Optional<ClubeDetalhe> buscarPorSlug(String slug);

    Page<ClubeResumo> listar(Pageable paginacao);

    Optional<ClubeResumo> resolverPorAlias(String alias);

    /** Upsert por {@code (nome, cidade)} — a chave natural criada em V14. */
    ResultadoDeSincronizacao sincronizarEstadio(DadosDeEstadio dados);

    /** Upsert por {@code slug}. */
    ResultadoDeSincronizacao sincronizarClube(DadosDeClube dados);

    /** Upsert por {@code (alias, fonte)}. */
    ResultadoDeSincronizacao sincronizarAlias(DadosDeAlias dados);
}
