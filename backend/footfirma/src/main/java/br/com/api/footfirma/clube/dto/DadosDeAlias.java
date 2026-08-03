package br.com.api.footfirma.clube.dto;

/**
 * Entrada de ingestão. {@code fonte} é String e não o enum FonteExterna: o enum
 * vive em clube/domain, que é interno, e tipo de domínio não cruza fronteira de módulo.
 */
public record DadosDeAlias(Long clubeId, String alias, String fonte) {
}
