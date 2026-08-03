package br.com.api.footfirma.shared.dto;

/**
 * Devolvido por todo método {@code sincronizar*} de módulo de catálogo.
 *
 * <p>O {@code id} volta porque o importador liga as entidades seguintes por chave
 * estrangeira, e o dataset só conhece slug. O {@code criado} volta porque o
 * relatório distingue inserção de atualização — sem isso não há como provar que
 * uma segunda importação do mesmo dataset não criou nada.
 */
public record ResultadoDeSincronizacao(Long id, boolean criado) {

    public static ResultadoDeSincronizacao criado(Long id) {
        return new ResultadoDeSincronizacao(id, true);
    }

    public static ResultadoDeSincronizacao atualizado(Long id) {
        return new ResultadoDeSincronizacao(id, false);
    }
}
