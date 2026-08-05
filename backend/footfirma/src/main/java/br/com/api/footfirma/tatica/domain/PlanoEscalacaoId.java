package br.com.api.footfirma.tatica.domain;

import java.io.Serializable;

public record PlanoEscalacaoId(Long planoId, Long jogadorId) implements Serializable {
}
