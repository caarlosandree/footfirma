package br.com.api.footfirma.importacao.repository;

import br.com.api.footfirma.importacao.domain.ImportacaoOcorrencia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportacaoOcorrenciaRepository extends JpaRepository<ImportacaoOcorrencia, Long> {
}
