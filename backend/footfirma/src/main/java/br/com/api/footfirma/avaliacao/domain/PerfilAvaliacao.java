package br.com.api.footfirma.avaliacao.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

@Entity
@Table(name = "perfil_avaliacao")
@Getter
@Setter
public class PerfilAvaliacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // posicao pertence ao módulo jogador: referência cruzada é coluna crua, não
    // associação JPA. A integridade fica na chave estrangeira do banco.
    @Column(name = "posicao_id", nullable = false)
    private Long posicaoId;

    @Column(nullable = false)
    private Integer versao;

    @Column(nullable = false)
    private Boolean ativo;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    // Os pesos nunca são escritos pela aplicação: nascem do seed. Como mapa, são
    // exatamente a forma que a calculadora consome.
    @ElementCollection
    @CollectionTable(name = "perfil_avaliacao_peso", joinColumns = @JoinColumn(name = "perfil_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "atributo")
    @Column(name = "peso", nullable = false)
    private Map<AtributoAvaliavel, BigDecimal> pesos = new EnumMap<>(AtributoAvaliavel.class);

    protected PerfilAvaliacao() {
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof PerfilAvaliacao perfil)) {
            return false;
        }
        return id != null && id.equals(perfil.id);
    }

    @Override
    public int hashCode() {
        return PerfilAvaliacao.class.hashCode();
    }
}
