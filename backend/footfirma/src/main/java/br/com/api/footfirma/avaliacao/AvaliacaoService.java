package br.com.api.footfirma.avaliacao;

import br.com.api.footfirma.avaliacao.dto.ResultadoMaterializacao;

public interface AvaliacaoService {

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
