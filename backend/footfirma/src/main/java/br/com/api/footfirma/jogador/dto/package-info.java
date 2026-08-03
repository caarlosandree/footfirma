// Os records deste pacote são parte do contrato público do módulo: JogadorService os
// devolve, e avaliacao os consome ao calcular overall. Sem @NamedInterface o Modulith
// trata um subpacote como interno e reprova a dependência.
@org.springframework.modulith.NamedInterface("dto")
package br.com.api.footfirma.jogador.dto;
