package br.com.api.footfirma.jogador;

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
import br.com.api.footfirma.shared.dto.ResultadoDeSincronizacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JogadorService {

    Optional<JogadorDetalhe> buscarPorSlug(String slug, String labelTemporada);

    List<JogadorResumo> listarElenco(String slugClube, String labelTemporada);

    List<PosicaoCatalogo> listarPosicoes();

    Page<JogadorComAtributos> listarAtributosPorTemporada(String labelTemporada, Pageable pageable);

    /** Não garante ordem: quem chama reordena conforme sua própria consulta. */
    List<JogadorResumo> listarResumosPorIds(Collection<Long> ids, String labelTemporada);

    Optional<Long> buscarIdPorSlug(String slug);

    /** Upsert por {@code chaveNatural}. O slug é atualizado, nunca usado como identidade. */
    ResultadoDeSincronizacao sincronizarJogador(DadosDeJogador dados);

    /** Upsert por {@code (jogador, posicao)}. */
    ResultadoDeSincronizacao sincronizarPosicaoSecundaria(DadosDePosicaoSecundaria dados);

    /** Upsert por {@code (jogador, temporada)} — atributos são versionados por temporada. */
    ResultadoDeSincronizacao sincronizarAtributos(DadosDeAtributos dados);

    /** Upsert por jogador: a PK da tabela é o próprio {@code jogador_id}. */
    ResultadoDeSincronizacao sincronizarAtributosOcultos(DadosDeAtributosOcultos dados);

    /** Upsert por {@code (jogador, caracteristica)}. */
    ResultadoDeSincronizacao sincronizarCaracteristica(DadosDeCaracteristica dados);

    /** Upsert por {@code (jogador, temporada, clube)} — dois clubes na mesma temporada são válidos. */
    ResultadoDeSincronizacao sincronizarVinculo(DadosDeVinculo dados);

    /** Resolve {@code codigo -> id}; o dataset referencia característica por código. */
    Optional<Long> buscarIdCaracteristicaPorCodigo(String codigo);
}
