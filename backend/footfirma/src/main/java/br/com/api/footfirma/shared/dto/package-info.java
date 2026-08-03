// shared é um módulo OPEN, então este subpacote já seria visível sem a anotação.
// Ela entra assim mesmo para declarar a intenção: no dia em que shared virar um
// módulo fechado, cinco módulos quebrariam de uma vez sem esta linha.
@org.springframework.modulith.NamedInterface("dto")
package br.com.api.footfirma.shared.dto;
