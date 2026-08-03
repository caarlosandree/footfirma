package br.com.api.footfirma.treinador.dto;

import br.com.api.footfirma.treinador.domain.StatusProposta;

import java.time.OffsetDateTime;

public record PropostaResumo(Long id, Long clubeId, Long treinadorId, Long temporadaId,
                             Integer metaPosicao, StatusProposta status,
                             OffsetDateTime expiraEm) {
}
