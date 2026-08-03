package br.com.api.footfirma.jogador.repository;

import br.com.api.footfirma.jogador.domain.JogadorPosicao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JogadorPosicaoRepository extends JpaRepository<JogadorPosicao, JogadorPosicao.Chave> {

    Optional<JogadorPosicao> findByJogadorIdAndPosicaoId(Long jogadorId, Long posicaoId);
}
