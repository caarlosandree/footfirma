package br.com.api.footfirma.avaliacao;

import br.com.api.footfirma.avaliacao.dto.AvaliacoesDoJogador;
import br.com.api.footfirma.avaliacao.dto.ItemDeRanking;
import br.com.api.footfirma.avaliacao.dto.OverallDeJogador;
import br.com.api.footfirma.avaliacao.dto.ResultadoMaterializacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AvaliacaoService {

    Optional<AvaliacoesDoJogador> buscarPorSlug(String slug, String labelTemporada);

    /**
     * O overall de vários jogadores em todas as posições da temporada.
     *
     * <p>Existe ao lado de {@link #buscarPorSlug} porque montar um onze precisa das nove
     * posições de um elenco inteiro, e não das nove de um jogador.
     *
     * <p>Devolve lista vazia para coleção vazia, sem consultar o banco.
     */
    List<OverallDeJogador> listarOveralls(Collection<Long> jogadorIds, long temporadaId);

    /** {@code overallMinimo} nulo significa sem piso. */
    Page<ItemDeRanking> ranquear(String labelTemporada, String codigoPosicao,
                                 Integer overallMinimo, Pageable pageable);

    /**
     * Recalcula e grava o overall de todos os jogadores com atributos na temporada,
     * em todas as nove posições. Idempotente: duas execuções sobre o mesmo estado
     * produzem resultado idêntico.
     *
     * <p>Não é exposta por REST — seria um POST no catálogo. Quem a chama é o
     * gerador de mundo, no último passo da geração.
     */
    ResultadoMaterializacao materializar(String labelTemporada);
}
