package br.com.api.footfirma.importacao.repository;

import br.com.api.footfirma.importacao.domain.ImportacaoExecucao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportacaoExecucaoRepository extends JpaRepository<ImportacaoExecucao, Long> {
}
