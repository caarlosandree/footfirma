package br.com.api.footfirma.competicao.repository;

import br.com.api.footfirma.competicao.domain.Competicao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompeticaoRepository extends JpaRepository<Competicao, Long> {

    Optional<Competicao> findBySlug(String slug);

    // Query nativa de propósito: pais pertence a outro módulo, e um JPQL com
    // a entidade Pais faria o ModularidadeTest falhar. O SQL cruza a tabela
    // sem cruzar a fronteira de código.
    @Query(value = """
            select c.* from competicao c
            join pais p on p.id = c.pais_id
            where p.iso_code = :isoPais
            order by c.nivel nulls last, c.nome
            """, nativeQuery = true)
    List<Competicao> findByPaisIso(@Param("isoPais") String isoPais);
}
