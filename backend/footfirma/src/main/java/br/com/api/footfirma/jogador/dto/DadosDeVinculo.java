package br.com.api.footfirma.jogador.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Entrada de ingestão. Espelha uma linha de {@code jogador_vinculo.csv}. */
public record DadosDeVinculo(Long jogadorId, Long clubeId, Long temporadaId, String tipo,
                             Integer numeroCamisa, LocalDate dataInicio, LocalDate dataFim,
                             BigDecimal valorMercadoEur) {
}
