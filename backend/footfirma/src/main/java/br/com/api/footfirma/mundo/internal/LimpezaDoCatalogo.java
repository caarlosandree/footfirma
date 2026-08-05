package br.com.api.footfirma.mundo.internal;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Esvazia o catálogo antes de gerar. A ordem é a inversa das dependências —
 * inverter uma linha faz falhar por chave estrangeira.
 *
 * <p>Exceção deliberada à regra de não tocar tabela alheia: apagar o catálogo
 * inteiro é operação de infraestrutura sobre o banco, não escrita de domínio. A
 * alternativa seria expor um método destrutivo na interface pública de cinco
 * módulos — superfície pior que esta lista.
 *
 * <p>País, estado, posição, característica, perfil de avaliação e o catálogo de
 * formações não são apagados: são seed de migration, e o gerador depende deles.
 *
 * <p>{@code plano_escalacao} e {@code plano_tatico} abrem a lista porque referenciam
 * {@code jogador} e {@code clube}. Sem elas aqui, apagar o catálogo falha por chave
 * estrangeira assim que o primeiro plano existe.
 */
final class LimpezaDoCatalogo {

    private static final List<String> TABELAS_EM_ORDEM = List.of(
            "plano_escalacao",
            "plano_tatico",
            "jogador_overall",
            "jogador_vinculo",
            "jogador_atributo",
            "jogador_atributo_oculto",
            "jogador_caracteristica",
            "jogador_posicao",
            "jogador_referencia_externa",
            "jogador",
            "regra_classificacao",
            "edicao_participante",
            "fase",
            "edicao",
            "competicao_referencia_externa",
            "competicao",
            "clube_alias",
            "clube_referencia_externa",
            "clube",
            "estadio");

    private LimpezaDoCatalogo() {
    }

    static void executar(JdbcTemplate jdbcTemplate) {
        TABELAS_EM_ORDEM.forEach(tabela -> jdbcTemplate.update("delete from " + tabela));
    }
}
