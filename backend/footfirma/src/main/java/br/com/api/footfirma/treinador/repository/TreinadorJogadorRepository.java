package br.com.api.footfirma.treinador.repository;

import br.com.api.footfirma.treinador.domain.TreinadorJogador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TreinadorJogadorRepository extends JpaRepository<TreinadorJogador, Long> {

    Optional<TreinadorJogador> findByVinculoIdAndJogadorId(Long vinculoId, Long jogadorId);

    List<TreinadorJogador> findByVinculoId(Long vinculoId);
}
