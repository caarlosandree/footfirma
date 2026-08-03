package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.shared.evento.PartidaEncerrada;
import br.com.api.footfirma.treinador.JogadorInsatisfeito;
import br.com.api.footfirma.treinador.TreinadorDemitido;
import br.com.api.footfirma.treinador.TreinadorSobPressao;
import br.com.api.footfirma.treinador.VinculoEncerrado;
import br.com.api.footfirma.treinador.domain.EventoProcessado;
import br.com.api.footfirma.treinador.domain.MotivoFim;
import br.com.api.footfirma.treinador.domain.Resultado;
import br.com.api.footfirma.treinador.domain.Skill;
import br.com.api.footfirma.treinador.domain.TreinadorJogador;
import br.com.api.footfirma.treinador.domain.VinculoTreinador;
import br.com.api.footfirma.treinador.repository.EventoProcessadoRepository;
import br.com.api.footfirma.treinador.repository.TreinadorJogadorRepository;
import br.com.api.footfirma.treinador.repository.TreinadorSkillRepository;
import br.com.api.footfirma.treinador.repository.VinculoTreinadorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.MORAL_JOGADOR_INSATISFEITO;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.SKILL_MINIMA;

/**
 * A borda do módulo: traduz o que aconteceu em campo para as duas morais que o módulo
 * guarda — a do treinador com o clube e a de cada jogador com o treinador.
 *
 * <p>Nada aqui decide regra. Os motores são funções puras e recebem apenas o fato; esta
 * classe existe para achar o vínculo, garantir que o evento entre uma vez só e gravar o
 * que os motores devolveram.
 */
@Component
@RequiredArgsConstructor
class OuvinteDePartida {

    private final VinculoTreinadorRepository vinculos;
    private final TreinadorJogadorRepository confiancas;
    private final TreinadorSkillRepository skills;
    private final EventoProcessadoRepository processados;
    private final ApplicationEventPublisher eventos;

    @ApplicationModuleListener
    void em(PartidaEncerrada evento) {
        processar(evento);
    }

    /**
     * O que o listener faz, com o proxy fora do caminho.
     *
     * <p>{@code @ApplicationModuleListener} é {@code @Async}, e o Modulith liga o
     * {@code @EnableAsync}: chamar {@link #em} direto devolveria antes de qualquer escrita
     * acontecer. Os testes entram por aqui.
     */
    void processar(PartidaEncerrada evento) {
        var doMandante = Resultado.de(evento.golsMandante(), evento.golsVisitante());

        aplicarNoClube(evento, evento.clubeMandanteId(), evento.reputacaoMandante(),
                evento.reputacaoVisitante(), doMandante);
        aplicarNoClube(evento, evento.clubeVisitanteId(), evento.reputacaoVisitante(),
                evento.reputacaoMandante(), doMandante.invertido());
    }

    private void aplicarNoClube(PartidaEncerrada evento, long clubeId, int minhaReputacao,
                                int reputacaoAdversario, Resultado resultado) {
        var vinculo = vinculos.findByClubeIdAndFimIsNull(clubeId).orElse(null);
        if (vinculo == null || !registrar(vinculo, evento)) {
            return;
        }

        // posicaoAtual recebe metaPosicao porque classificação não existe até a partida
        // existir — é o que mantém fator_meta em 1,0, como o spec previu. Quando a tabela
        // existir, só esta linha muda.
        var fato = new FatoDeResultado(minhaReputacao, reputacaoAdversario, resultado,
                vinculo.getMetaPosicao(), vinculo.getMetaPosicao());
        vinculo.setMoral(MotorDeMoral.aplicar(vinculo.getMoral(), fato));

        aplicarConfianca(vinculo, evento);
        avaliarDemissao(vinculo, evento, minhaReputacao);
    }

    /** @return {@code false} quando este evento já tinha sido aplicado a este vínculo. */
    private boolean registrar(VinculoTreinador vinculo, PartidaEncerrada evento) {
        if (processados.existsByVinculoIdAndChaveEvento(vinculo.getId(), evento.chave())) {
            return false;
        }
        processados.save(new EventoProcessado(vinculo, evento.chave()));
        return true;
    }

    private void aplicarConfianca(VinculoTreinador vinculo, PartidaEncerrada evento) {
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

            if (antes >= MORAL_JOGADOR_INSATISFEITO && relacao.getMoral() < MORAL_JOGADOR_INSATISFEITO) {
                publicarInsatisfacao(vinculo, relacao);
            }
        }
    }

    private void avaliarDemissao(VinculoTreinador vinculo, PartidaEncerrada evento,
                                 int reputacaoDoClube) {
        var partidas = (int) processados.countByVinculoId(vinculo.getId());

        switch (PoliticaDeDemissao.avaliar(vinculo.getMoral(), reputacaoDoClube, partidas)) {
            case SEGURO -> {
            }
            case SOB_PRESSAO -> eventos.publishEvent(new TreinadorSobPressao(
                    vinculo.getTreinador().getId(), vinculo.getClubeId(),
                    vinculo.getId(), vinculo.getMoral()));
            case DEMITIDO -> demitir(vinculo, evento);
        }
    }

    private void demitir(VinculoTreinador vinculo, PartidaEncerrada evento) {
        // A data sai do evento, não do relógio: reprocessar precisa reproduzir a mesma
        // carreira, e System.now() faria a data do fim depender de quando se reprocessou.
        vinculo.encerrar(LocalDate.ofInstant(evento.ocorridoEm(), ZoneOffset.UTC),
                MotivoFim.DEMISSAO);

        eventos.publishEvent(new TreinadorDemitido(vinculo.getTreinador().getId(),
                vinculo.getClubeId(), vinculo.getId(), vinculo.getMoral()));
        eventos.publishEvent(new VinculoEncerrado(vinculo.getTreinador().getId(),
                vinculo.getClubeId(), vinculo.getId(), vinculo.getTemporadaId()));
    }

    private void publicarInsatisfacao(VinculoTreinador vinculo, TreinadorJogador relacao) {
        eventos.publishEvent(new JogadorInsatisfeito(vinculo.getTreinador().getId(),
                relacao.getJogadorId(), vinculo.getId(), relacao.getMoral()));
    }

    private int lideranca(VinculoTreinador vinculo) {
        return skills.findByTreinadorIdAndTemporadaIdAndSkill(
                        vinculo.getTreinador().getId(), vinculo.getTemporadaId(), Skill.LIDERANCA)
                .map(skill -> skill.getValor())
                .orElse(SKILL_MINIMA);
    }
}
