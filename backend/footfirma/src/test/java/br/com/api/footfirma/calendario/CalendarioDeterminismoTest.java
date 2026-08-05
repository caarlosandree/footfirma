package br.com.api.footfirma.calendario;

import br.com.api.footfirma.TestcontainersConfiguration;
import br.com.api.footfirma.calendario.dto.EdicaoParaGerar;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O calendário precisa ser reprodutível pela semente, como o mundo já é para clubes e
 * elencos. Sem isso, regerar depois de um ajuste produziria um mundo diferente e o diff
 * seria ilegível.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CalendarioDeterminismoTest {

    @Autowired
    CalendarioService calendarioService;

    @Autowired
    CalendarioFactory factory;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void deveProduzirOMesmoCalendarioComAMesmaSementeRelativa() {
        var primeira = gerarEAssinar("det-a", 100L);
        var segunda = gerarEAssinar("det-b", 100L);
        assertThat(segunda).isEqualTo(primeira);
    }

    @Test
    void deveProduzirCalendarioDiferenteComSementeDiferente() {
        var comCem = gerarEAssinar("det-c", 100L);
        var comSete = gerarEAssinar("det-d", 7L);
        assertThat(comSete).isNotEqualTo(comCem);
    }

    @Test
    void deveRepetirOChaveamentoDoMataMataComAMesmaSemente() {
        assertThat(gerarEAssinarMataMata("mm-b", 55L))
                .isEqualTo(gerarEAssinarMataMata("mm-a", 55L));
    }

    /**
     * Assina o calendário por posição relativa, não por id.
     *
     * <p>Os ids diferem entre as duas gerações — são cenários distintos no mesmo banco. O
     * que precisa se repetir é a estrutura: qual participante enfrenta qual, em que ordem
     * e a quantos dias do início.
     *
     * <p>A semente efetiva é {@code semente + edicaoId}, então a chamada compensa o id da
     * edição para que as duas gerações partam do mesmo ponto.
     */
    private List<String> gerarEAssinar(String slug, long sementeRelativa) {
        var cenario = factory.criar(slug, 8, "PONTOS_CORRIDOS", 2);
        calendarioService.gerarTemporada(cenario.temporadaId(),
                sementeRelativa - cenario.edicaoId(),
                List.of(new EdicaoParaGerar(cenario.edicaoId(), 1, CalendarioFactory.PERFIL)));

        return jdbcTemplate.queryForList("""
                select r.ordem || '|' || pm.pos || '|' || pv.pos || '|' ||
                       (j.data_jogo - date '2026-04-04') as assinatura
                from jogo j
                join rodada r on r.id = j.rodada_id
                join lateral (select count(*) as pos from clube x
                              where x.slug like ? and x.id < j.mandante_id) pm on true
                join lateral (select count(*) as pos from clube x
                              where x.slug like ? and x.id < j.visitante_id) pv on true
                where r.fase_id = ?
                order by r.ordem, pm.pos
                """, String.class, slug + "%", slug + "%", cenario.faseId());
    }

    private List<String> gerarEAssinarMataMata(String slug, long sementeRelativa) {
        var cenario = factory.criar(slug, 8, "ELIMINATORIA", 2, true, true, true);
        calendarioService.gerarTemporada(cenario.temporadaId(),
                sementeRelativa - cenario.edicaoId(),
                List.of(new EdicaoParaGerar(cenario.edicaoId(), 1, CalendarioFactory.PERFIL)));

        return jdbcTemplate.queryForList("""
                select c.ordem || '|' || coalesce(pa.pos::text, 'vazio') as assinatura
                from confronto c
                left join lateral (select count(*) as pos from clube x
                                   where x.slug like ? and x.id < c.clube_a_id) pa on true
                where c.fase_id = ?
                order by c.ordem
                """, String.class, slug + "%", cenario.faseId());
    }
}
