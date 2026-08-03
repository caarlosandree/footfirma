package br.com.api.footfirma.clube.repository;

import br.com.api.footfirma.clube.domain.Estadio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EstadioRepository extends JpaRepository<Estadio, Long> {

    Optional<Estadio> findByNomeAndCidade(String nome, String cidade);
}
