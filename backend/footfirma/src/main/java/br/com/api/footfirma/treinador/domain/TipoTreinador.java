package br.com.api.footfirma.treinador.domain;

/**
 * Quem está no comando. Não há coluna apontando para conta de usuário: o elo entre
 * pessoa e treinador é do spec de autenticação.
 */
public enum TipoTreinador {
    HUMANO,
    IA
}
