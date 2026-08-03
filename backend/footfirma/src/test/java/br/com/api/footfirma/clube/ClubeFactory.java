package br.com.api.footfirma.clube;

import br.com.api.footfirma.clube.domain.Clube;

final class ClubeFactory {

    private ClubeFactory() {
    }

    static Clube valido(String slug, String nomeCurto, Long paisId) {
        var clube = new Clube(slug, nomeCurto + " Futebol Clube", nomeCurto, paisId);
        clube.setAnoFundacao(1900);
        clube.setReputacao(70);
        clube.setQualidadeBase(60);
        return clube;
    }
}
