package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.shared.evento.PartidaEncerrada;
import br.com.api.footfirma.treinador.JogadorInsatisfeito;
import br.com.api.footfirma.treinador.domain.Afinidade;
import br.com.api.footfirma.treinador.domain.Skill;
import br.com.api.footfirma.treinador.domain.TreinadorJogador;
import br.com.api.footfirma.treinador.domain.VinculoTreinador;
import br.com.api.footfirma.treinador.repository.AfinidadeRepository;
import br.com.api.footfirma.treinador.repository.TreinadorJogadorRepository;
import br.com.api.footfirma.treinador.repository.TreinadorSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.MORAL_JOGADOR_INSATISFEITO;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.NEUTRO;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.SKILL_MINIMA;

/**
 * A relação do treinador com cada jogador durante uma passagem: quem entra no elenco, com
 * que moral chega e como ela reage aos minutos recebidos.
 */
@Component
@RequiredArgsConstructor
class ConfiancaDoElenco {

    private final TreinadorJogadorRepository confiancas;
    private final TreinadorSkillRepository skills;
    private final AfinidadeRepository afinidades;
    private final ApplicationEventPublisher eventos;

    /**
     * Põe o jogador no elenco do vínculo com a moral semeada pela memória do par.
     *
     * <p>Ex-pupilo chega adiantado, quem foi queimado no banco chega desconfiado, e quem
     * nunca trabalhou com este treinador chega neutro. Trocar de clube não é reset.
     */
    TreinadorJogador registrar(VinculoTreinador vinculo, Long jogadorId) {
        var afinidade = afinidades
                .findByTreinadorIdAndJogadorId(vinculo.getTreinador().getId(), jogadorId)
                .map(Afinidade::getAfinidade)
                .orElse(NEUTRO);

        return confiancas.save(new TreinadorJogador(vinculo, jogadorId,
                CalculadoraDeExpectativa.moralInicialDoJogador(afinidade)));
    }

    void aplicarMinutagem(VinculoTreinador vinculo, PartidaEncerrada evento) {
        // Mapa vazio é "o produtor não apura minutagem", não "ninguém jogou". Tratar os
        // dois igual puniria o elenco inteiro por uma lacuna do contrato.
        if (evento.minutosPorJogador().isEmpty()) {
            return;
        }

        var lideranca = lideranca(vinculo);
        var quando = OffsetDateTime.ofInstant(evento.ocorridoEm(), ZoneOffset.UTC);

        for (var relacao : confiancas.findByVinculoId(vinculo.getId())) {
            // Ausente do mapa é ausente da partida: ficar de fora é exatamente a
            // frustração que o status de confiança promete não causar.
            var minutos = evento.minutosPorJogador().getOrDefault(relacao.getJogadorId(), 0);
            var antes = relacao.getMoral();

            relacao.setMoral(MotorDeConfianca.aplicar(antes,
                    new FatoDeMinutagem(relacao.getStatusConfianca(), minutos, lideranca)));
            relacao.acumularMinutos(minutos);
            relacao.setAtualizadoEm(quando);

            if (antes >= MORAL_JOGADOR_INSATISFEITO
                    && relacao.getMoral() < MORAL_JOGADOR_INSATISFEITO) {
                eventos.publishEvent(new JogadorInsatisfeito(vinculo.getTreinador().getId(),
                        relacao.getJogadorId(), vinculo.getId(), relacao.getMoral()));
            }
        }
    }

    private int lideranca(VinculoTreinador vinculo) {
        return skills.findByTreinadorIdAndTemporadaIdAndSkill(
                        vinculo.getTreinador().getId(), vinculo.getTemporadaId(), Skill.LIDERANCA)
                .map(skill -> skill.getValor())
                .orElse(SKILL_MINIMA);
    }
}
