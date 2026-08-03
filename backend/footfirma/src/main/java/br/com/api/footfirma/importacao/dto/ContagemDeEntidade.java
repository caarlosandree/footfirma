package br.com.api.footfirma.importacao.dto;

/** Uma linha por arquivo do dataset. {@code recusados} conta ocorrências, não erros de banco. */
public record ContagemDeEntidade(String entidade, int lidos, int criados,
                                 int atualizados, int recusados) {
}
