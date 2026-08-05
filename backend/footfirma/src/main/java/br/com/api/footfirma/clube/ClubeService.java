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

    /**
     * O estádio onde o clube manda, por id.
     *
     * <p>{@code ClubeDetalhe} já traz o estádio, mas só é alcançável por slug — e quem
     * grava {@code jogo.estadio_id} tem o id do clube, vindo da lista de participantes.
     *
     * <p>Devolve vazio quando o clube não existe ou não tem estádio: {@code clube.estadio_id}
     * é anulável desde a V2.
     */
    Optional<Long> buscarEstadioPorClubeId(long clubeId);

    /** Upsert por {@code (nome, cidade)} — a chave natural criada em V14. */
    ResultadoDeSincronizacao sincronizarEstadio(DadosDeEstadio dados);

    /** Upsert por {@code slug}. */
    ResultadoDeSincronizacao sincronizarClube(DadosDeClube dados);

    /** Upsert por {@code (alias, fonte)}. */
    ResultadoDeSincronizacao sincronizarAlias(DadosDeAlias dados);
}
