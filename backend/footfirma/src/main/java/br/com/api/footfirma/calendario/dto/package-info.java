// Os records e enums deste pacote são o contrato do módulo: CalendarioService os devolve
// e o módulo partida vai consumi-los. Sem @NamedInterface o Modulith trata o subpacote
// como interno — é o que hoje impede treinador.dto de atravessar a fronteira.
@org.springframework.modulith.NamedInterface("dto")
package br.com.api.footfirma.calendario.dto;
