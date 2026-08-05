package br.com.api.footfirma.calendario.repository;

import br.com.api.footfirma.calendario.domain.Rodada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RodadaRepository extends JpaRepository<Rodada, Long> {

    List<Rodada> findByFaseIdOrderByOrdemAsc(Long faseId);

    List<Rodada> findByFaseIdInOrderByOrdemAsc(List<Long> faseIds);
}
