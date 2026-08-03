package br.com.api.footfirma.treinador;

import br.com.api.footfirma.shared.exception.DistribuicaoInvalidaException;
import br.com.api.footfirma.treinador.domain.Skill;
import br.com.api.footfirma.treinador.domain.TipoTreinador;
import br.com.api.footfirma.treinador.domain.TreinadorSkill;
import br.com.api.footfirma.treinador.dto.DistribuicaoDeSkills;
import br.com.api.footfirma.treinador.dto.NovoTreinador;
import br.com.api.footfirma.treinador.dto.TreinadorResumo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A soma das seis skills numa temporada precisa bater com {@code 20 + pontos ganhos}.
 *
 * <p>É invariante de agregado, e {@code check} não enxerga outras linhas: quem a garante é
 * o serviço, e é isto que estes testes cravam. O banco cobre o que ele consegue cobrir —
 * a faixa de cada valor isolado.
 */
class SkillIntegridadeTest extends CenarioDeTreinador {

    @Autowired
    TreinadorService treinadorService;

    @Test
    void deveCriarTreinadorQuandoSomaDasSkillsEhVinte() {
        var resumo = treinadorService.criar(novoCom(TreinadorFactory.distribuicaoValida()));

        assertThat(resumo.slug()).isEqualTo("ana-souza");
        assertThat(resumo.reputacao()).isEqualTo(50);
    }

    @Test
    void deveGravarAsSeisSkillsDaTemporadaAoCriar() {
        var distribuicao = TreinadorFactory.distribuicaoValida();

        var resumo = treinadorService.criar(novoCom(distribuicao));

        assertThat(detalheDe(resumo).skills()).containsExactlyInAnyOrderEntriesOf(distribuicao);
    }

    @Test
    void deveNascerSemPontosDisponiveis() {
        var resumo = treinadorService.criar(novoCom(TreinadorFactory.distribuicaoValida()));

        assertThat(detalheDe(resumo).pontosDisponiveis()).isZero();
    }

    @Test
    void deveRejeitarQuandoSomaDasSkillsPassaDeVinte() {
        var distribuicao = distribuicao(10, 10, 2, 1, 1, 1);

        assertThatThrownBy(() -> treinadorService.criar(novoCom(distribuicao)))
                .isInstanceOf(DistribuicaoInvalidaException.class)
                .hasMessageContaining("20");
    }

    @Test
    void deveRejeitarQuandoAlgumaSkillPassaDoTetoDez() {
        var distribuicao = distribuicao(15, 1, 1, 1, 1, 1);

        assertThatThrownBy(() -> treinadorService.criar(novoCom(distribuicao)))
                .isInstanceOf(DistribuicaoInvalidaException.class);
    }

    @Test
    void deveRejeitarQuandoFaltaAlgumaDasSeisSkills() {
        var distribuicao = Map.of(Skill.VISAO_DE_JOGO, 15, Skill.PRELECAO, 5);

        assertThatThrownBy(() -> treinadorService.criar(novoCom(distribuicao)))
                .isInstanceOf(DistribuicaoInvalidaException.class);
    }

    @Test
    void deveRejeitarValorForaDaFaixaDiretoNoBanco() {
        var treinador = treinador("burlao");
        var fora = new TreinadorSkill(treinador, temporada(), Skill.TATICA, 15);

        assertThatThrownBy(() -> skills.saveAndFlush(fora))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarDistribuicaoAcimaDosPontosDisponiveis() {
        var resumo = treinadorService.criar(novoCom(TreinadorFactory.distribuicaoValida()));

        assertThatThrownBy(() -> treinadorService.distribuirPontos(
                resumo.id(), temporada(), new DistribuicaoDeSkills(Map.of(Skill.TATICA, 3))))
                .isInstanceOf(DistribuicaoInvalidaException.class);
    }

    @Test
    void deveSomarOsPontosGanhosAsSkillsDaTemporada() {
        var resumo = treinadorService.criar(novoCom(TreinadorFactory.distribuicaoValida()));
        comPontosDisponiveis(resumo, 3);

        var detalhe = treinadorService.distribuirPontos(resumo.id(), temporada(),
                new DistribuicaoDeSkills(Map.of(Skill.TATICA, 3)));

        assertThat(detalhe.skills()).containsEntry(Skill.TATICA, 6);
        assertThat(detalhe.skills()).containsEntry(Skill.LIDERANCA, 3);
    }

    @Test
    void deveZerarOsPontosDisponiveisAposDistribuir() {
        var resumo = treinadorService.criar(novoCom(TreinadorFactory.distribuicaoValida()));
        comPontosDisponiveis(resumo, 3);

        var detalhe = treinadorService.distribuirPontos(resumo.id(), temporada(),
                new DistribuicaoDeSkills(Map.of(Skill.TATICA, 2, Skill.PRELECAO, 1)));

        assertThat(detalhe.pontosDisponiveis()).isZero();
    }

    @Test
    void deveRejeitarDistribuicaoQueEstouraOTetoDaSkill() {
        var resumo = treinadorService.criar(
                novoCom(TreinadorFactory.especialistaEm(Skill.VISAO_DE_JOGO)));
        comPontosDisponiveis(resumo, 1);

        assertThatThrownBy(() -> treinadorService.distribuirPontos(
                resumo.id(), temporada(), new DistribuicaoDeSkills(Map.of(Skill.VISAO_DE_JOGO, 1))))
                .isInstanceOf(DistribuicaoInvalidaException.class);
    }

    @Test
    void deveRejeitarDistribuicaoEmTemporadaSemSkillsGravadas() {
        var resumo = treinadorService.criar(novoCom(TreinadorFactory.distribuicaoValida()));
        comPontosDisponiveis(resumo, 3);

        assertThatThrownBy(() -> treinadorService.distribuirPontos(
                resumo.id(), outraTemporada(), new DistribuicaoDeSkills(Map.of(Skill.TATICA, 3))))
                .isInstanceOf(DistribuicaoInvalidaException.class);
    }

    private NovoTreinador novoCom(Map<Skill, Integer> distribuicao) {
        return new NovoTreinador("ana-souza", "Ana Souza", "Ana", LocalDate.of(1980, 1, 1),
                paisBrasil(), TipoTreinador.HUMANO, temporada(),
                new DistribuicaoDeSkills(distribuicao));
    }

    private br.com.api.footfirma.treinador.dto.TreinadorDetalhe detalheDe(TreinadorResumo resumo) {
        return treinadorService.buscarPorSlug(resumo.slug(), temporada()).orElseThrow();
    }

    private void comPontosDisponiveis(TreinadorResumo resumo, int pontos) {
        treinadores.findById(resumo.id()).orElseThrow().setPontosDisponiveis(pontos);
    }

    /** Na ordem do enum: visão, preleção, liderança, treinamento, tática, negociação. */
    private static Map<Skill, Integer> distribuicao(int... valores) {
        var distribuicao = new EnumMap<Skill, Integer>(Skill.class);
        for (var indice = 0; indice < Skill.values().length; indice++) {
            distribuicao.put(Skill.values()[indice], valores[indice]);
        }
        return distribuicao;
    }

    private Long outraTemporada() {
        jdbcTemplate.update("""
                insert into temporada (label, ano_inicio, ano_fim) values ('2027', 2027, 2027)
                on conflict (label) do nothing
                """);
        return jdbcTemplate.queryForObject(
                "select id from temporada where label = '2027'", Long.class);
    }
}
