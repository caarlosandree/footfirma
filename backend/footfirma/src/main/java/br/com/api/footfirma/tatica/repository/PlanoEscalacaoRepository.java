package br.com.api.footfirma.tatica.repository;

import br.com.api.footfirma.tatica.domain.PlanoEscalacao;
import br.com.api.footfirma.tatica.domain.PlanoEscalacaoId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanoEscalacaoRepository
        extends JpaRepository<PlanoEscalacao, PlanoEscalacaoId> {

    List<PlanoEscalacao> findByPlanoId(Long planoId);
}
