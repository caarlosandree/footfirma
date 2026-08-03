package br.com.api.footfirma.treinador.dto;

import br.com.api.footfirma.treinador.domain.Skill;
import br.com.api.footfirma.treinador.domain.TipoTreinador;

import java.time.LocalDate;
import java.util.Map;

/**
 * @param skills os valores <b>daquela temporada</b>. Não existe "as skills do treinador"
 *               sem dizer de quando: a tabela é versionada justamente para que comparar
 *               dois anos seja um select.
 */
public record TreinadorDetalhe(Long id, String slug, String nomeCompleto, String nomeExibicao,
                               LocalDate dataNascimento, Long paisId, TipoTreinador tipo,
                               Integer reputacao, Integer pontosDisponiveis,
                               Map<Skill, Integer> skills) {
}
