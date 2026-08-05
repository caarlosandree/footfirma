package br.com.api.footfirma.calendario.domain;

import br.com.api.footfirma.calendario.dto.SituacaoDoJogo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * A partida concreta: quando, onde e com que placar.
 *
 * <p>{@code confronto} e {@code rodada} são {@code @ManyToOne} porque são do mesmo
 * módulo; clube e estádio são colunas cruas porque não são.
 *
 * <p>Mandante, visitante, estádio e data nascem nulos em fase eliminatória — o jogo
 * existe antes de existirem os classificados.
 */
@Entity
@Table(name = "jogo")
@Getter
@Setter
public class Jogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "confronto_id", nullable = false)
    private Confronto confronto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rodada_id", nullable = false)
    private Rodada rodada;

    @Column(name = "ordem_no_confronto", nullable = false)
    private Integer ordemNoConfronto;

    @Column(name = "mandante_id")
    private Long mandanteId;

    @Column(name = "visitante_id")
    private Long visitanteId;

    @Column(name = "estadio_id")
    private Long estadioId;

    @Column(name = "data_jogo")
    private LocalDate dataJogo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SituacaoDoJogo situacao;

    @Column(name = "gols_mandante")
    private Integer golsMandante;

    @Column(name = "gols_visitante")
    private Integer golsVisitante;

    @Column(name = "gols_mandante_prorrogacao")
    private Integer golsMandanteProrrogacao;

    @Column(name = "gols_visitante_prorrogacao")
    private Integer golsVisitanteProrrogacao;

    @Column(name = "penaltis_mandante")
    private Integer penaltisMandante;

    @Column(name = "penaltis_visitante")
    private Integer penaltisVisitante;

    protected Jogo() {
    }

    public Jogo(Confronto confronto, Rodada rodada, Integer ordemNoConfronto) {
        this.confronto = confronto;
        this.rodada = rodada;
        this.ordemNoConfronto = ordemNoConfronto;
        this.situacao = SituacaoDoJogo.AGENDADO;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Jogo jogo)) {
            return false;
        }
        return id != null && id.equals(jogo.id);
    }

    @Override
    public int hashCode() {
        return Jogo.class.hashCode();
    }
}
