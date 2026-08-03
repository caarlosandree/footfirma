package br.com.api.footfirma.avaliacao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "jogador_overall")
@Getter
@Setter
public class JogadorOverall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // As três referências apontam para fora do módulo avaliacao: coluna crua, sem
    // associação JPA, como Jogador.paisId e JogadorAtributo.temporadaId já fazem.
    // A integridade fica nas chaves estrangeiras declaradas na migration V12.
    @Column(name = "jogador_id", nullable = false)
    private Long jogadorId;

    @Column(name = "temporada_id", nullable = false)
    private Long temporadaId;

    @Column(name = "posicao_id", nullable = false)
    private Long posicaoId;

    @Column(name = "perfil_versao", nullable = false)
    private Integer perfilVersao;

    @Column(nullable = false)
    private Integer overall;

    @Column(name = "calculado_em", nullable = false)
    private OffsetDateTime calculadoEm;

    protected JogadorOverall() {
    }

    public JogadorOverall(Long jogadorId, Long temporadaId, Long posicaoId) {
        this.jogadorId = jogadorId;
        this.temporadaId = temporadaId;
        this.posicaoId = posicaoId;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof JogadorOverall registro)) {
            return false;
        }
        return id != null && id.equals(registro.id);
    }

    @Override
    public int hashCode() {
        return JogadorOverall.class.hashCode();
    }
}
