package br.com.api.footfirma.geografia.repository;

import br.com.api.footfirma.geografia.domain.Pais;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaisRepository extends JpaRepository<Pais, Long> {

    Optional<Pais> findByIsoCode(String isoCode);
}
