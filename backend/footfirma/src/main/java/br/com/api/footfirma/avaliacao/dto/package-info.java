// Records devolvidos por AvaliacaoService. Sem @NamedInterface o Modulith trata o
// subpacote como interno; hoje ninguém consome de fora, mas o controller de web/ e
// o importador do Plano 3 vão.
@org.springframework.modulith.NamedInterface("dto")
package br.com.api.footfirma.avaliacao.dto;
