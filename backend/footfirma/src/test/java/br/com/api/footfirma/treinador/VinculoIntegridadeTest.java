package br.com.api.footfirma.treinador;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.treinador.domain.MotivoFim;
import br.com.api.footfirma.treinador.domain.Treinador;
import br.com.api.footfirma.treinador.domain.VinculoTreinador;
import br.com.api.footfirma.treinador.repository.TreinadorRepository;
import br.com.api.footfirma.treinador.repository.VinculoTreinadorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VinculoIntegridadeTest {

    @Autowired
    TreinadorRepository treinadorRepository;

    @Autowired
    VinculoTreinadorRepository vinculoTreinadorRepository;

    @Autowired
    TestEntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveRejeitarSegundoVinculoAtivoNoMesmoClube() {
        var clubeId = clube("gremio");
        vinculoTreinadorRepository.save(vinculoAtivo(treinador("ana-souza"), clubeId));
        entityManager.flush();

        var segundo = vinculoAtivo(treinador("bia-lima"), clubeId);

        assertThatThrownBy(() -> vinculoTreinadorRepository.saveAndFlush(segundo))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_vinculo_clube_ativo");
    }

    @Test
    void deveRejeitarTreinadorDirigindoDoisClubes() {
        var treinador = treinador("caio-melo");
        vinculoTreinadorRepository.save(vinculoAtivo(treinador, clube("inter")));
        entityManager.flush();

        var segundo = vinculoAtivo(treinador, clube("juventude"));

        assertThatThrownBy(() -> vinculoTreinadorRepository.saveAndFlush(segundo))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_vinculo_treinador_ativo");
    }

    @Test
    void devePermitirNovoVinculoAposEncerrarOAnterior() {
        var treinador = treinador("davi-rocha");
        var primeiro = vinculoTreinadorRepository.save(vinculoAtivo(treinador, clube("bahia")));
        primeiro.setFim(LocalDate.of(2026, 6, 30));
        primeiro.setMotivoFim(MotivoFim.DEMISSAO);
        entityManager.flush();

        var segundo = vinculoTreinadorRepository.save(vinculoAtivo(treinador, clube("vitoria")));
        entityManager.flush();

        assertThat(segundo.getId()).isNotNull();
        assertThat(segundo.estaAtivo()).isTrue();
    }

    @Test
    void devePermitirClubeContratarOutroTreinadorAposDemissao() {
        var clubeId = clube("sport");
        var demitido = vinculoTreinadorRepository.save(vinculoAtivo(treinador("elias-dias"), clubeId));
        demitido.setFim(LocalDate.of(2026, 5, 1));
        demitido.setMotivoFim(MotivoFim.DEMISSAO);
        entityManager.flush();

        var sucessor = vinculoTreinadorRepository.save(vinculoAtivo(treinador("fabio-reis"), clubeId));
        entityManager.flush();

        assertThat(vinculoTreinadorRepository.findByClubeIdAndFimIsNull(clubeId))
                .contains(sucessor);
    }

    @Test
    void deveRejeitarVinculoEncerradoSemMotivo() {
        var vinculo = vinculoTreinadorRepository.save(vinculoAtivo(treinador("gil-nunes"), clube("ceara")));
        vinculo.setFim(LocalDate.of(2026, 8, 1));

        assertThatThrownBy(() -> entityManager.flush())
                .rootCause()
                .hasMessageContaining("ck_vinculo_treinador_motivo");
    }

    @Test
    void deveRejeitarMoralForaDaFaixaZeroNoventaENove() {
        var vinculo = vinculoAtivo(treinador("hugo-pires"), clube("fortaleza"));
        vinculo.setMoral(120.0);

        assertThatThrownBy(() -> vinculoTreinadorRepository.saveAndFlush(vinculo))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("moral");
    }

    private Treinador treinador(String slug) {
        return treinadorRepository.save(TreinadorFactory.humano(slug, paisBrasil()));
    }

    private VinculoTreinador vinculoAtivo(Treinador treinador, Long clubeId) {
        return TreinadorFactory.vinculoAtivo(treinador, clubeId, temporada("2026"));
    }

    private Long paisBrasil() {
        return jdbcTemplate.queryForObject("select id from pais where iso_code = 'BRA'", Long.class);
    }

    private Long clube(String slug) {
        jdbcTemplate.update("""
                insert into clube (slug, nome_oficial, nome_curto, pais_id) values (?, ?, ?, ?)
                on conflict (slug) do nothing
                """, slug, slug + " oficial", slug, paisBrasil());
        return jdbcTemplate.queryForObject("select id from clube where slug = ?", Long.class, slug);
    }

    private Long temporada(String label) {
        jdbcTemplate.update("""
                insert into temporada (label, ano_inicio, ano_fim) values (?, ?, ?)
                on conflict (label) do nothing
                """, label, Integer.parseInt(label), Integer.parseInt(label));
        return jdbcTemplate.queryForObject("select id from temporada where label = ?", Long.class, label);
    }
}
