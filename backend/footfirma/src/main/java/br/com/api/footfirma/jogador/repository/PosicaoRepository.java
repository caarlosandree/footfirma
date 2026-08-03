package br.com.api.footfirma.jogador.repository;

import br.com.api.footfirma.jogador.domain.Posicao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PosicaoRepository extends JpaRepository<Posicao, Long> {

    Optional<Posicao> findByCodigo(String codigo);

    List<Posicao> findAllByOrderByOrdem();
}
