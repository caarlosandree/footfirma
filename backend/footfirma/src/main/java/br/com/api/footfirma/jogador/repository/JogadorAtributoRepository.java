package br.com.api.footfirma.jogador.repository;

import br.com.api.footfirma.jogador.domain.JogadorAtributo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JogadorAtributoRepository extends JpaRepository<JogadorAtributo, Long> {

    Optional<JogadorAtributo> findByJogadorIdAndTemporadaId(Long jogadorId, Long temporadaId);
}
