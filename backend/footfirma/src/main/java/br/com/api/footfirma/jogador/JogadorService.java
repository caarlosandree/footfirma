package br.com.api.footfirma.jogador;

import br.com.api.footfirma.jogador.dto.JogadorDetalhe;
import br.com.api.footfirma.jogador.dto.JogadorResumo;
import br.com.api.footfirma.jogador.dto.PosicaoCatalogo;

import java.util.List;
import java.util.Optional;

public interface JogadorService {

    Optional<JogadorDetalhe> buscarPorSlug(String slug, String labelTemporada);

    List<JogadorResumo> listarElenco(String slugClube, String labelTemporada);

    List<PosicaoCatalogo> listarPosicoes();
}
