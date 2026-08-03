package br.com.api.footfirma.importacao;

import br.com.api.footfirma.importacao.dto.RelatorioDeImportacao;

import java.nio.file.Path;

public interface ImportacaoService {

    /**
     * Valida o dataset, importa na ordem de dependência e materializa o overall de
     * cada temporada carregada. Idempotente: duas execuções sobre o mesmo dataset
     * produzem estado idêntico.
     *
     * <p>Não é exposta por REST — seria escrita no catálogo, que é read-only por
     * decisão registrada em {@code docs/adr/2026-08-01-catalogo-read-only.md}.
     *
     * <p>Não lança em falha de carga: devolve o relatório com status {@code FALHOU}
     * e o motivo. Quem chama é um job, e job traduz resultado em código de saída.
     * A exceção é dataset inválido, que sobe antes de a execução ser aberta.
     */
    RelatorioDeImportacao importar(Path diretorio);
}
