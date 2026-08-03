package br.com.api.footfirma.temporada;

import br.com.api.footfirma.shared.dto.ResultadoDeSincronizacao;
import br.com.api.footfirma.temporada.dto.DadosDeTemporada;
import br.com.api.footfirma.temporada.dto.TemporadaResumo;

import java.util.List;
import java.util.Optional;

public interface TemporadaService {

    Optional<TemporadaResumo> buscarPorLabel(String label);

    List<TemporadaResumo> listarOrdenadas();

    /**
     * Upsert por {@code label}. Chamado apenas pelo importador — a API REST do
     * catálogo é read-only por decisão registrada em
     * {@code docs/adr/2026-08-01-catalogo-read-only.md}.
     */
    ResultadoDeSincronizacao sincronizar(DadosDeTemporada dados);
}
