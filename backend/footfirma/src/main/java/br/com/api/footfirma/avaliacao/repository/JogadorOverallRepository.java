package br.com.api.footfirma.avaliacao.repository;

import br.com.api.footfirma.avaliacao.domain.JogadorOverall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface JogadorOverallRepository extends JpaRepository<JogadorOverall, Long> {

    List<JogadorOverall> findByTemporadaIdAndJogadorIdIn(Long temporadaId, Collection<Long> jogadorIds);
}
