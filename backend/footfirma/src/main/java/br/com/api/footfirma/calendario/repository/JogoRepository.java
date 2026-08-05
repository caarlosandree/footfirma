package br.com.api.footfirma.calendario.repository;

import br.com.api.footfirma.calendario.domain.Jogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface JogoRepository extends JpaRepository<Jogo, Long> {

    List<Jogo> findByConfrontoIdOrderByOrdemNoConfrontoAsc(Long confrontoId);

    List<Jogo> findByRodadaIdOrderByDataJogoAsc(Long rodadaId);

    /**
     * As datas já ocupadas por um clube nas fases dadas. É o que o alocador consulta para
     * verificar descanso — e a base de {@code listarAgendaDoClube}.
     *
     * <p>Recebe as fases, e não a temporada: {@code jogo} não conhece temporada, e
     * atravessar até {@code edicao} seria consultar tabela de outro módulo por JPQL.
     */
    @Query("""
            select j.dataJogo from Jogo j
            where (j.mandanteId = :clubeId or j.visitanteId = :clubeId)
              and j.dataJogo is not null
              and j.rodada.faseId in :faseIds
            """)
    List<LocalDate> findDatasDoClube(@Param("clubeId") Long clubeId,
                                     @Param("faseIds") List<Long> faseIds);

    @Query("""
            select j from Jogo j
            where (j.mandanteId = :clubeId or j.visitanteId = :clubeId)
              and j.rodada.faseId in :faseIds
            order by j.dataJogo asc nulls last
            """)
    List<Jogo> findAgendaDoClube(@Param("clubeId") Long clubeId,
                                 @Param("faseIds") List<Long> faseIds);
}
