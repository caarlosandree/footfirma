package br.com.api.footfirma.calendario.dto;

/**
 * Uma edição a gerar, com sua precedência na temporada.
 *
 * <p>A precedência existe porque a ordem de geração decide quem fica com os melhores dias:
 * quem gera primeiro ocupa, quem vem depois se acomoda. Como argumento ordenado pelo
 * próprio módulo, a dependência deixa de ser a sequência das linhas de quem chama.
 *
 * @param precedencia menor gera primeiro. Divisão mais alta antes da mais baixa; liga
 *                    antes de copa.
 */
public record EdicaoParaGerar(long edicaoId, int precedencia, PerfilDeCalendario perfil) {
}
