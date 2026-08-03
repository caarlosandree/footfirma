package br.com.api.footfirma.treinador.dto;

import br.com.api.footfirma.treinador.domain.TipoTreinador;

import java.time.LocalDate;

/**
 * Entrada de criação, com país e temporada já resolvidos em id por quem chama —
 * resolver aqui faria o módulo depender de {@code geografia} e {@code temporada} para
 * escrever.
 *
 * <p>{@code temporadaId} é obrigatório porque skill é versionada por temporada: um
 * treinador sem a temporada da distribuição seria um treinador sem skill nenhuma.
 *
 * <p>Não há {@code semente} nem {@code reputacao}: a primeira é derivada do slug, e a
 * segunda nasce neutra e só muda ao encerrar vínculo. Deixar o cliente escolher a
 * reputação seria deixá-lo escolher quais clubes o procuram.
 */
public record NovoTreinador(String slug, String nomeCompleto, String nomeExibicao,
                            LocalDate dataNascimento, Long paisId, TipoTreinador tipo,
                            Long temporadaId, DistribuicaoDeSkills skills) {
}
