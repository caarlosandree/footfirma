package br.com.api.footfirma.calendario.internal;

/**
 * Um jogo já disputado, do ponto de vista de quem decide o confronto.
 *
 * <p>Carrega mandante e visitante porque gol fora depende de quem jogava em casa — e o
 * resolvedor é puro, então não pode ir buscar isso em lugar nenhum.
 */
record PlacarDoConfronto(long mandanteId, long visitanteId,
                         int golsMandante, int golsVisitante,
                         Integer prorrogacaoMandante, Integer prorrogacaoVisitante,
                         Integer penaltisMandante, Integer penaltisVisitante) {
}
