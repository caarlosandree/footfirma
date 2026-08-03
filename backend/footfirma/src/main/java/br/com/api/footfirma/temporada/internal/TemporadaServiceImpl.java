package br.com.api.footfirma.temporada.internal;

import br.com.api.footfirma.temporada.TemporadaService;
import br.com.api.footfirma.temporada.dto.TemporadaResumo;
import br.com.api.footfirma.temporada.mapper.TemporadaMapper;
import br.com.api.footfirma.temporada.repository.TemporadaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class TemporadaServiceImpl implements TemporadaService {

    private final TemporadaRepository temporadaRepository;
    private final TemporadaMapper temporadaMapper;

    @Override
    public Optional<TemporadaResumo> buscarPorLabel(String label) {
        return temporadaRepository.findByLabel(label).map(temporadaMapper::paraResumo);
    }

    @Override
    public List<TemporadaResumo> listarOrdenadas() {
        return temporadaRepository.findAllByOrderByAnoInicioDesc().stream()
                .map(temporadaMapper::paraResumo)
                .toList();
    }
}
