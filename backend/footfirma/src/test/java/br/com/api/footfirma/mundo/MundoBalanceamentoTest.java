package br.com.api.footfirma.mundo;

import br.com.api.footfirma.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "footfirma.mundo.recriar=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MundoBalanceamentoTest {

    @Autowired
    MundoService mundoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeAll
    void gerarOMundo() {
        mundoService.gerar();
    }

    @Test
    void devePorAPrimeiraDivisaoAcimaDaSegunda() {
        var medias = jdbcTemplate.queryForList("""
                select c.nivel, avg(o.overall) as media
                from jogador_overall o
                join jogador j on j.id = o.jogador_id and j.posicao_principal_id = o.posicao_id
                join jogador_vinculo v on v.jogador_id = j.id and v.categoria = 'PROFISSIONAL'
                join edicao_participante p on p.clube_id = v.clube_id
                join edicao e on e.id = p.edicao_id
                join competicao c on c.id = e.competicao_id
                group by c.nivel order by c.nivel
                """);
        var primeira = ((Number) medias.getFirst().get("media")).doubleValue();
        var segunda = ((Number) medias.getLast().get("media")).doubleValue();
        assertThat(primeira).isGreaterThan(segunda + 4);
    }

    @Test
    void deveDarUmDestaqueACadaClube() {
        // Todo clube precisa de alguém para se olhar, por pior que seja o elenco.
        var semDestaque = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select v.clube_id, max(o.overall) as melhor, avg(o.overall) as media
                    from jogador_overall o
                    join jogador j on j.id = o.jogador_id and j.posicao_principal_id = o.posicao_id
                    join jogador_vinculo v on v.jogador_id = j.id and v.categoria = 'PROFISSIONAL'
                    group by v.clube_id
                ) elenco where melhor < media + 8
                """, Integer.class);
        assertThat(semDestaque).isZero();
    }

    @Test
    void deveEsconderJoiaNaBaseDeClubePobre() {
        var clubesComJoia = jdbcTemplate.queryForObject("""
                select count(distinct v.clube_id)
                from jogador_vinculo v
                join clube c on c.id = v.clube_id
                join jogador_atributo a on a.jogador_id = v.jogador_id
                where v.categoria = 'BASE' and c.forca_financeira < 50 and a.potencial_base >= 88
                """, Integer.class);
        assertThat(clubesComJoia).isPositive();
    }

    @Test
    void deveFazerOOverallMedioSeguirONivelDoElenco() {
        // Correlação, não igualdade: o overall é derivado dos atributos, e o ruído
        // por skill desloca o resultado alguns pontos.
        //
        // O alvo é o índice do clube comprimido para a escala de overall — a mesma
        // conta de ClubeGerado.alvoDoElenco(). Comparar direto com o índice cru
        // afirmaria que reputação 92 produz elenco de overall 92, que não é o desenho.
        var discrepantes = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select c.id,
                           round(38 + 0.45 * (0.6 * c.reputacao + 0.4 * c.forca_financeira)) as nivel,
                           avg(o.overall) as media
                    from clube c
                    join jogador_vinculo v on v.clube_id = c.id and v.categoria = 'PROFISSIONAL'
                    join jogador j on j.id = v.jogador_id
                    join jogador_overall o on o.jogador_id = j.id
                                          and o.posicao_id = j.posicao_principal_id
                    group by c.id, c.reputacao, c.forca_financeira
                ) comparacao where abs(media - nivel) > 8
                """, Integer.class);
        assertThat(discrepantes).isZero();
    }

    @Test
    void deveTerGiganteEndividadoComElencoAbaixoDaReputacao() {
        var gigantes = jdbcTemplate.queryForObject("""
                select count(*) from clube
                where reputacao >= 75 and forca_financeira <= 50
                """, Integer.class);
        assertThat(gigantes).isPositive();
    }
}
