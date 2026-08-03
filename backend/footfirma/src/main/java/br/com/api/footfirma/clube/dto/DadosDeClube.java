package br.com.api.footfirma.clube.dto;

/**
 * Entrada de ingestão, com país, estado e estádio já resolvidos em id por quem
 * chama — resolver aqui faria o módulo clube depender de geografia para escrever.
 */
public record DadosDeClube(String slug, String nomeOficial, String nomeCurto, String apelido,
                           Integer anoFundacao, Long paisId, Long estadoId, Long estadioId,
                           String corPrimaria, String corSecundaria,
                           Integer reputacao, Integer forcaFinanceira, Integer qualidadeBase,
                           Long estadoBaseId) {
}
