package br.com.api.footfirma.mundo.dto;

/** {@code atualizados} distingue reexecução de carga nova sem consultar o banco. */
public record ContagemPorEntidade(String entidade, int criados, int atualizados) {
}
