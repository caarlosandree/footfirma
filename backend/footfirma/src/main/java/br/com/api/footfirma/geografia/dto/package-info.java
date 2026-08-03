// GeografiaService devolve estes records, e importacao os consome para traduzir
// iso_pais e uf em id antes de chamar os demais módulos. Sem @NamedInterface o
// Modulith trata um subpacote como interno e reprova a dependência.
@org.springframework.modulith.NamedInterface("dto")
package br.com.api.footfirma.geografia.dto;
