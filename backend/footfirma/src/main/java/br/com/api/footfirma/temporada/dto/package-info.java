// Os records deste pacote são parte do contrato público do módulo: TemporadaService
// os devolve, e competicao os consome. Sem @NamedInterface o Modulith trata um
// subpacote como interno e reprova a dependência.
@org.springframework.modulith.NamedInterface("dto")
package br.com.api.footfirma.temporada.dto;
