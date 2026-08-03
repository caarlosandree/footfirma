package br.com.api.footfirma.competicao;

import br.com.api.footfirma.competicao.dto.CompeticaoResumo;
import br.com.api.footfirma.competicao.dto.DadosDeCompeticao;
import br.com.api.footfirma.competicao.dto.DadosDeEdicao;
import br.com.api.footfirma.competicao.dto.DadosDeFase;
import br.com.api.footfirma.competicao.dto.DadosDeParticipante;
import br.com.api.footfirma.competicao.dto.DadosDeRegra;
import br.com.api.footfirma.competicao.dto.EdicaoDetalhe;
import br.com.api.footfirma.shared.dto.ResultadoDeSincronizacao;

import java.util.List;
import java.util.Optional;

public interface CompeticaoService {

    List<CompeticaoResumo> listarPorPais(String isoPais);

    Optional<EdicaoDetalhe> buscarEdicao(String slugCompeticao, String labelTemporada);

    /** Upsert por {@code slug}. */
    ResultadoDeSincronizacao sincronizarCompeticao(DadosDeCompeticao dados);

    /** Upsert por {@code (competicao, temporada)}. */
    ResultadoDeSincronizacao sincronizarEdicao(DadosDeEdicao dados);

    /** Upsert por {@code (edicao, ordem)}. */
    ResultadoDeSincronizacao sincronizarFase(DadosDeFase dados);

    /** Upsert por {@code (edicao, clube)}. */
    ResultadoDeSincronizacao sincronizarParticipante(DadosDeParticipante dados);

    /** Upsert por {@code (edicao, faixa, tipo)}. */
    ResultadoDeSincronizacao sincronizarRegra(DadosDeRegra dados);

    /** Resolve {@code slug -> id}; regra de classificação aponta para competição destino. */
    Optional<Long> buscarIdPorSlug(String slug);
}
