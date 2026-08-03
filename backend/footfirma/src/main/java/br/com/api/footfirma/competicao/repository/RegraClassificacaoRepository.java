package br.com.api.footfirma.competicao.repository;

import br.com.api.footfirma.competicao.domain.RegraClassificacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegraClassificacaoRepository extends JpaRepository<RegraClassificacao, Long> {

    List<RegraClassificacao> findByEdicaoIdOrderByPosicaoInicio(Long edicaoId);
}
