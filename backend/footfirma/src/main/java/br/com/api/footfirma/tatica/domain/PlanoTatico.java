package br.com.api.footfirma.tatica.domain;

import br.com.api.footfirma.tatica.dto.Largura;
import br.com.api.footfirma.tatica.dto.LinhaDefensiva;
import br.com.api.footfirma.tatica.dto.Mentalidade;
import br.com.api.footfirma.tatica.dto.OrigemDoPlano;
import br.com.api.footfirma.tatica.dto.Pressao;
import br.com.api.footfirma.tatica.dto.Ritmo;
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

import java.time.OffsetDateTime;

/**
 * O plano do clube numa temporada, numa versão.
 *
 * <p>{@code clubeId}, {@code temporadaId} e {@code capitaoId} são {@code Long} cru, não
 * {@code @ManyToOne}: entidade JPA de outro módulo não vaza para cá, como
 * {@code VinculoTreinador} já faz.
 */
@Entity
@Table(name = "plano_tatico")
@Getter
@Setter
public class PlanoTatico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clube_id", nullable = false)
    private Long clubeId;

    @Column(name = "temporada_id", nullable = false)
    private Long temporadaId;

    @Column(nullable = false)
    private Integer versao;

    @Column(nullable = false)
    private Boolean vigente;

    @Column(name = "formacao_id", nullable = false)
    private Long formacaoId;

    @Column(name = "capitao_id")
    private Long capitaoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigemDoPlano origem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mentalidade mentalidade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Ritmo ritmo;

    @Enumerated(EnumType.STRING)
    @Column(name = "linha_defensiva", nullable = false)
    private LinhaDefensiva linhaDefensiva;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Pressao pressao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Largura largura;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        return outro instanceof PlanoTatico outra && id != null && id.equals(outra.id);
    }

    @Override
    public int hashCode() {
        return PlanoTatico.class.hashCode();
    }
}
