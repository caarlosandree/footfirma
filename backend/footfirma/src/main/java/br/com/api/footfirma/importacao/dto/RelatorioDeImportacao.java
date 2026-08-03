package br.com.api.footfirma.importacao.dto;

import java.util.List;

/**
 * Resultado completo de uma carga. {@code motivoDaFalha} é nulo quando o status é
 * {@code CONCLUIDA} — uma carga pode concluir com ocorrências, e isso não é falha.
 */
public record RelatorioDeImportacao(Long execucaoId, String datasetVersao, String status,
                                    List<ContagemDeEntidade> contagens,
                                    List<OcorrenciaRegistrada> ocorrencias,
                                    int overallsMaterializados,
                                    String motivoDaFalha) {
}
