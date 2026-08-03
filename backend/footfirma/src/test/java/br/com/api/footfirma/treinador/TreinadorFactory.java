package br.com.api.footfirma.treinador;

import br.com.api.footfirma.treinador.domain.Skill;
import br.com.api.footfirma.treinador.domain.TipoTreinador;
import br.com.api.footfirma.treinador.domain.Treinador;
import br.com.api.footfirma.treinador.domain.VinculoTreinador;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Massa de teste do módulo. Cada método devolve um objeto válido por padrão; o teste
 * sobrescreve apenas o que quer destacar.
 */
public final class TreinadorFactory {

    private static final LocalDate NASCIMENTO = LocalDate.of(1980, 1, 1);
    private static final LocalDate INICIO_DA_TEMPORADA = LocalDate.of(2026, 1, 1);

    private TreinadorFactory() {
    }

    public static Treinador humano(String slug, Long paisId) {
        return new Treinador(slug, "Treinador " + slug, slug, NASCIMENTO,
                paisId, TipoTreinador.HUMANO, (long) slug.hashCode());
    }

    public static Treinador ia(String slug, Long paisId) {
        return new Treinador(slug, "Treinador " + slug, slug, NASCIMENTO,
                paisId, TipoTreinador.IA, (long) slug.hashCode());
    }

    public static Treinador comReputacao(String slug, Long paisId, int reputacao) {
        var treinador = humano(slug, paisId);
        treinador.setReputacao(reputacao);
        return treinador;
    }

    public static VinculoTreinador vinculoAtivo(Treinador treinador, Long clubeId, Long temporadaId) {
        return new VinculoTreinador(treinador, clubeId, temporadaId, INICIO_DA_TEMPORADA, 50.0, 10);
    }

    public static VinculoTreinador vinculoComMoral(Treinador treinador, Long clubeId,
                                            Long temporadaId, double moral, int metaPosicao) {
        return new VinculoTreinador(treinador, clubeId, temporadaId,
                INICIO_DA_TEMPORADA, moral, metaPosicao);
    }

    /** Distribuição válida de 20 pontos: generalista 4·4·3·3·3·3. */
    public static Map<Skill, Integer> distribuicaoValida() {
        var distribuicao = new EnumMap<Skill, Integer>(Skill.class);
        distribuicao.put(Skill.VISAO_DE_JOGO, 4);
        distribuicao.put(Skill.PRELECAO, 4);
        distribuicao.put(Skill.LIDERANCA, 3);
        distribuicao.put(Skill.TREINAMENTO, 3);
        distribuicao.put(Skill.TATICA, 3);
        distribuicao.put(Skill.NEGOCIACAO, 3);
        return distribuicao;
    }

    /** Distribuição válida de 20 pontos: especialista 10·5·2·1·1·1, com o teto no destaque. */
    public static Map<Skill, Integer> especialistaEm(Skill destaque) {
        var distribuicao = new EnumMap<Skill, Integer>(Skill.class);
        for (var skill : Skill.values()) {
            distribuicao.put(skill, 1);
        }
        var apoios = Arrays.stream(Skill.values())
                .filter(skill -> skill != destaque)
                .limit(2)
                .toList();

        distribuicao.put(destaque, 10);
        distribuicao.put(apoios.getFirst(), 5);
        distribuicao.put(apoios.getLast(), 2);
        return distribuicao;
    }
}
