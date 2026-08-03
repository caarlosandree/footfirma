package br.com.api.footfirma.jogador;

import br.com.api.footfirma.jogador.domain.Jogador;
import br.com.api.footfirma.jogador.domain.PeParaChute;
import br.com.api.footfirma.jogador.domain.Posicao;

import java.time.LocalDate;

final class JogadorFactory {

    private JogadorFactory() {
    }

    static Jogador valido(String slug, String nome, LocalDate nascimento, Long paisId, Posicao posicao) {
        var jogador = new Jogador(
                slug,
                nome.toLowerCase().replace(" ", "-") + "|" + nascimento + "|BRA",
                nome,
                nome,
                nascimento,
                paisId,
                posicao,
                PeParaChute.DIREITO,
                nome.hashCode());
        jogador.setAlturaCm(180);
        jogador.setPesoKg(75);
        return jogador;
    }
}
