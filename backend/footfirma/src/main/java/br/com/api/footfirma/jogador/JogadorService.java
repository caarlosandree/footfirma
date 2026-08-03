package br.com.api.footfirma.jogador;

import br.com.api.footfirma.jogador.dto.JogadorComAtributos;
import br.com.api.footfirma.jogador.dto.JogadorDetalhe;
import br.com.api.footfirma.jogador.dto.JogadorResumo;
import br.com.api.footfirma.jogador.dto.PosicaoCatalogo;
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
}
