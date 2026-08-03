package br.com.api.footfirma.clube.repository;

import br.com.api.footfirma.clube.domain.ClubeAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClubeAliasRepository extends JpaRepository<ClubeAlias, Long> {

    @Query("select a from ClubeAlias a join fetch a.clube where a.alias = :alias")
    Optional<ClubeAlias> findByAlias(@Param("alias") String alias);
}
