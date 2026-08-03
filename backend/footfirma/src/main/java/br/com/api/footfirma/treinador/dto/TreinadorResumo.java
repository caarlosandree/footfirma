package br.com.api.footfirma.treinador.dto;

import br.com.api.footfirma.treinador.domain.TipoTreinador;

public record TreinadorResumo(Long id, String slug, String nomeExibicao,
                              TipoTreinador tipo, Integer reputacao) {
}
