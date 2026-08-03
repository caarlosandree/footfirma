package br.com.api.footfirma.temporada.repository;

import br.com.api.footfirma.temporada.domain.Temporada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TemporadaRepository extends JpaRepository<Temporada, Long> {

    Optional<Temporada> findByLabel(String label);

    List<Temporada> findAllByOrderByAnoInicioDesc();
}
