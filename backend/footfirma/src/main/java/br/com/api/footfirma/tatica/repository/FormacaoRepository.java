package br.com.api.footfirma.tatica.repository;

import br.com.api.footfirma.tatica.domain.Formacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormacaoRepository extends JpaRepository<Formacao, Long> {

    List<Formacao> findAllByOrderByOrdemAsc();
}
