package br.com.api.footfirma.mundo.internal;

/** Um clube antes de virar linha. O gerador o traduz em {@code DadosDeClube}. */
record ClubeGerado(String slug, String nomeOficial, String nomeCurto, String apelido,
                   int anoFundacao, String cidade, String uf,
                   String estadio, int capacidade, int anoInauguracao,
                   String corPrimaria, String corSecundaria,
                   int reputacao, int forcaFinanceira, int qualidadeBase, int divisao) {

    /**
     * Reputação pesa mais que dinheiro porque história atrai jogador que salário não
     * paga — mas dinheiro pesa o bastante para um gigante endividado decair.
     */
    int nivelElenco() {
        return (int) Math.round(0.6 * reputacao + 0.4 * forcaFinanceira);
    }

    /**
     * Índice de clube não é overall de jogador. Um clube de reputação 92 não tem
     * elenco de overall 92: tem um elenco médio na casa dos 70 e uma estrela nos 90.
     *
     * <p>Sem esta compressão o topo encosta no teto de 95 e o elenco inteiro se
     * achata contra ele — a estrela deixa de ser distinguível dos titulares, que é
     * exatamente a propriedade que o balanceamento existe para garantir.
     */
    int alvoDoElenco() {
        return comprimir(nivelElenco());
    }

    int alvoDaBase() {
        return comprimir(qualidadeBase);
    }

    private static int comprimir(int indice) {
        return (int) Math.round(38 + 0.45 * indice);
    }
}
