package br.com.api.footfirma.clube.repository;

import br.com.api.footfirma.clube.domain.Clube;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClubeRepository extends JpaRepository<Clube, Long> {

    @Query("select c from Clube c left join fetch c.estadio where c.slug = :slug")
    Optional<Clube> findBySlug(@Param("slug") String slug);

    Page<Clube> findAllByOrderByNomeCurto(Pageable paginacao);
}
