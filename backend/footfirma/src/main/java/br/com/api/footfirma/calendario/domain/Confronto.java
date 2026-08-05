package br.com.api.footfirma.calendario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * O slot do chaveamento: de onde vêm os dois lados e quem avançou.
 *
 * <p>A auto-referência de {@code origemLadoA}/{@code origemLadoB} também é coluna crua.
 * Numa árvore de sete confrontos o lazy loading de {@code @ManyToOne} não se paga, e a
 * propagação busca por id de qualquer forma.
 */
@Entity
@Table(name = "confronto")
@Getter
@Setter
public class Confronto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fase_id", nullable = false)
    private Long faseId;

    @Column(nullable = false)
    private Integer ordem;

    @Column
    private String chave;

    @Column(name = "origem_lado_a")
    private Long origemLadoA;

    @Column(name = "origem_lado_b")
    private Long origemLadoB;

    @Column(name = "clube_a_id")
    private Long clubeAId;

    @Column(name = "clube_b_id")
    private Long clubeBId;

    @Column(name = "vencedor_clube_id")
    private Long vencedorClubeId;

    protected Confronto() {
    }

    public Confronto(Long faseId, Integer ordem, String chave) {
        this.faseId = faseId;
        this.ordem = ordem;
        this.chave = chave;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Confronto confronto)) {
            return false;
        }
        return id != null && id.equals(confronto.id);
    }

    @Override
    public int hashCode() {
        return Confronto.class.hashCode();
    }
}
