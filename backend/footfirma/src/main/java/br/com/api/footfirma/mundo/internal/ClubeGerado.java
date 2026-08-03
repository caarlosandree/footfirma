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
}
