package br.com.api.footfirma.jogador.repository;

import br.com.api.footfirma.jogador.domain.Caracteristica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CaracteristicaRepository extends JpaRepository<Caracteristica, Long> {

    Optional<Caracteristica> findByCodigo(String codigo);
}
