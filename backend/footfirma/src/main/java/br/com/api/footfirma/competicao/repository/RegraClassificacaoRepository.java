package br.com.api.footfirma.competicao.repository;

import br.com.api.footfirma.competicao.domain.RegraClassificacao;
import br.com.api.footfirma.competicao.domain.TipoClassificacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegraClassificacaoRepository extends JpaRepository<RegraClassificacao, Long> {

    List<RegraClassificacao> findByEdicaoIdOrderByPosicaoInicio(Long edicaoId);

    Optional<RegraClassificacao> findByEdicaoIdAndPosicaoInicioAndPosicaoFimAndTipo(
            Long edicaoId, Integer posicaoInicio, Integer posicaoFim, TipoClassificacao tipo);
}
