package br.com.api.footfirma.treinador.repository;

import br.com.api.footfirma.treinador.domain.Treinador;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TreinadorRepository extends JpaRepository<Treinador, Long> {

    Optional<Treinador> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Treinador> findAllByOrderByReputacaoDesc(Pageable paginacao);

    /** Quem está no mercado: sem vínculo ativo. Alimenta o gerador de propostas. */
    @Query("""
            select t from Treinador t
            where not exists (
                select 1 from VinculoTreinador v
                where v.treinador = t and v.fim is null
            )
            """)
    List<Treinador> buscarLivres();
}
