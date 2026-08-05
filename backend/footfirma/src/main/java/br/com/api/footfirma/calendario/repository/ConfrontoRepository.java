package br.com.api.footfirma.calendario.repository;

import br.com.api.footfirma.calendario.domain.Confronto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConfrontoRepository extends JpaRepository<Confronto, Long> {

    List<Confronto> findByFaseIdOrderByOrdemAsc(Long faseId);

    /**
     * Os confrontos que esperam o resultado deste. Os dois índices parciais de origem
     * existem para esta consulta — ela roda a cada confronto resolvido.
     */
    List<Confronto> findByOrigemLadoAOrOrigemLadoB(Long origemLadoA, Long origemLadoB);
}
