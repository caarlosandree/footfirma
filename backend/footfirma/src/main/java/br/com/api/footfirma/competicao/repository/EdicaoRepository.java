package br.com.api.footfirma.competicao.repository;

import br.com.api.footfirma.competicao.domain.Edicao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EdicaoRepository extends JpaRepository<Edicao, Long> {

    // Recebe o temporadaId já resolvido, não o label: uma subconsulta sobre a
    // entidade Temporada cruzaria a fronteira do módulo. Quem traduz
    // label -> id é o service, via TemporadaService.
    @Query("""
            select e from Edicao e
            join fetch e.competicao c
            left join fetch e.fases
            where c.slug = :slug and e.temporadaId = :temporadaId
            """)
    Optional<Edicao> buscarPorSlugETemporadaId(@Param("slug") String slug,
                                               @Param("temporadaId") Long temporadaId);
}
