package br.com.api.footfirma.calendario.domain;

import br.com.api.footfirma.calendario.dto.TipoDeRodada;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * A unidade ordinal da fase. {@code faseId} é coluna crua, e não {@code @ManyToOne}:
 * {@code fase} é de outro módulo, e associação JPA só vale dentro do próprio.
 */
@Entity
@Table(name = "rodada")
@Getter
@Setter
public class Rodada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fase_id", nullable = false)
    private Long faseId;

    @Column(nullable = false)
    private Integer ordem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDeRodada tipo;

    @Column(name = "data_alvo", nullable = false)
    private LocalDate dataAlvo;

    @Column(name = "janela_inicio", nullable = false)
    private LocalDate janelaInicio;

    @Column(name = "janela_fim", nullable = false)
    private LocalDate janelaFim;

    protected Rodada() {
    }

    public Rodada(Long faseId, Integer ordem, TipoDeRodada tipo,
                  LocalDate dataAlvo, LocalDate janelaInicio, LocalDate janelaFim) {
        this.faseId = faseId;
        this.ordem = ordem;
        this.tipo = tipo;
        this.dataAlvo = dataAlvo;
        this.janelaInicio = janelaInicio;
        this.janelaFim = janelaFim;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Rodada rodada)) {
            return false;
        }
        return id != null && id.equals(rodada.id);
    }

    @Override
    public int hashCode() {
        return Rodada.class.hashCode();
    }
}
