package br.com.api.footfirma.competicao;

import br.com.api.footfirma.competicao.dto.CompeticaoResumo;
import br.com.api.footfirma.competicao.dto.DadosDeCompeticao;
import br.com.api.footfirma.competicao.dto.DadosDeEdicao;
import br.com.api.footfirma.competicao.dto.DadosDeFase;
import br.com.api.footfirma.competicao.dto.DadosDeParticipante;
import br.com.api.footfirma.competicao.dto.DadosDeRegra;
import br.com.api.footfirma.competicao.dto.EdicaoDetalhe;
import br.com.api.footfirma.competicao.dto.FaseResumo;
import br.com.api.footfirma.competicao.dto.JanelaDaEdicao;
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

    /**
     * As fases de uma edição, em ordem, com id e regras de desempate.
     *
     * <p>Devolve vazio quando a edição não existe, em vez de lançar: quem gera calendário
     * trata edição sem fase como erro de calendário, não como recurso ausente.
     */
    List<FaseResumo> listarFasesDaEdicao(long edicaoId);

    /**
     * Os ids dos clubes inscritos na edição a que a fase pertence, em ordem crescente.
     *
     * <p>Participante é da edição, não da fase — a fase herda todos. A ordenação por id
     * não é estética: é o que torna o sorteio do calendário reprodutível.
     *
     * <p>Devolve só os ids, e não um record com estádio: {@code competicao} não conhece
     * {@code clube} e não vai passar a conhecer por causa de um campo. Quem precisa do
     * estádio resolve pela porta de {@code clube}.
     */
    List<Long> listarParticipantesDaFase(long faseId);

    /**
     * A janela em que a edição acontece. {@code EdicaoDetalhe} já traz as duas datas, mas
     * só é alcançável por slug e temporada — e quem gera calendário tem o id.
     */
    Optional<JanelaDaEdicao> buscarJanelaDaEdicao(long edicaoId);
}
