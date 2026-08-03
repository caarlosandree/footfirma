package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.treinador.domain.Skill;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.PONTOS_INICIAIS;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.SKILL_MAXIMA;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.SKILL_MINIMA;

/**
 * Treinadores de IA para as vagas que o mercado não consegue preencher com quem já
 * existe. Nenhum clube pode ficar vago, ou o mundo trava na primeira rodada.
 *
 * <p>Pura, no formato das fábricas do gerador de mundo: recebe semente e devolve um
 * record. A mesma semente devolve o mesmo treinador.
 *
 * <p>Os nomes são inventados e combinados de duas listas curtas. Coincidir com alguém
 * real é possível e não sugere identificação — nenhum clube deste mundo existe.
 */
final class FabricaDeTreinadorDeIa {

    private static final List<String> PRIMEIROS = List.of(
            "Alceu", "Bento", "Caio", "Dario", "Elias", "Fabio", "Gilson", "Heitor",
            "Ivo", "Jonas", "Lauro", "Murilo", "Nestor", "Otavio", "Rufino");

    private static final List<String> SOBRENOMES = List.of(
            "Andrade", "Barcelos", "Camargo", "Dutra", "Esteves", "Furtado", "Guedes",
            "Lacerda", "Macedo", "Nogueira", "Peixoto", "Queiroz", "Rangel", "Sampaio", "Teixeira");

    private static final int NASCIMENTO_MAIS_VELHO = 1962;
    private static final int JANELA_DE_NASCIMENTO = 24;

    private FabricaDeTreinadorDeIa() {
    }

    static TreinadorDeIa gerar(long semente) {
        var aleatorio = new SplittableRandom(semente);

        var primeiro = PRIMEIROS.get(aleatorio.nextInt(PRIMEIROS.size()));
        var sobrenome = SOBRENOMES.get(aleatorio.nextInt(SOBRENOMES.size()));
        var nascimento = LocalDate.of(
                NASCIMENTO_MAIS_VELHO + aleatorio.nextInt(JANELA_DE_NASCIMENTO),
                1 + aleatorio.nextInt(12), 1 + aleatorio.nextInt(28));

        return new TreinadorDeIa(slug(primeiro, sobrenome, semente),
                primeiro + " " + sobrenome, sobrenome, nascimento, distribuir(aleatorio));
    }

    /** A semente entra no slug para que duas vagas seguidas no mesmo clube não colidam. */
    private static String slug(String primeiro, String sobrenome, long semente) {
        return "ia-" + primeiro.toLowerCase() + "-" + sobrenome.toLowerCase()
                + "-" + Long.toUnsignedString(semente, 36);
    }

    /**
     * Todo mundo parte do piso e o resto é sorteado ponto a ponto, respeitando o teto.
     * Termina sempre: as seis skills comportam 60 pontos e só 20 são distribuídos.
     */
    private static Map<Skill, Integer> distribuir(SplittableRandom aleatorio) {
        var distribuicao = new EnumMap<Skill, Integer>(Skill.class);
        for (var skill : Skill.values()) {
            distribuicao.put(skill, SKILL_MINIMA);
        }

        var restantes = PONTOS_INICIAIS - Skill.values().length * SKILL_MINIMA;
        while (restantes > 0) {
            var sorteada = Skill.values()[aleatorio.nextInt(Skill.values().length)];
            if (distribuicao.get(sorteada) < SKILL_MAXIMA) {
                distribuicao.merge(sorteada, 1, Integer::sum);
                restantes--;
            }
        }
        return distribuicao;
    }

    record TreinadorDeIa(String slug, String nomeCompleto, String nomeExibicao,
                         LocalDate dataNascimento, Map<Skill, Integer> skills) {
    }
}
