package br.com.api.footfirma.avaliacao.repository;

import br.com.api.footfirma.avaliacao.domain.PerfilAvaliacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PerfilAvaliacaoRepository extends JpaRepository<PerfilAvaliacao, Long> {

    // join fetch nos pesos: sem ele, materializar 1.200 jogadores dispararia uma
    // consulta de pesos por perfil por lote.
    @Query("select distinct p from PerfilAvaliacao p join fetch p.pesos where p.ativo = true")
    List<PerfilAvaliacao> buscarAtivosComPesos();
}
