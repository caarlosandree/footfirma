package br.com.api.footfirma.geografia.internal;

import br.com.api.footfirma.geografia.GeografiaService;
import br.com.api.footfirma.geografia.dto.EstadoResumo;
import br.com.api.footfirma.geografia.dto.PaisResumo;
import br.com.api.footfirma.geografia.mapper.GeografiaMapper;
import br.com.api.footfirma.geografia.repository.EstadoRepository;
import br.com.api.footfirma.geografia.repository.PaisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class GeografiaServiceImpl implements GeografiaService {

    private final PaisRepository paisRepository;
    private final EstadoRepository estadoRepository;
    private final GeografiaMapper geografiaMapper;

    @Override
    public Optional<PaisResumo> buscarPaisPorIso(String isoCode) {
        return paisRepository.findByIsoCode(isoCode).map(geografiaMapper::paraResumo);
    }

    @Override
    public List<EstadoResumo> listarEstadosDoPais(String isoCode) {
        return estadoRepository.findByPaisIsoCode(isoCode).stream()
                .map(geografiaMapper::paraResumo)
                .toList();
    }
}
