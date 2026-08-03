package br.com.api.footfirma.geografia;

import br.com.api.footfirma.geografia.dto.EstadoResumo;
import br.com.api.footfirma.geografia.dto.PaisResumo;

import java.util.List;
import java.util.Optional;

public interface GeografiaService {

    Optional<PaisResumo> buscarPaisPorIso(String isoCode);

    List<EstadoResumo> listarEstadosDoPais(String isoCode);
}
