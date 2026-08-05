package br.com.api.footfirma.tatica.repository;

import br.com.api.footfirma.tatica.domain.FormacaoSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormacaoSlotRepository
        extends JpaRepository<FormacaoSlot, FormacaoSlot.Chave> {

    List<FormacaoSlot> findByFormacaoIdOrderByOrdemAsc(Long formacaoId);
}
