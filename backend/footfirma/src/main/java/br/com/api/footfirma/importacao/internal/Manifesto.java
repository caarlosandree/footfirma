package br.com.api.footfirma.importacao.internal;

import java.util.List;

/** Espelha {@code manifest.json}. Desserializado por Jackson pelo nome dos componentes. */
record Manifesto(String schemaVersao, String datasetVersao, String geradoEm,
                 List<ArquivoDoManifesto> arquivos) {

    static final String SCHEMA_SUPORTADO = "1";

    record ArquivoDoManifesto(String nome, int linhas, String sha256) {
    }
}
