package br.com.api.footfirma.tatica.repository;

import br.com.api.footfirma.tatica.domain.PlanoTatico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlanoTaticoRepository extends JpaRepository<PlanoTatico, Long> {

    Optional<PlanoTatico> findByClubeIdAndTemporadaIdAndVigenteTrue(Long clubeId, Long temporadaId);

    @Query("""
            select coalesce(max(p.versao), 0) from PlanoTatico p
            where p.clubeId = :clubeId and p.temporadaId = :temporadaId
            """)
    int findMaxVersao(@Param("clubeId") Long clubeId, @Param("temporadaId") Long temporadaId);
}
