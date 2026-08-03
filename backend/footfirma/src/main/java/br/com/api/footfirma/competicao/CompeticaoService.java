package br.com.api.footfirma.competicao;

import br.com.api.footfirma.competicao.dto.CompeticaoResumo;
import br.com.api.footfirma.competicao.dto.EdicaoDetalhe;

import java.util.List;
import java.util.Optional;

public interface CompeticaoService {

    List<CompeticaoResumo> listarPorPais(String isoPais);

    Optional<EdicaoDetalhe> buscarEdicao(String slugCompeticao, String labelTemporada);
}
