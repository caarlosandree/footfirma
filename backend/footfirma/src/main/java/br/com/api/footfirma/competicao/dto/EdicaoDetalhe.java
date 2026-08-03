package br.com.api.footfirma.competicao.dto;

import java.time.LocalDate;
import java.util.List;

public record EdicaoDetalhe(
        Long id,
        String nome,
        String competicao,
        LocalDate dataInicio,
        LocalDate dataFim,
        List<FaseResumo> fases,
        List<RegraClassificacaoResumo> regras
) {
}
