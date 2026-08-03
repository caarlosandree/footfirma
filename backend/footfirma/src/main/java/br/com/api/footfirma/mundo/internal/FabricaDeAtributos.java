package br.com.api.footfirma.mundo.internal;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Gera as 18 skills a partir de um alvo de overall. O overall não é escrito: o
 * módulo {@code avaliacao} o deriva por média ponderada, e como os pesos de cada
 * perfil somam 1,0, gerar em torno do alvo as skills que têm peso faz o overall cair
 * perto dele sem inverter fórmula nenhuma.
 *
 * <p>O conjunto de skills relevantes por posição duplica o que a V13 declara. É
 * duplicação consciente: a alternativa seria expor os pesos na interface pública de
 * {@code avaliacao} e acoplar a geração à versão do perfil de avaliação.
 */
final class FabricaDeAtributos {

    private static final Set<String> SKILLS_DE_GOLEIRO =
            Set.of("GOL_REFLEXO", "GOL_POSICIONAMENTO", "GOL_MANEJO");

    private static final Map<String, Set<String>> RELEVANTES_POR_POSICAO = Map.of(
            "GOL", Set.of("SALTO", "AGILIDADE", "PASSE", "FRIEZA",
                    "GOL_REFLEXO", "GOL_POSICIONAMENTO", "GOL_MANEJO"),
            "ZAG", Set.of("RITMO", "FORCA", "FOLEGO", "SALTO", "AGILIDADE", "PASSE",
                    "FRIEZA", "CABECEIO", "DESARME", "MARCACAO"),
            "LTD", Set.of("RITMO", "FORCA", "FOLEGO", "AGILIDADE", "PASSE", "DRIBLE",
                    "CRUZAMENTO", "FRIEZA", "DESARME", "MARCACAO"),
            "LTE", Set.of("RITMO", "FORCA", "FOLEGO", "AGILIDADE", "PASSE", "DRIBLE",
                    "CRUZAMENTO", "FRIEZA", "DESARME", "MARCACAO"),
            "VOL", Set.of("RITMO", "FORCA", "FOLEGO", "AGILIDADE", "PASSE", "DRIBLE",
                    "FRIEZA", "CABECEIO", "DESARME", "MARCACAO"),
            "MEC", Set.of("RITMO", "FORCA", "FOLEGO", "AGILIDADE", "PASSE", "DRIBLE",
                    "CRUZAMENTO", "FRIEZA", "FINALIZACAO", "DESARME"),
            "MEA", Set.of("RITMO", "FORCA", "FOLEGO", "AGILIDADE", "PASSE", "DRIBLE",
                    "CRUZAMENTO", "FRIEZA", "FINALIZACAO"),
            "PTA", Set.of("RITMO", "FORCA", "FOLEGO", "AGILIDADE", "PASSE", "DRIBLE",
                    "CRUZAMENTO", "FRIEZA", "FINALIZACAO"),
            "ATA", Set.of("RITMO", "FORCA", "FOLEGO", "SALTO", "AGILIDADE", "PASSE",
                    "DRIBLE", "FRIEZA", "FINALIZACAO", "CABECEIO"));

    private static final List<String> ORDEM = List.of(
            "RITMO", "FORCA", "FOLEGO", "SALTO", "AGILIDADE", "PASSE", "DRIBLE",
            "CRUZAMENTO", "FRIEZA", "FINALIZACAO", "CABECEIO", "FALTA", "PENALTI",
            "DESARME", "MARCACAO", "GOL_REFLEXO", "GOL_POSICIONAMENTO", "GOL_MANEJO");

    private FabricaDeAtributos() {
    }

    static AtributosGerados gerar(String posicao, int alvoOverall, SplittableRandom aleatorio) {
        var relevantes = RELEVANTES_POR_POSICAO.get(posicao);
        var goleiro = "GOL".equals(posicao);
        var valores = ORDEM.stream()
                .map(skill -> valorDe(skill, relevantes, goleiro, alvoOverall, aleatorio))
                .toList();
        return new AtributosGerados(
                valores.get(0), valores.get(1), valores.get(2), valores.get(3), valores.get(4),
                valores.get(5), valores.get(6), valores.get(7), valores.get(8), valores.get(9),
                valores.get(10), valores.get(11), valores.get(12), valores.get(13),
                valores.get(14), valores.get(15), valores.get(16), valores.get(17));
    }

    private static int valorDe(String skill, Set<String> relevantes, boolean goleiro,
                               int alvo, SplittableRandom aleatorio) {
        if (relevantes.contains(skill)) {
            return limitar(alvo + aleatorio.nextInt(-3, 4));
        }
        // Skill fora do perfil não entra no overall, mas aparece na ficha: um
        // goleiro com finalização 70 seria absurdo visível.
        var forasteira = goleiro != SKILLS_DE_GOLEIRO.contains(skill);
        return forasteira
                ? limitar(aleatorio.nextInt(8, 26))
                : limitar(aleatorio.nextInt(30, 61));
    }

    private static int limitar(int valor) {
        return Math.clamp(valor, 1, 99);
    }
}
