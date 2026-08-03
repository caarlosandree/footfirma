package br.com.api.footfirma.avaliacao;

import br.com.api.footfirma.avaliacao.dto.AvaliacoesDoJogador;
import br.com.api.footfirma.avaliacao.dto.ItemDeRanking;
import br.com.api.footfirma.avaliacao.dto.ResultadoMaterializacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface AvaliacaoService {

    Optional<AvaliacoesDoJogador> buscarPorSlug(String slug, String labelTemporada);

    /** {@code overallMinimo} nulo significa sem piso. */
    Page<ItemDeRanking> ranquear(String labelTemporada, String codigoPosicao,
                                 Integer overallMinimo, Pageable pageable);

    /**
     * Recalcula e grava o overall de todos os jogadores com atributos na temporada,
     * em todas as nove posições. Idempotente: duas execuções sobre o mesmo estado
     * produzem resultado idêntico.
     *
     * <p>Não é exposta por REST — seria um POST no catálogo. Quem a chama é o
     * importador do Plano 3.
     */
    ResultadoMaterializacao materializar(String labelTemporada);
}
