package br.com.api.footfirma.avaliacao.internal;

import br.com.api.footfirma.avaliacao.domain.JogadorOverall;
import br.com.api.footfirma.avaliacao.domain.PerfilAvaliacao;
import br.com.api.footfirma.avaliacao.repository.JogadorOverallRepository;
import br.com.api.footfirma.jogador.dto.JogadorComAtributos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Uma transação por lote, não uma de dez mil linhas. Bean separado porque
 * {@code @Transactional} só vale em chamada que passa pelo proxy do Spring.
 */
@Component
@RequiredArgsConstructor
class MaterializadorDeLote {

    private final JogadorOverallRepository jogadorOverallRepository;

    @Transactional
    int gravarLote(List<JogadorComAtributos> lote, Long temporadaId, List<PerfilAvaliacao> perfis) {
        var ids = lote.stream().map(JogadorComAtributos::jogadorId).toList();
        // Uma consulta para o lote inteiro: sem isso seriam nove selects por jogador.
        var existentes = jogadorOverallRepository
                .findByTemporadaIdAndJogadorIdIn(temporadaId, ids).stream()
                .collect(Collectors.toMap(
                        registro -> chave(registro.getJogadorId(), registro.getPosicaoId()),
                        Function.identity()));

        var gravadas = 0;
        for (var jogador : lote) {
            for (var perfil : perfis) {
                var overall = CalculadoraDeOverall.calcular(jogador.atributos(), perfil.getPesos());
                var registro = existentes.getOrDefault(
                        chave(jogador.jogadorId(), perfil.getPosicaoId()),
                        new JogadorOverall(jogador.jogadorId(), temporadaId, perfil.getPosicaoId()));
                registro.setPerfilVersao(perfil.getVersao());
                registro.setOverall(overall);
                registro.setCalculadoEm(OffsetDateTime.now());
                jogadorOverallRepository.save(registro);
                gravadas++;
            }
        }
        return gravadas;
    }

    private static Map.Entry<Long, Long> chave(Long jogadorId, Long posicaoId) {
        return Map.entry(jogadorId, posicaoId);
    }
}
