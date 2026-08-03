package br.com.api.footfirma.clube.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "clube")
@Getter
@Setter
public class Clube {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "nome_oficial", nullable = false)
    private String nomeOficial;

    @Column(name = "nome_curto", nullable = false)
    private String nomeCurto;

    private String apelido;

    @Column(name = "ano_fundacao")
    private Integer anoFundacao;

    @Column(name = "pais_id", nullable = false)
    private Long paisId;

    @Column(name = "estado_id")
    private Long estadoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estadio_id")
    private Estadio estadio;

    @Column(name = "cor_primaria")
    private String corPrimaria;

    @Column(name = "cor_secundaria")
    private String corSecundaria;

    @Column(nullable = false)
    private Integer reputacao;

    @Column(name = "qualidade_base", nullable = false)
    private Integer qualidadeBase;

    @Column(name = "estado_base_id")
    private Long estadoBaseId;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    protected Clube() {
    }

    public Clube(String slug, String nomeOficial, String nomeCurto, Long paisId) {
        this.slug = slug;
        this.nomeOficial = nomeOficial;
        this.nomeCurto = nomeCurto;
        this.paisId = paisId;
        this.reputacao = 50;
        this.qualidadeBase = 50;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Clube clube)) {
            return false;
        }
        return id != null && id.equals(clube.id);
    }

    @Override
    public int hashCode() {
        return Clube.class.hashCode();
    }
}
