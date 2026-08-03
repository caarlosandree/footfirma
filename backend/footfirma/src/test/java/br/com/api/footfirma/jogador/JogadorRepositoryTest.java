package br.com.api.footfirma.jogador;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.jogador.domain.Posicao;
import br.com.api.footfirma.jogador.domain.Setor;
import br.com.api.footfirma.jogador.repository.JogadorRepository;
import br.com.api.footfirma.jogador.repository.PosicaoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JogadorRepositoryTest {

    @Autowired
    JogadorRepository jogadorRepository;

    @Autowired
    PosicaoRepository posicaoRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveCarregarAsNovePosicoesDaMigrationDeSeed() {
        var posicoes = posicaoRepository.findAllByOrderByOrdem();

        assertThat(posicoes).hasSize(9);
        assertThat(posicoes.getFirst().getCodigo()).isEqualTo("GOL");
        assertThat(posicoes.getFirst().getSetor()).isEqualTo(Setor.GOLEIRO);
        assertThat(posicoes.getLast().getCodigo()).isEqualTo("ATA");
    }

    @Test
    void deveEncontrarJogadorPeloSlug() {
        jogadorRepository.save(JogadorFactory.valido(
                "raphael-veiga", "Raphael Veiga", LocalDate.of(1995, 6, 19), idDoBrasil(), atacante()));

        var encontrado = jogadorRepository.findBySlug("raphael-veiga");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getNomeExibicao()).isEqualTo("Raphael Veiga");
    }

    @Test
    void deveRejeitarDoisJogadoresComAMesmaChaveNatural() {
        var nascimento = LocalDate.of(1997, 2, 5);
        jogadorRepository.saveAndFlush(JogadorFactory.valido(
                "vitor-roque", "Vitor Roque", nascimento, idDoBrasil(), atacante()));

        var duplicado = JogadorFactory.valido(
                "vitor-roque-2", "Vitor Roque", nascimento, idDoBrasil(), atacante());

        assertThatThrownBy(() -> jogadorRepository.saveAndFlush(duplicado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Posicao atacante() {
        return posicaoRepository.findByCodigo("ATA").orElseThrow();
    }

    private Long idDoBrasil() {
        return jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
    }
}
