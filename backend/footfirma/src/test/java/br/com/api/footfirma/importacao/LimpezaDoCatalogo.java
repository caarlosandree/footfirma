package br.com.api.footfirma.importacao;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Esvazia o catálogo antes de cada carga. A ordem é a inversa das dependências —
 * inverter uma linha faz o teste falhar por FK, não por defeito do importador.
 *
 * <p>Seeds de país, estado, posição, característica e perfil de avaliação não são
 * apagados: são catálogo versionado, e o importador depende deles.
 */
final class LimpezaDoCatalogo {

    private static final List<String> TABELAS_EM_ORDEM = List.of(
            "jogador_overall",
            "jogador_vinculo",
            "jogador_atributo",
            "jogador_atributo_oculto",
            "jogador_caracteristica",
            "jogador_posicao",
            "jogador",
            "regra_classificacao",
            "edicao_participante",
            "fase",
            "edicao",
            "competicao",
            "clube_alias",
            "clube",
            "estadio",
            "importacao_ocorrencia",
            "importacao_contagem",
            "importacao_execucao");

    private LimpezaDoCatalogo() {
    }

    static void executar(JdbcTemplate jdbcTemplate) {
        TABELAS_EM_ORDEM.forEach(tabela -> jdbcTemplate.update("delete from " + tabela));
    }
}
