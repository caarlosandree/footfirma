package br.com.api.footfirma.tatica;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Mundo mínimo para os testes de escalação: uma temporada, dois clubes, um treinador
 * vinculado ao primeiro, um elenco de 22 no primeiro clube — dois goleiros, vinte de
 * linha, os dois últimos da base — e o overall materializado nas nove posições.
 *
 * <p>O segundo clube existe sem elenco e sem treinador, de propósito: é ele quem prova
 * os caminhos vazios de {@code buscarPerfilDoClube} e {@code buscarPlanoVigente}.
 *
 * <p>Escreve por JDBC porque os repositories de jogador, clube, treinador e avaliacao
 * são internos aos seus módulos — o teste vive em {@code tatica}.
 */
record CenarioDeElenco(long temporadaId, long clubeId, long clubeSemTreinadorId,
                       long primeiroJogadorId, long segundoJogadorId,
                       int tamanhoDoElenco, int taticaDoTreinador, int reputacaoDoTreinador,
                       List<Long> posicoesEmOrdem) {

    static final int GOLEIROS = 2;
    static final int JOGADORES_DE_LINHA = 20;
    static final int TAMANHO_DO_ELENCO = GOLEIROS + JOGADORES_DE_LINHA;
    static final int DA_BASE = 2;
    static final int TATICA = 6;
    static final int REPUTACAO = 70;

    static CenarioDeElenco montar(JdbcTemplate jdbc) {
        limpar(jdbc);

        var temporadaId = jdbc.queryForObject("""
                insert into temporada (label, ano_inicio, ano_fim)
                values ('2026', 2026, 2026) returning id
                """, Long.class);

        var paisId = jdbc.queryForObject(
                "select id from pais where iso_code = 'BRA'", Long.class);

        var clubeId = inserirClube(jdbc, "clube-um", "Clube Um", paisId, 70);
        var clubeSemTreinadorId = inserirClube(jdbc, "clube-dois", "Clube Dois", paisId, 40);

        var treinadorId = jdbc.queryForObject("""
                insert into treinador (slug, nome_completo, nome_exibicao, data_nascimento,
                                       pais_id, tipo, reputacao, pontos_disponiveis, semente)
                values ('tec-um', 'Técnico Um', 'Um', date '1975-03-11', ?, 'IA', ?, 0, 1)
                returning id
                """, Long.class, paisId, REPUTACAO);

        jdbc.update("""
                insert into vinculo_treinador (treinador_id, clube_id, temporada_id,
                                               inicio, moral, meta_posicao)
                values (?, ?, ?, date '2026-01-01', 50.0, 5)
                """, treinadorId, clubeId, temporadaId);

        jdbc.update("""
                insert into treinador_skill (treinador_id, temporada_id, skill, valor)
                values (?, ?, 'TATICA', ?)
                """, treinadorId, temporadaId, TATICA);
        for (var skill : List.of("VISAO_DE_JOGO", "PRELECAO", "LIDERANCA",
                "TREINAMENTO", "NEGOCIACAO")) {
            jdbc.update("""
                    insert into treinador_skill (treinador_id, temporada_id, skill, valor)
                    values (?, ?, ?, 2)
                    """, treinadorId, temporadaId, skill);
        }

        var posicoes = jdbc.queryForList("select id from posicao order by ordem", Long.class);
        var goleiro = posicoes.getFirst();
        var deLinha = posicoes.subList(1, posicoes.size());

        var jogadorIds = new ArrayList<Long>();
        for (int i = 0; i < TAMANHO_DO_ELENCO; i++) {
            // Os dois primeiros são goleiros; o resto roda pelas oito posições de linha.
            var posicaoPrincipal = i < GOLEIROS
                    ? goleiro
                    : deLinha.get((i - GOLEIROS) % deLinha.size());
            var categoria = i >= TAMANHO_DO_ELENCO - DA_BASE ? "BASE" : "PROFISSIONAL";
            jogadorIds.add(inserirJogador(jdbc, temporadaId, clubeId, paisId,
                    posicaoPrincipal, posicoes, categoria, i));
        }

        return new CenarioDeElenco(temporadaId, clubeId, clubeSemTreinadorId,
                jogadorIds.get(0), jogadorIds.get(1), TAMANHO_DO_ELENCO,
                TATICA, REPUTACAO, posicoes);
    }

    private static long inserirClube(JdbcTemplate jdbc, String slug, String nome,
                                     long paisId, int reputacao) {
        return jdbc.queryForObject("""
                insert into clube (slug, nome_oficial, nome_curto, pais_id, reputacao,
                                   forca_financeira)
                values (?, ?, ?, ?, ?, 50) returning id
                """, Long.class, slug, nome, nome, paisId, reputacao);
    }

    private static long inserirJogador(JdbcTemplate jdbc, long temporadaId, long clubeId,
                                       long paisId, long posicaoPrincipalId,
                                       List<Long> posicoes, String categoria, int indice) {
        var jogadorId = jdbc.queryForObject("""
                insert into jogador (slug, chave_natural, nome_completo, nome_exibicao,
                                     data_nascimento, pais_id, pe_preferido,
                                     posicao_principal_id, semente, origem)
                values (?, ?, ?, ?, date '2000-01-01', ?, 'DIREITO', ?, ?, 'GERADO')
                returning id
                """, Long.class,
                "jog-" + indice, "jog-" + indice + "|2000-01-01|BRA",
                "Jogador " + indice, "J" + indice, paisId, posicaoPrincipalId, (long) indice);

        jdbc.update("""
                insert into jogador_vinculo (jogador_id, clube_id, temporada_id, tipo,
                                             numero_camisa, categoria)
                values (?, ?, ?, 'CONTRATO', ?, ?)
                """, jogadorId, clubeId, temporadaId, indice + 1, categoria);

        // Overall determinístico e sem empates entre jogadores: alto na posição
        // principal, baixo nas demais. O índice entra nos dois para dar ordem estável.
        for (var posicaoId : posicoes) {
            var overall = posicaoId.equals(posicaoPrincipalId) ? 70 + indice : 20 + indice;
            jdbc.update("""
                    insert into jogador_overall (jogador_id, temporada_id, posicao_id,
                                                 perfil_versao, overall, calculado_em)
                    values (?, ?, ?, 1, ?, now())
                    """, jogadorId, temporadaId, posicaoId, overall);
        }

        return jogadorId;
    }

    private static void limpar(JdbcTemplate jdbc) {
        jdbc.execute("""
                truncate plano_escalacao, plano_tatico, jogador_overall, jogador_vinculo,
                         treinador_skill, vinculo_treinador, treinador, jogador, clube,
                         temporada restart identity cascade
                """);
    }
}
