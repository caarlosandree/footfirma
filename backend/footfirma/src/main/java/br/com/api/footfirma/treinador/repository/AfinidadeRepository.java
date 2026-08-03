package br.com.api.footfirma.treinador.repository;

import br.com.api.footfirma.treinador.domain.Afinidade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AfinidadeRepository extends JpaRepository<Afinidade, Afinidade.Chave> {

    Optional<Afinidade> findByTreinadorIdAndJogadorId(Long treinadorId, Long jogadorId);
}
