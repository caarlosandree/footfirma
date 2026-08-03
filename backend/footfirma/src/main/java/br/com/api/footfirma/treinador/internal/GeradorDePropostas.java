package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.treinador.domain.Treinador;

import java.util.Comparator;
import java.util.List;

import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.ASSEDIO_DIFERENCA_MINIMA;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.ASSEDIO_MARGEM_DE_REPUTACAO;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.ASSEDIO_MORAL_MINIMA;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.FAIXA_DE_COMPATIBILIDADE;

/**
 * Quem o mercado considera, sem tocar em banco.
 *
 * <p>Duas perguntas distintas: quem serve para um clube vago, e quando um clube maior
 * tenta tirar um treinador que já está empregado.
 */
final class GeradorDePropostas {

    private GeradorDePropostas() {
    }

    /**
     * Os treinadores livres compatíveis com o clube, do mais reputado ao menos.
     *
     * <p>A faixa vale nos dois sentidos: gigante não contrata desconhecido, e clube
     * pequeno não contrata quem só aceitaria por desespero — e largaria o clube na
     * primeira proposta.
     */
    static List<Treinador> candidatos(int reputacaoDoClube, List<Treinador> livres) {
        return livres.stream()
                .filter(treinador -> compativel(treinador.getReputacao(), reputacaoDoClube))
                .sorted(Comparator.comparing(Treinador::getReputacao).reversed()
                        .thenComparing(Treinador::getSlug))
                .toList();
    }

    /**
     * As três condições do assédio, todas obrigatórias: o clube interessado precisa ser
     * um passo acima do atual, o treinador precisa estar indo bem onde está, e precisa
     * ter carreira que justifique o convite.
     *
     * <p>Sem a segunda, o mercado viraria escada de fuga para quem vai mal; sem a
     * terceira, todo gigante assediaria todo mundo toda temporada.
     */
    static boolean deveAssediar(int reputacaoInteressado, int reputacaoAtual,
                                double moral, int reputacaoTreinador) {
        return reputacaoInteressado > reputacaoAtual + ASSEDIO_DIFERENCA_MINIMA
                && moral >= ASSEDIO_MORAL_MINIMA
                && reputacaoTreinador >= reputacaoInteressado - ASSEDIO_MARGEM_DE_REPUTACAO;
    }

    private static boolean compativel(int reputacaoTreinador, int reputacaoDoClube) {
        return Math.abs(reputacaoTreinador - reputacaoDoClube) <= FAIXA_DE_COMPATIBILIDADE;
    }
}
