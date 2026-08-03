package br.com.api.footfirma.avaliacao.repository;

import br.com.api.footfirma.avaliacao.domain.JogadorOverall;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JogadorOverallRepository extends JpaRepository<JogadorOverall, Long> {

    List<JogadorOverall> findByTemporadaIdAndJogadorIdIn(Long temporadaId, Collection<Long> jogadorIds);

    List<JogadorOverall> findByJogadorIdAndTemporadaIdOrderByOverallDesc(Long jogadorId, Long temporadaId);

    // O desempate por jogadorId é o que torna a paginação determinística com empates.
    @Query("""
            select o from JogadorOverall o
            where o.temporadaId = :temporadaId
              and o.posicaoId = :posicaoId
              and o.overall >= :overallMinimo
            order by o.overall desc, o.jogadorId asc
            """)
    Page<JogadorOverall> ranquear(@Param("temporadaId") Long temporadaId,
                                  @Param("posicaoId") Long posicaoId,
                                  @Param("overallMinimo") Integer overallMinimo,
                                  Pageable pageable);
}
