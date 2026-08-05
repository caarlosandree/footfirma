package br.com.api.footfirma.competicao.repository;

import br.com.api.footfirma.competicao.domain.EdicaoParticipante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EdicaoParticipanteRepository extends JpaRepository<EdicaoParticipante, Long> {

    Optional<EdicaoParticipante> findByEdicaoIdAndClubeId(Long edicaoId, Long clubeId);

    /** A ordem por clube é o que torna o sorteio do calendário reprodutível. */
    List<EdicaoParticipante> findByEdicaoIdOrderByClubeIdAsc(Long edicaoId);
}
