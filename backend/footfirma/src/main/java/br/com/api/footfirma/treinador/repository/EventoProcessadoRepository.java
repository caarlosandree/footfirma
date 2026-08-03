package br.com.api.footfirma.treinador.repository;

import br.com.api.footfirma.treinador.domain.EventoProcessado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoProcessadoRepository
        extends JpaRepository<EventoProcessado, EventoProcessado.Chave> {

    boolean existsByVinculoIdAndChaveEvento(Long vinculoId, String chaveEvento);

    /** Quantas partidas este vínculo já processou — a carência da política de demissão. */
    long countByVinculoId(Long vinculoId);
}
