package br.com.api.footfirma.treinador.repository;

import br.com.api.footfirma.treinador.domain.VinculoTreinador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VinculoTreinadorRepository extends JpaRepository<VinculoTreinador, Long> {

    Optional<VinculoTreinador> findByTreinadorIdAndFimIsNull(Long treinadorId);

    Optional<VinculoTreinador> findByClubeIdAndFimIsNull(Long clubeId);

    List<VinculoTreinador> findByTemporadaIdAndFimIsNull(Long temporadaId);

    List<VinculoTreinador> findByTemporadaId(Long temporadaId);
}
