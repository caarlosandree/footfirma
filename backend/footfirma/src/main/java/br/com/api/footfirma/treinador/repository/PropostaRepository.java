package br.com.api.footfirma.treinador.repository;

import br.com.api.footfirma.treinador.domain.Proposta;
import br.com.api.footfirma.treinador.domain.StatusProposta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PropostaRepository extends JpaRepository<Proposta, Long> {

    List<Proposta> findByTreinadorIdAndStatus(Long treinadorId, StatusProposta status);

    List<Proposta> findByClubeIdAndStatus(Long clubeId, StatusProposta status);
}
