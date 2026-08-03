package br.com.api.footfirma.jogador.dto;

/** Entrada de ingestão. Um registro por jogador — a PK da tabela é o próprio jogador_id. */
public record DadosDeAtributosOcultos(Long jogadorId, Integer profissionalismo, Integer ambicao,
                                      Integer lealdade, Integer temperamento, Integer lideranca,
                                      Integer regularidade, Integer propensaoLesao,
                                      Integer resistenciaPressao) {
}
