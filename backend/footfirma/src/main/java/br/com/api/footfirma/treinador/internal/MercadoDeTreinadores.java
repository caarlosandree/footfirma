package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.treinador.PropostaEnviada;
import br.com.api.footfirma.treinador.TreinadorService;
import br.com.api.footfirma.treinador.domain.Proposta;
import br.com.api.footfirma.treinador.domain.TipoTreinador;
import br.com.api.footfirma.treinador.domain.Treinador;
import br.com.api.footfirma.treinador.dto.DistribuicaoDeSkills;
import br.com.api.footfirma.treinador.dto.NovoTreinador;
import br.com.api.footfirma.treinador.internal.FabricaDeTreinadorDeIa.TreinadorDeIa;
import br.com.api.footfirma.treinador.repository.PropostaRepository;
import br.com.api.footfirma.treinador.repository.TreinadorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * O lado do mercado que escreve: escolhe a quem o clube vai oferecer a vaga e grava a
 * proposta.
 *
 * <p>As reputações chegam por parâmetro, congeladas em {@link VagaAberta}. É o que evita
 * que o módulo consulte {@code clube} para escrever — e o que faz a moral de assinatura
 * ser calculada contra o clube que fez o convite, não contra o que ele virou depois.
 */
@Component
@RequiredArgsConstructor
class MercadoDeTreinadores {

    private static final int TENTATIVAS_DE_SLUG = 50;

    private final TreinadorRepository treinadores;
    private final PropostaRepository propostas;
    private final TreinadorService treinadorService;
    private final ApplicationEventPublisher eventos;

    /**
     * Oferece a vaga ao candidato livre mais reputado dentro da faixa.
     *
     * <p>Se não houver nenhum, gera um treinador de IA à altura do clube: nenhum clube
     * pode ficar vago, ou o mundo trava na primeira rodada.
     */
    Proposta abrirVaga(VagaAberta vaga) {
        var escolhido = GeradorDePropostas
                .candidatos(vaga.reputacaoDoClube(), treinadores.buscarLivres())
                .stream()
                .findFirst()
                .orElseGet(() -> gerarTreinadorDeIa(vaga));

        var proposta = propostas.save(new Proposta(vaga.clubeId(), escolhido, vaga.temporadaId(),
                vaga.metaPosicao(), vaga.reputacaoDoClube(), vaga.expiraEm()));

        eventos.publishEvent(new PropostaEnviada(proposta.getId(), vaga.clubeId(),
                escolhido.getId(), vaga.metaPosicao()));
        return proposta;
    }

    private Treinador gerarTreinadorDeIa(VagaAberta vaga) {
        var gerado = inedito(vaga.semente());

        // Passa pelo serviço, e não pelo repository, porque a soma das seis skills é
        // invariante de agregado: um segundo caminho de escrita seria um segundo lugar
        // onde ela pode ser violada.
        var resumo = treinadorService.criar(new NovoTreinador(gerado.slug(),
                gerado.nomeCompleto(), gerado.nomeExibicao(), gerado.dataNascimento(),
                vaga.paisId(), TipoTreinador.IA, vaga.temporadaId(),
                new DistribuicaoDeSkills(gerado.skills())));

        var treinador = treinadores.findById(resumo.id()).orElseThrow();
        // Nasce à altura da vaga: reputação neutra num clube de 95 deixaria o recém-criado
        // fora da própria faixa que o trouxe ao mundo.
        treinador.setReputacao(vaga.reputacaoDoClube());
        return treinador;
    }

    /** A semente já entra no slug; o laço cobre o caso raro de dois mundos convergirem. */
    private TreinadorDeIa inedito(long semente) {
        for (var tentativa = 0; tentativa < TENTATIVAS_DE_SLUG; tentativa++) {
            var gerado = FabricaDeTreinadorDeIa.gerar(semente + tentativa);
            if (!treinadores.existsBySlug(gerado.slug())) {
                return gerado;
            }
        }
        throw new IllegalStateException(
                "não foi possível gerar slug inédito de treinador de IA para a semente " + semente);
    }

    /**
     * Tudo que o mercado precisa saber sobre o clube que abriu a vaga. Reputação, país e
     * meta vêm de fora porque são de outros módulos — o treinador não os alcança.
     */
    record VagaAberta(long clubeId, int reputacaoDoClube, long paisId, long temporadaId,
                      int metaPosicao, OffsetDateTime expiraEm) {

        long semente() {
            return clubeId * 1_000_003L + temporadaId;
        }
    }
}
