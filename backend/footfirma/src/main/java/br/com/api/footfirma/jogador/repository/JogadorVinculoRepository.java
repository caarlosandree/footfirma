package br.com.api.footfirma.jogador.repository;

import br.com.api.footfirma.jogador.domain.JogadorVinculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JogadorVinculoRepository extends JpaRepository<JogadorVinculo, Long> {

    @Query("""
            select v from JogadorVinculo v
            where v.jogador.id in :ids and v.temporadaId = :temporadaId
            """)
    List<JogadorVinculo> buscarPorJogadoresETemporada(@Param("ids") Collection<Long> ids,
                                                      @Param("temporadaId") Long temporadaId);

    @Query("""
            select v from JogadorVinculo v
            join fetch v.jogador j
            join fetch j.posicaoPrincipal
            where v.clubeId = :clubeId and v.temporadaId = :temporadaId
            order by j.nomeExibicao
            """)
    List<JogadorVinculo> buscarElenco(@Param("clubeId") Long clubeId,
                                      @Param("temporadaId") Long temporadaId);

    List<JogadorVinculo> findByJogadorIdOrderByTemporadaIdDesc(Long jogadorId);
}
