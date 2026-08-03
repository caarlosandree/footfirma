package br.com.api.footfirma.mundo.dto;

import java.util.List;

/**
 * A semente volta no relatório porque é o único jeito de reproduzir um mundo:
 * sem ela, um bug observado em execução passada é irrecuperável.
 */
public record RelatorioDeMundo(long semente, String temporada, List<ContagemPorEntidade> contagens) {
}
