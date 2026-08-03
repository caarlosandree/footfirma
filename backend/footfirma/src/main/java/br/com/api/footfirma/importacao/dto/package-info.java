// Records devolvidos por ImportacaoService. Hoje quem os consome é o
// ApplicationRunner do próprio módulo; sem @NamedInterface, um consumidor externo
// futuro seria reprovado pelo ModularidadeTest sem explicação óbvia.
@org.springframework.modulith.NamedInterface("dto")
package br.com.api.footfirma.importacao.dto;
