package br.com.api.footfirma.treinador.repository;

import br.com.api.footfirma.treinador.domain.Skill;
import br.com.api.footfirma.treinador.domain.TreinadorSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TreinadorSkillRepository
        extends JpaRepository<TreinadorSkill, TreinadorSkill.Chave> {

    List<TreinadorSkill> findByTreinadorIdAndTemporadaId(Long treinadorId, Long temporadaId);

    Optional<TreinadorSkill> findByTreinadorIdAndTemporadaIdAndSkill(
            Long treinadorId, Long temporadaId, Skill skill);
}
