package br.com.api.footfirma.importacao.repository;

import br.com.api.footfirma.importacao.domain.ImportacaoContagem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportacaoContagemRepository extends JpaRepository<ImportacaoContagem, Long> {
}
