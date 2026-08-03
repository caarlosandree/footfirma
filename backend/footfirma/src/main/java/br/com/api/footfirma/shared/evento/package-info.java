// shared é um módulo OPEN, então este subpacote já seria visível sem a anotação.
// Ela entra assim mesmo para declarar a intenção, pelo mesmo motivo de shared/dto.
//
// Os eventos daqui são hóspedes: no Modulith o evento pertence ao módulo que o publica,
// e o produtor destes dois — partida — ainda não existe. Quando existir, mover os
// records para lá é um refactor de import, e este pacote some.
@org.springframework.modulith.NamedInterface("evento")
package br.com.api.footfirma.shared.evento;
