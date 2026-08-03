package br.com.api.footfirma.treinador.repository;

import br.com.api.footfirma.treinador.domain.Proposta;
import br.com.api.footfirma.treinador.domain.StatusProposta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface PropostaRepository extends JpaRepository<Proposta, Long> {

    List<Proposta> findByTreinadorIdAndStatus(Long treinadorId, StatusProposta status);

    /**
     * A caixa de entrada real: aberta <b>e</b> dentro do prazo. O filtro por prazo está
     * aqui, e não num estado gravado, porque a varredura que marcaria {@code EXPIRADA}
     * ainda não existe — e sem ele a vencida ficaria visível para sempre.
     */
    List<Proposta> findByTreinadorIdAndStatusAndExpiraEmAfterOrderByCriadaEmDesc(
            Long treinadorId, StatusProposta status, OffsetDateTime agora);

    List<Proposta> findByClubeIdAndStatus(Long clubeId, StatusProposta status);
}
