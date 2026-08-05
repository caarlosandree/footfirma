package br.com.api.footfirma.tatica.dto;

import java.util.List;

/**
 * @param banco      ids em ordem de banco — a posição na lista vira {@code ordem_banco},
 *                   então buraco na numeração não tem como existir.
 * @param capitaoId  opcional; quando informado precisa estar entre os titulares.
 */
public record NovoPlano(long formacaoId, Long capitaoId,
                        Mentalidade mentalidade, Ritmo ritmo, LinhaDefensiva linhaDefensiva,
                        Pressao pressao, Largura largura,
                        List<TitularEscalado> titulares, List<Long> banco) {
}
