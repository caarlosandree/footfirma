package br.com.api.footfirma.temporada;

import br.com.api.footfirma.temporada.dto.TemporadaResumo;

import java.util.List;
import java.util.Optional;

public interface TemporadaService {

    Optional<TemporadaResumo> buscarPorLabel(String label);

    List<TemporadaResumo> listarOrdenadas();
}
