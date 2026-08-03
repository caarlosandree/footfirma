package br.com.api.footfirma.jogador;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.jogador.domain.FonteAtributo;
import br.com.api.footfirma.jogador.domain.Jogador;
import br.com.api.footfirma.jogador.domain.JogadorAtributo;
import br.com.api.footfirma.jogador.repository.JogadorAtributoRepository;
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
class JogadorAtributoRepositoryTest {

    @Autowired
    JogadorRepository jogadorRepository;

    @Autowired
    JogadorAtributoRepository jogadorAtributoRepository;

    @Autowired
    PosicaoRepository posicaoRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveGuardarAtributosDeTemporadasDiferentesParaOMesmoJogador() {
        var jogador = jogadorSalvo();
        jogadorAtributoRepository.save(atributos(jogador, temporada("2025"), 70));
        jogadorAtributoRepository.save(atributos(jogador, temporada("2026"), 74));

        var de2026 = jogadorAtributoRepository.findByJogadorIdAndTemporadaId(jogador.getId(), temporada("2026"));

        assertThat(de2026).isPresent();
        assertThat(de2026.get().getFinalizacao()).isEqualTo(74);
    }

    @Test
    void deveRejeitarDoisConjuntosDeAtributosNaMesmaTemporada() {
        var jogador = jogadorSalvo();
        var temporada = temporada("2025");
        jogadorAtributoRepository.saveAndFlush(atributos(jogador, temporada, 70));

        assertThatThrownBy(() -> jogadorAtributoRepository.saveAndFlush(atributos(jogador, temporada, 80)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarAtributoForaDaEscalaDeZeroANoventaENove() {
        var jogador = jogadorSalvo();
        var invalido = atributos(jogador, temporada("2025"), 70);
        invalido.setRitmo(120);

        assertThatThrownBy(() -> jogadorAtributoRepository.saveAndFlush(invalido))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Jogador jogadorSalvo() {
        var paisId = jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
        var atacante = posicaoRepository.findByCodigo("ATA").orElseThrow();
        return jogadorRepository.save(JogadorFactory.valido(
                "teste-jogador", "Jogador Teste", LocalDate.of(1998, 3, 10), paisId, atacante));
    }

    private Long temporada(String label) {
        jdbcTemplate.update("""
                insert into temporada (label, ano_inicio, ano_fim) values (?, ?, ?)
                on conflict (label) do nothing
                """, label, Integer.parseInt(label), Integer.parseInt(label));
        return jdbcTemplate.queryForObject("select id from temporada where label = ?", Long.class, label);
    }

    private JogadorAtributo atributos(Jogador jogador, Long temporadaId, int finalizacao) {
        var atributo = new JogadorAtributo(jogador, temporadaId, FonteAtributo.IMPORTADO);
        atributo.setRitmo(75);
        atributo.setForca(70);
        atributo.setFolego(80);
        atributo.setSalto(65);
        atributo.setAgilidade(78);
        atributo.setPasse(72);
        atributo.setDrible(76);
        atributo.setCruzamento(68);
        atributo.setFrieza(74);
        atributo.setFinalizacao(finalizacao);
        atributo.setCabeceio(60);
        atributo.setFalta(55);
        atributo.setPenalti(70);
        atributo.setDesarme(40);
        atributo.setMarcacao(38);
        atributo.setGolReflexo(10);
        atributo.setGolPosicionamento(10);
        atributo.setGolManejo(10);
        atributo.setPotencialBase(85);
        atributo.setPotencialVariacao(5);
        return atributo;
    }
}
