package br.com.api.footfirma.tatica.internal;

import br.com.api.footfirma.tatica.dto.Largura;
import br.com.api.footfirma.tatica.dto.LinhaDefensiva;
import br.com.api.footfirma.tatica.dto.Mentalidade;
import br.com.api.footfirma.tatica.dto.Pressao;
import br.com.api.footfirma.tatica.dto.Ritmo;

import java.util.Comparator;
import java.util.Map;

/**
 * As cinco instruções coletivas do treinador de IA, e a braçadeira.
 *
 * <p>A mentalidade sai da reputação do clube comparada à média global — não à da
 * divisão, que exigiria depender de {@code competicao} e seria ambígua para quem disputa
 * mais de uma competição. Os outros quatro eixos saem da tabela de coerência: cinco
 * eixos sorteados de forma independente produziriam um time ofensivo com linha recuada,
 * e é essa tabela que impede a IA de se contradizer.
 */
final class EscolhedorDeInstrucoes {

    private EscolhedorDeInstrucoes() {
    }

    record Instrucoes(Mentalidade mentalidade, Ritmo ritmo, LinhaDefensiva linhaDefensiva,
                      Pressao pressao, Largura largura) {
    }

    private static final Map<Mentalidade, Instrucoes> COERENCIA = Map.of(
            Mentalidade.MUITO_DEFENSIVA, new Instrucoes(Mentalidade.MUITO_DEFENSIVA,
                    Ritmo.LENTO, LinhaDefensiva.RECUADA, Pressao.BAIXA, Largura.ESTREITA),
            Mentalidade.DEFENSIVA, new Instrucoes(Mentalidade.DEFENSIVA,
                    Ritmo.LENTO, LinhaDefensiva.RECUADA, Pressao.MEDIA, Largura.MEDIA),
            Mentalidade.EQUILIBRADA, new Instrucoes(Mentalidade.EQUILIBRADA,
                    Ritmo.EQUILIBRADO, LinhaDefensiva.MEDIA, Pressao.MEDIA, Largura.MEDIA),
            Mentalidade.OFENSIVA, new Instrucoes(Mentalidade.OFENSIVA,
                    Ritmo.INTENSO, LinhaDefensiva.MEDIA, Pressao.ALTA, Largura.ABERTA),
            Mentalidade.MUITO_OFENSIVA, new Instrucoes(Mentalidade.MUITO_OFENSIVA,
                    Ritmo.INTENSO, LinhaDefensiva.ADIANTADA, Pressao.ALTA, Largura.ABERTA));

    static Mentalidade mentalidadeDe(int reputacaoDoClube, double mediaDeReputacao) {
        var distancia = reputacaoDoClube - mediaDeReputacao;
        var faixa = ConstantesDeTatica.FAIXA_DE_MENTALIDADE;

        if (distancia <= -2.0 * faixa) {
            return Mentalidade.MUITO_DEFENSIVA;
        }
        if (distancia < 0) {
            return Mentalidade.DEFENSIVA;
        }
        if (distancia == 0) {
            return Mentalidade.EQUILIBRADA;
        }
        if (distancia < 2.0 * faixa) {
            return Mentalidade.OFENSIVA;
        }
        return Mentalidade.MUITO_OFENSIVA;
    }

    static Instrucoes instrucoesDe(Mentalidade mentalidade) {
        return COERENCIA.get(mentalidade);
    }

    /**
     * A braçadeira vai para o titular de maior aptidão; empate resolve pelo menor id.
     *
     * <p>Poderia sair de LIDERANCA ou da afinidade com o treinador, mas ambas seriam
     * calibragem contra um motor que não existe — e a braçadeira, hoje, não modifica
     * nada.
     */
    static long capitaoDe(Onze onze) {
        return onze.slots().stream()
                .max(Comparator.comparingInt(SlotPreenchido::aptidao)
                        .thenComparing(Comparator.comparingLong(SlotPreenchido::jogadorId).reversed()))
                .orElseThrow()
                .jogadorId();
    }
}
