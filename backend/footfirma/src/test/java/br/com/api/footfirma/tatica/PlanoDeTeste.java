package br.com.api.footfirma.tatica;

import br.com.api.footfirma.tatica.dto.Largura;
import br.com.api.footfirma.tatica.dto.LinhaDefensiva;
import br.com.api.footfirma.tatica.dto.Mentalidade;
import br.com.api.footfirma.tatica.dto.NovoPlano;
import br.com.api.footfirma.tatica.dto.Pressao;
import br.com.api.footfirma.tatica.dto.Ritmo;
import br.com.api.footfirma.tatica.dto.TitularEscalado;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Um plano manual válido para o cenário: 4-4-2, um goleiro no slot 1, dez jogadores de
 * linha nos slots 2-11, e banco de seis começando pelo goleiro reserva.
 */
final class PlanoDeTeste {

    private PlanoDeTeste() {
    }

    static NovoPlano valido(JdbcTemplate jdbc, CenarioDeElenco cenario) {
        var formacaoId = jdbc.queryForObject(
                "select id from formacao where codigo = '4-4-2'", Long.class);

        // O slot 1 do 4-4-2 é GOL e os outros dez não são. Escalar por ordem de id poria
        // os dois goleiros do cenário nos slots 1 e 2 — e o banco ficaria sem goleiro.
        var goleiros = porPosicao(jdbc, cenario, "=");
        var linha = porPosicao(jdbc, cenario, "<>");

        var titulares = new ArrayList<TitularEscalado>();
        titulares.add(new TitularEscalado(goleiros.getFirst(), 1));
        for (int slot = 2; slot <= 11; slot++) {
            titulares.add(new TitularEscalado(linha.get(slot - 2), slot));
        }

        var banco = new ArrayList<Long>();
        banco.add(goleiros.get(1));
        banco.addAll(linha.subList(10, 15));

        return new NovoPlano(formacaoId, goleiros.getFirst(),
                Mentalidade.EQUILIBRADA, Ritmo.EQUILIBRADO, LinhaDefensiva.MEDIA,
                Pressao.MEDIA, Largura.MEDIA, List.copyOf(titulares), List.copyOf(banco));
    }

    static NovoPlano com(NovoPlano base, List<TitularEscalado> titulares,
                         List<Long> banco, Long capitaoId) {
        return new NovoPlano(base.formacaoId(), capitaoId, base.mentalidade(), base.ritmo(),
                base.linhaDefensiva(), base.pressao(), base.largura(), titulares, banco);
    }

    private static List<Long> porPosicao(JdbcTemplate jdbc, CenarioDeElenco cenario,
                                         String comparador) {
        return jdbc.queryForList("""
                select v.jogador_id from jogador_vinculo v
                join jogador j on j.id = v.jogador_id
                join posicao p on p.id = j.posicao_principal_id
                where v.clube_id = ? and v.temporada_id = ? and p.codigo %s 'GOL'
                order by v.jogador_id
                """.formatted(comparador), Long.class,
                cenario.clubeId(), cenario.temporadaId());
    }
}
