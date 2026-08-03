package br.com.api.footfirma.jogador.domain;

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

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "jogador_vinculo")
@Getter
@Setter
public class JogadorVinculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jogador_id", nullable = false)
    private Jogador jogador;

    @Column(name = "clube_id", nullable = false)
    private Long clubeId;

    @Column(name = "temporada_id", nullable = false)
    private Long temporadaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoVinculo tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoriaDeElenco categoria;

    @Column(name = "numero_camisa")
    private Integer numeroCamisa;

    @Column(name = "data_inicio")
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Column(name = "valor_mercado_eur")
    private BigDecimal valorMercadoEur;

    protected JogadorVinculo() {
    }

    public JogadorVinculo(Jogador jogador, Long clubeId, Long temporadaId, TipoVinculo tipo) {
        this.jogador = jogador;
        this.clubeId = clubeId;
        this.temporadaId = temporadaId;
        this.tipo = tipo;
        this.categoria = CategoriaDeElenco.PROFISSIONAL;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof JogadorVinculo jogadorVinculo)) {
            return false;
        }
        return id != null && id.equals(jogadorVinculo.id);
    }

    @Override
    public int hashCode() {
        return JogadorVinculo.class.hashCode();
    }
}
