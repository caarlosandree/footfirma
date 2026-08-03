package br.com.api.footfirma.avaliacao.domain;

import br.com.api.footfirma.jogador.dto.AtributosJogador;

import java.util.function.Function;

/**
 * As 18 skills que entram no cálculo de overall, cada uma sabendo se extrair do
 * record de atributos. A alternativa — um switch de 18 casos no calculador —
 * permitiria declarar a constante e esquecer de somá-la.
 */
public enum AtributoAvaliavel {

    RITMO(AtributosJogador::ritmo),
    FORCA(AtributosJogador::forca),
    FOLEGO(AtributosJogador::folego),
    SALTO(AtributosJogador::salto),
    AGILIDADE(AtributosJogador::agilidade),
    PASSE(AtributosJogador::passe),
    DRIBLE(AtributosJogador::drible),
    CRUZAMENTO(AtributosJogador::cruzamento),
    FRIEZA(AtributosJogador::frieza),
    FINALIZACAO(AtributosJogador::finalizacao),
    CABECEIO(AtributosJogador::cabeceio),
    FALTA(AtributosJogador::falta),
    PENALTI(AtributosJogador::penalti),
    DESARME(AtributosJogador::desarme),
    MARCACAO(AtributosJogador::marcacao),
    GOL_REFLEXO(AtributosJogador::golReflexo),
    GOL_POSICIONAMENTO(AtributosJogador::golPosicionamento),
    GOL_MANEJO(AtributosJogador::golManejo);

    private final Function<AtributosJogador, Integer> extrator;

    AtributoAvaliavel(Function<AtributosJogador, Integer> extrator) {
        this.extrator = extrator;
    }

    public Integer extrair(AtributosJogador atributos) {
        return extrator.apply(atributos);
    }
}
