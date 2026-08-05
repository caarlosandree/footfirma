package br.com.api.footfirma.tatica;

import br.com.api.footfirma.tatica.domain.PlanoEscalacao;
import br.com.api.footfirma.tatica.domain.PlanoTatico;
import br.com.api.footfirma.tatica.dto.Largura;
import br.com.api.footfirma.tatica.dto.LinhaDefensiva;
import br.com.api.footfirma.tatica.dto.Mentalidade;
import br.com.api.footfirma.tatica.dto.OrigemDoPlano;
import br.com.api.footfirma.tatica.dto.Papel;
import br.com.api.footfirma.tatica.dto.Pressao;
import br.com.api.footfirma.tatica.dto.Ritmo;

final class PlanoFactory {

    private PlanoFactory() {
    }

    static PlanoTatico vigente(CenarioDeElenco cenario, long formacaoId, int versao) {
        var plano = new PlanoTatico();
        plano.setClubeId(cenario.clubeId());
        plano.setTemporadaId(cenario.temporadaId());
        plano.setVersao(versao);
        plano.setVigente(true);
        plano.setFormacaoId(formacaoId);
        plano.setOrigem(OrigemDoPlano.MANUAL);
        plano.setMentalidade(Mentalidade.EQUILIBRADA);
        plano.setRitmo(Ritmo.EQUILIBRADO);
        plano.setLinhaDefensiva(LinhaDefensiva.MEDIA);
        plano.setPressao(Pressao.MEDIA);
        plano.setLargura(Largura.MEDIA);
        return plano;
    }

    static PlanoEscalacao titular(long planoId, long jogadorId, long posicaoId,
                                  int slotOrdem, int aptidao) {
        var linha = base(planoId, jogadorId, posicaoId, aptidao);
        linha.setPapel(Papel.TITULAR);
        linha.setSlotOrdem(slotOrdem);
        return linha;
    }

    static PlanoEscalacao reserva(long planoId, long jogadorId, long posicaoId,
                                  int ordemBanco, int aptidao) {
        var linha = base(planoId, jogadorId, posicaoId, aptidao);
        linha.setPapel(Papel.RESERVA);
        linha.setOrdemBanco(ordemBanco);
        return linha;
    }

    private static PlanoEscalacao base(long planoId, long jogadorId, long posicaoId, int aptidao) {
        var linha = new PlanoEscalacao();
        linha.setPlanoId(planoId);
        linha.setJogadorId(jogadorId);
        linha.setPosicaoId(posicaoId);
        linha.setAptidaoNoMomento(aptidao);
        return linha;
    }
}
