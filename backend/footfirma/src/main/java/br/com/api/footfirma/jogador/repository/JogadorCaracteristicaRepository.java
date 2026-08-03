package br.com.api.footfirma.jogador.repository;

import br.com.api.footfirma.jogador.domain.JogadorCaracteristica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JogadorCaracteristicaRepository
        extends JpaRepository<JogadorCaracteristica, JogadorCaracteristica.Chave> {

    Optional<JogadorCaracteristica> findByJogadorIdAndCaracteristicaId(Long jogadorId, Long caracteristicaId);
}
