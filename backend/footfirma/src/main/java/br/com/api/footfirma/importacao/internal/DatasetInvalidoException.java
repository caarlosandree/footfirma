package br.com.api.footfirma.importacao.internal;

/**
 * Defeito estrutural do dataset: manifesto inconsistente, checksum divergente,
 * cabeçalho errado, coluna faltando. Sempre aborta antes de qualquer escrita —
 * é diferente de ocorrência, que é linha recusada com a carga prosseguindo.
 */
class DatasetInvalidoException extends RuntimeException {

    DatasetInvalidoException(String mensagem) {
        super(mensagem);
    }

    DatasetInvalidoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
