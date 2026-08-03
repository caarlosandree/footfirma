package br.com.api.footfirma.geografia.repository;

import br.com.api.footfirma.geografia.domain.Estado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EstadoRepository extends JpaRepository<Estado, Long> {

    @Query("select e from Estado e join fetch e.pais p where p.isoCode = :isoCode order by e.uf")
    List<Estado> findByPaisIsoCode(@Param("isoCode") String isoCode);

    @Query("select e from Estado e join fetch e.pais p where p.isoCode = :isoCode and e.uf = :uf")
    Optional<Estado> buscarPorIsoEUf(@Param("isoCode") String isoCode, @Param("uf") String uf);
}
