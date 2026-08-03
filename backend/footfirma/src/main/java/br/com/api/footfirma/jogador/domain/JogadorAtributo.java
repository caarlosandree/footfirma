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

import java.time.OffsetDateTime;

@Entity
@Table(name = "jogador_atributo")
@Getter
@Setter
public class JogadorAtributo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jogador_id", nullable = false)
    private Jogador jogador;

    @Column(name = "temporada_id", nullable = false)
    private Long temporadaId;

    @Column(nullable = false)
    private Integer ritmo;

    @Column(nullable = false)
    private Integer forca;

    @Column(nullable = false)
    private Integer folego;

    @Column(nullable = false)
    private Integer salto;

    @Column(nullable = false)
    private Integer agilidade;

    @Column(nullable = false)
    private Integer passe;

    @Column(nullable = false)
    private Integer drible;

    @Column(nullable = false)
    private Integer cruzamento;

    @Column(nullable = false)
    private Integer frieza;

    @Column(nullable = false)
    private Integer finalizacao;

    @Column(nullable = false)
    private Integer cabeceio;

    @Column(nullable = false)
    private Integer falta;

    @Column(nullable = false)
    private Integer penalti;

    @Column(nullable = false)
    private Integer desarme;

    @Column(nullable = false)
    private Integer marcacao;

    @Column(name = "gol_reflexo", nullable = false)
    private Integer golReflexo;

    @Column(name = "gol_posicionamento", nullable = false)
    private Integer golPosicionamento;

    @Column(name = "gol_manejo", nullable = false)
    private Integer golManejo;

    @Column(name = "potencial_base", nullable = false)
    private Integer potencialBase;

    @Column(name = "potencial_variacao", nullable = false)
    private Integer potencialVariacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "fonte_atributo", nullable = false)
    private FonteAtributo fonteAtributo;

    @Column(name = "coletado_em", nullable = false)
    private OffsetDateTime coletadoEm;

    protected JogadorAtributo() {
    }

    public JogadorAtributo(Jogador jogador, Long temporadaId, FonteAtributo fonteAtributo) {
        this.jogador = jogador;
        this.temporadaId = temporadaId;
        this.fonteAtributo = fonteAtributo;
        this.potencialVariacao = 0;
        this.coletadoEm = OffsetDateTime.now();
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof JogadorAtributo jogadorAtributo)) {
            return false;
        }
        return id != null && id.equals(jogadorAtributo.id);
    }

    @Override
    public int hashCode() {
        return JogadorAtributo.class.hashCode();
    }
}
