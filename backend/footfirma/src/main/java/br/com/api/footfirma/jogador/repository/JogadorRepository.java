package br.com.api.footfirma.jogador.repository;

import br.com.api.footfirma.jogador.domain.Jogador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JogadorRepository extends JpaRepository<Jogador, Long> {

    @Query("select j from Jogador j join fetch j.posicaoPrincipal where j.slug = :slug")
    Optional<Jogador> findBySlug(@Param("slug") String slug);

    @Query("select j from Jogador j join fetch j.posicaoPrincipal where j.id in :ids")
    List<Jogador> buscarPorIds(@Param("ids") Collection<Long> ids);

    Optional<Jogador> findByChaveNatural(String chaveNatural);
}
