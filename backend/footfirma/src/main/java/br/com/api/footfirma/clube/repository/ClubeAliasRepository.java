package br.com.api.footfirma.clube.repository;

import br.com.api.footfirma.clube.domain.ClubeAlias;
import br.com.api.footfirma.clube.domain.FonteExterna;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClubeAliasRepository extends JpaRepository<ClubeAlias, Long> {

    @Query("select a from ClubeAlias a join fetch a.clube where a.alias = :alias")
    Optional<ClubeAlias> findByAlias(@Param("alias") String alias);

    Optional<ClubeAlias> findByAliasAndFonte(String alias, FonteExterna fonte);
}
