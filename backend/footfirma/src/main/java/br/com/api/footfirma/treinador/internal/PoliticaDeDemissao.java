package br.com.api.footfirma.treinador.internal;

import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.CARENCIA_PARTIDAS;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.FAIXA_DE_PRESSAO;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.LIMIAR_BASE;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.LIMIAR_INCLINACAO;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.NEUTRO;

final class PoliticaDeDemissao {

    private PoliticaDeDemissao() {
    }

    /**
     * @param reputacaoDoClube vem do evento, nunca do banco — igual ao motor de moral. Um
     *                         limiar lido do estado atual faria o reprocessamento demitir
     *                         (ou salvar) alguém que na campanha original teve o desfecho
     *                         oposto.
     * @param partidasNoVinculo carência: um início ruim de duas rodadas não custa o emprego.
     */
    static Veredito avaliar(double moral, int reputacaoDoClube, int partidasNoVinculo) {
        var limiar = limiar(reputacaoDoClube);

        if (moral >= limiar + FAIXA_DE_PRESSAO) {
            return Veredito.SEGURO;
        }
        if (moral >= limiar || partidasNoVinculo < CARENCIA_PARTIDAS) {
            return Veredito.SOB_PRESSAO;
        }
        return Veredito.DEMITIDO;
    }

    /** Gigante demite antes: reputação 88 exige ~18,8 de moral, clube pequeno se contenta com ~13,5. */
    private static double limiar(int reputacaoDoClube) {
        return LIMIAR_BASE + (reputacaoDoClube - NEUTRO) * LIMIAR_INCLINACAO;
    }
}
