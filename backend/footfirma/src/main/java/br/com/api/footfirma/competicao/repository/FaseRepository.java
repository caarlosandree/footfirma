package br.com.api.footfirma.competicao.repository;

import br.com.api.footfirma.competicao.domain.Fase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FaseRepository extends JpaRepository<Fase, Long> {

    Optional<Fase> findByEdicaoIdAndOrdem(Long edicaoId, Integer ordem);
}
