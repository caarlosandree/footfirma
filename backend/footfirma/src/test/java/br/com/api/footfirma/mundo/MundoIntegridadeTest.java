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
class MundoIntegridadeTest {

    @Autowired
    MundoService mundoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    // Gerar uma vez para a classe inteira: são 1.520 jogadores, e repetir por teste
    // multiplicaria a carga por seis sem checar nada de novo.
    @BeforeAll
    void gerarOMundo() {
        mundoService.gerar();
    }

    @Test
    void deveCriarMilQuinhentosEVinteJogadores() {
        assertThat(contar("jogador")).isEqualTo(1_520);
        assertThat(contar("jogador_atributo")).isEqualTo(1_520);
        assertThat(contar("jogador_atributo_oculto")).isEqualTo(1_520);
        assertThat(contar("jogador_vinculo")).isEqualTo(1_520);
    }

    @Test
    void deveDarVinteESeisProfissionaisEDozeDaBaseACadaClube() {
        var fora = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select clube_id,
                           count(*) filter (where categoria = 'PROFISSIONAL') as profissionais,
                           count(*) filter (where categoria = 'BASE') as base
                    from jogador_vinculo group by clube_id
                ) elenco where profissionais <> 26 or base <> 12
                """, Integer.class);
        assertThat(fora).isZero();
    }

    @Test
    void deveCobrirAsNovePosicoesEmTodoElencoProfissional() {
        var incompletos = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select v.clube_id, count(distinct p.codigo) as posicoes
                    from jogador_vinculo v
                    join jogador j on j.id = v.jogador_id
                    join posicao p on p.id = j.posicao_principal_id
                    where v.categoria = 'PROFISSIONAL'
                    group by v.clube_id
                ) cobertura where posicoes <> 9
                """, Integer.class);
        assertThat(incompletos).isZero();
    }

    @Test
    void deveDarCamisasUnicasDentroDeCadaClube() {
        var duplicadas = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select clube_id, numero_camisa from jogador_vinculo
                    where numero_camisa is not null
                    group by clube_id, numero_camisa having count(*) > 1
                ) repetidas
                """, Integer.class);
        assertThat(duplicadas).isZero();
    }

    @Test
    void deveMaterializarOverallDeTodoJogadorNasNovePosicoes() {
        assertThat(contar("jogador_overall")).isEqualTo(1_520 * 9);
    }

    @Test
    void deveManterOOverallDentroDaFaixaQueACurvaProduz() {
        // O alvo não é gravado; o que se verifica é que o overall na posição
        // principal ficou dentro da faixa que a curva de papéis produz.
        var forasteiros = jdbcTemplate.queryForObject("""
                select count(*) from jogador_overall o
                join jogador j on j.id = o.jogador_id
                where o.posicao_id = j.posicao_principal_id
                  and (o.overall < 20 or o.overall > 97)
                """, Integer.class);
        assertThat(forasteiros).isZero();
    }

    @Test
    void deveDeixarTodoClubeComPlanoTaticoVigenteEEscalado() {
        assertThat(contar("plano_tatico")).isEqualTo(40);
        // 11 titulares e o banco cheio: com 38 jogadores no elenco a sobra passa do
        // teto, então todo clube bate BANCO_MAXIMO.
        assertThat(contar("plano_escalacao")).isEqualTo(40 * (11 + 12));

        var fora = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select p.id,
                           count(*) filter (where e.slot_ordem  is not null) as titulares,
                           count(*) filter (where e.ordem_banco is not null) as banco
                    from plano_tatico p
                    join plano_escalacao e on e.plano_id = p.id
                    where p.vigente and p.versao = 1
                      and p.origem = 'AUTOMATICO' and p.capitao_id is not null
                    group by p.id
                ) plano where titulares <> 11 or banco <> 12
                """, Integer.class);
        assertThat(fora).isZero();
    }

    @Test
    void deveEscalarSomenteJogadoresDoProprioClube() {
        var intrusos = jdbcTemplate.queryForObject("""
                select count(*) from plano_tatico p
                join plano_escalacao e on e.plano_id = p.id
                left join jogador_vinculo v
                       on v.jogador_id = e.jogador_id
                      and v.clube_id = p.clube_id
                      and v.temporada_id = p.temporada_id
                where v.jogador_id is null
                """, Integer.class);
        assertThat(intrusos).isZero();
    }

    @Test
    void deveCongelarAAptidaoDeTodoEscaladoComOOverallJaMaterializado() {
        // O escalador roda depois de materializar, então nenhuma linha pode ter
        // nascido com aptidão zero — seria a prova de que a ordem foi invertida.
        var zerados = jdbcTemplate.queryForObject(
                "select count(*) from plano_escalacao where aptidao_no_momento <= 0",
                Integer.class);
        assertThat(zerados).isZero();
    }

    @Test
    void deveGerarOCalendarioDasDuasLigas() {
        // 20 clubes em turno e returno: 38 rodadas e 380 jogos por divisão.
        assertThat(contar("rodada")).isEqualTo(76);
        assertThat(contar("confronto")).isEqualTo(760);
        assertThat(contar("jogo")).isEqualTo(760);
    }

    @Test
    void naoDeveMarcarJogoNaSextaEmNenhumaDasDuasLigas() {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from jogo where extract(dow from data_jogo) = 5",
                Integer.class)).isZero();
    }

    @Test
    void deveRespeitarODescansoDeTodoClubeNoMundoInteiro() {
        var violacoes = jdbcTemplate.queryForObject("""
                with agenda as (
                    select mandante_id as clube_id, data_jogo from jogo
                    union all
                    select visitante_id, data_jogo from jogo
                ),
                consecutivos as (
                    select clube_id, data_jogo,
                           lag(data_jogo) over (partition by clube_id order by data_jogo) as anterior
                    from agenda
                )
                select count(*) from consecutivos
                where anterior is not null and data_jogo - anterior < 3
                """, Integer.class);
        assertThat(violacoes).isZero();
    }

    @Test
    void deveFazerTodoClubeJogarTrintaEOitoVezes() {
        var fora = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select clube_id, count(*) as jogos from (
                        select mandante_id as clube_id from jogo
                        union all
                        select visitante_id from jogo
                    ) a group by clube_id
                ) t where jogos <> 38
                """, Integer.class);
        assertThat(fora).isZero();
    }

    @Test
    void naoDeveCruzarClubesDeDivisoesDiferentesNoMesmoJogo() {
        var cruzados = jdbcTemplate.queryForObject("""
                select count(*) from jogo j
                join rodada r on r.id = j.rodada_id
                join fase f on f.id = r.fase_id
                join edicao_participante pm on pm.edicao_id = f.edicao_id and pm.clube_id = j.mandante_id
                left join edicao_participante pv on pv.edicao_id = f.edicao_id and pv.clube_id = j.visitante_id
                where pv.clube_id is null
                """, Integer.class);
        assertThat(cruzados).isZero();
    }

    private int contar(String tabela) {
        return jdbcTemplate.queryForObject("select count(*) from " + tabela, Integer.class);
    }
}
