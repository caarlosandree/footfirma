package br.com.api.footfirma.avaliacao;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.avaliacao.domain.AtributoAvaliavel;
import br.com.api.footfirma.avaliacao.repository.PerfilAvaliacaoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PerfilAvaliacaoIntegridadeTest {

    @Autowired
    PerfilAvaliacaoRepository perfilAvaliacaoRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveTerUmPerfilAtivoParaCadaUmaDasNovePosicoes() {
        var ativos = perfilAvaliacaoRepository.buscarAtivosComPesos();

        assertThat(ativos).hasSize(9);
        assertThat(ativos).extracting("posicaoId").doesNotHaveDuplicates();
    }

    // Este é o teste que pega peso quebrado no seed antes de ele virar overall
    // errado: a soma igual a 1.0 não é expressável como check de linha.
    @Test
    void deveSomarExatamenteUmEmTodoPerfilAtivo() {
        var ativos = perfilAvaliacaoRepository.buscarAtivosComPesos();

        for (var perfil : ativos) {
            var soma = perfil.getPesos().values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(soma)
                    .as("soma dos pesos do perfil da posição %d", perfil.getPosicaoId())
                    .usingComparator(BigDecimal::compareTo)
                    .isEqualTo(BigDecimal.ONE);
        }
    }

    @Test
    void deveDeclararOsDezoitoAtributosEmTodoPerfilAtivo() {
        var ativos = perfilAvaliacaoRepository.buscarAtivosComPesos();

        for (var perfil : ativos) {
            assertThat(perfil.getPesos().keySet())
                    .as("atributos do perfil da posição %d", perfil.getPosicaoId())
                    .containsExactlyInAnyOrder(AtributoAvaliavel.values());
        }
    }

    @Test
    void deveDarPesoZeroParaFaltaEPenaltiEmTodoPerfil() {
        var ativos = perfilAvaliacaoRepository.buscarAtivosComPesos();

        for (var perfil : ativos) {
            assertThat(perfil.getPesos().get(AtributoAvaliavel.FALTA))
                    .usingComparator(BigDecimal::compareTo).isEqualTo(BigDecimal.ZERO);
            assertThat(perfil.getPesos().get(AtributoAvaliavel.PENALTI))
                    .usingComparator(BigDecimal::compareTo).isEqualTo(BigDecimal.ZERO);
        }
    }

    @Test
    void deveDarAMaiorParteDoPesoDoGoleiroAsSkillsDeGoleiro() {
        var goleiro = jdbcTemplate.queryForObject("""
                select sum(peso) from perfil_avaliacao_peso w
                join perfil_avaliacao p on p.id = w.perfil_id
                join posicao pos on pos.id = p.posicao_id
                where pos.codigo = 'GOL' and p.ativo
                  and w.atributo in ('GOL_REFLEXO', 'GOL_POSICIONAMENTO', 'GOL_MANEJO')
                """, BigDecimal.class);

        assertThat(goleiro).usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("0.8200"));
    }
}
