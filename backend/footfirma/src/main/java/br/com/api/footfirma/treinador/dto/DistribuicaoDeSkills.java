package br.com.api.footfirma.treinador.dto;

import br.com.api.footfirma.treinador.domain.Skill;

import java.util.Map;

/**
 * Pontos por skill. Serve às duas escritas do módulo: na criação são os 20 pontos
 * iniciais, e cada skill precisa aparecer; na evolução são os pontos ganhos ao fim da
 * temporada, e só as skills que recebem algum entram.
 *
 * <p>O que separa os dois casos é a validação, não o formato — por isso um record só.
 */
public record DistribuicaoDeSkills(Map<Skill, Integer> pontos) {

    public DistribuicaoDeSkills {
        pontos = Map.copyOf(pontos);
    }

    public int total() {
        return pontos.values().stream().mapToInt(Integer::intValue).sum();
    }
}
