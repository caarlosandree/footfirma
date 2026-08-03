package br.com.api.footfirma.treinador.internal;

import br.com.api.footfirma.shared.exception.DistribuicaoInvalidaException;
import br.com.api.footfirma.shared.exception.RecursoNaoEncontradoException;
import br.com.api.footfirma.treinador.TreinadorService;
import br.com.api.footfirma.treinador.domain.Skill;
import br.com.api.footfirma.treinador.domain.Treinador;
import br.com.api.footfirma.treinador.domain.TreinadorSkill;
import br.com.api.footfirma.treinador.dto.DistribuicaoDeSkills;
import br.com.api.footfirma.treinador.dto.NovoTreinador;
import br.com.api.footfirma.treinador.dto.TreinadorDetalhe;
import br.com.api.footfirma.treinador.dto.TreinadorResumo;
import br.com.api.footfirma.treinador.mapper.TreinadorMapper;
import br.com.api.footfirma.treinador.repository.TreinadorRepository;
import br.com.api.footfirma.treinador.repository.TreinadorSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.PONTOS_INICIAIS;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.SKILL_MAXIMA;
import static br.com.api.footfirma.treinador.internal.ConstantesDeTreinador.SKILL_MINIMA;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class TreinadorServiceImpl implements TreinadorService {

    private final TreinadorRepository treinadores;
    private final TreinadorSkillRepository skills;
    private final TreinadorMapper treinadorMapper;

    @Override
    public Optional<TreinadorDetalhe> buscarPorSlug(String slug, long temporadaId) {
        return treinadores.findBySlug(slug)
                .map(treinador -> treinadorMapper.paraDetalhe(treinador,
                        skillsDe(treinador.getId(), temporadaId)));
    }

    @Override
    @Transactional
    public TreinadorResumo criar(NovoTreinador dados) {
        validarDistribuicaoInicial(dados.skills());

        var treinador = treinadores.save(new Treinador(dados.slug(), dados.nomeCompleto(),
                dados.nomeExibicao(), dados.dataNascimento(), dados.paisId(), dados.tipo(),
                semente(dados.slug())));

        dados.skills().pontos().forEach((skill, valor) ->
                skills.save(new TreinadorSkill(treinador, dados.temporadaId(), skill, valor)));

        return treinadorMapper.paraResumo(treinador);
    }

    @Override
    @Transactional
    public TreinadorDetalhe distribuirPontos(long treinadorId, long temporadaId,
                                             DistribuicaoDeSkills ganhos) {
        var treinador = treinadores.findById(treinadorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "treinador " + treinadorId + " não existe"));
        var atuais = skills.findByTreinadorIdAndTemporadaId(treinadorId, temporadaId);

        validarGanhos(ganhos, treinador.getPontosDisponiveis(), atuais);

        atuais.forEach(linha ->
                linha.setValor(linha.getValor() + ganhos.pontos().getOrDefault(linha.getSkill(), 0)));
        treinador.setPontosDisponiveis(0);

        return treinadorMapper.paraDetalhe(treinador, mapear(atuais));
    }

    /**
     * As seis skills, cada uma entre 1 e 10, somando exatamente 20.
     *
     * <p>A ordem das três checagens importa para a mensagem de erro: mapa incompleto
     * quase sempre também erra a soma, e dizer "faltam skills" ajuda mais que dizer "a
     * soma deu 6".
     */
    private void validarDistribuicaoInicial(DistribuicaoDeSkills distribuicao) {
        if (distribuicao.pontos().size() != Skill.values().length) {
            throw new DistribuicaoInvalidaException(
                    "as " + Skill.values().length + " skills precisam ser informadas");
        }
        distribuicao.pontos().forEach((skill, valor) -> {
            if (valor < SKILL_MINIMA || valor > SKILL_MAXIMA) {
                throw new DistribuicaoInvalidaException(skill + " precisa ficar entre "
                        + SKILL_MINIMA + " e " + SKILL_MAXIMA + ", veio " + valor);
            }
        });
        if (distribuicao.total() != PONTOS_INICIAIS) {
            throw new DistribuicaoInvalidaException("a soma das skills precisa ser "
                    + PONTOS_INICIAIS + ", veio " + distribuicao.total());
        }
    }

    private void validarGanhos(DistribuicaoDeSkills ganhos, int pontosDisponiveis,
                               List<TreinadorSkill> atuais) {
        if (atuais.isEmpty()) {
            throw new DistribuicaoInvalidaException(
                    "não há skills gravadas nesta temporada para receber pontos");
        }
        if (ganhos.pontos().values().stream().anyMatch(ganho -> ganho < 1)) {
            throw new DistribuicaoInvalidaException("cada ganho precisa ser de ao menos 1 ponto");
        }
        if (ganhos.total() != pontosDisponiveis) {
            throw new DistribuicaoInvalidaException("há " + pontosDisponiveis
                    + " pontos a distribuir, e a distribuição gasta " + ganhos.total());
        }

        var valorAtual = mapear(atuais);
        ganhos.pontos().forEach((skill, ganho) -> {
            if (valorAtual.getOrDefault(skill, 0) + ganho > SKILL_MAXIMA) {
                throw new DistribuicaoInvalidaException(
                        skill + " passaria de " + SKILL_MAXIMA + " com este ganho");
            }
        });
    }

    private Map<Skill, Integer> skillsDe(Long treinadorId, long temporadaId) {
        return mapear(skills.findByTreinadorIdAndTemporadaId(treinadorId, temporadaId));
    }

    private static Map<Skill, Integer> mapear(List<TreinadorSkill> linhas) {
        var valores = new EnumMap<Skill, Integer>(Skill.class);
        linhas.forEach(linha -> valores.put(linha.getSkill(), linha.getValor()));
        return valores;
    }

    /**
     * Derivada do slug, como o gerador de mundo faz com o clube. Deixar o cliente escolher
     * abriria a porta para dois treinadores com a mesma semente sorteando igual, e pedir
     * uma semente a quem cria um treinador não significa nada para quem chama.
     */
    private static long semente(String slug) {
        return slug.hashCode();
    }
}
