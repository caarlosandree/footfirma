# Catálogo é read-only e separado do save

Status: verificado em 2026-08-02

## Fontes

- `../../docs/superpowers/specs/2026-08-01-catalogo-futebol-brasileiro-design.md`
- `src/main/java/br/com/api/footfirma/clube/web/ClubeController.java`
- `src/main/java/br/com/api/footfirma/jogador/web/JogadorController.java`
- `src/main/java/br/com/api/footfirma/competicao/web/CompeticaoController.java`
- `src/main/resources/db/migration/V8__cria_jogador_atributo.sql`
- `src/main/resources/db/migration/V6__cria_jogador.sql`
- `src/test/java/br/com/api/footfirma/ModularidadeTest.java`

## Decisão

Os módulos `temporada`, `geografia`, `clube`, `jogador` e `competicao` formam o
catálogo: dado do mundo real, compartilhado entre todas as carreiras. A API REST
os expõe apenas para leitura; a única escrita virá do importador (Plano 3).

O estado de carreira — jogador envelhecido, transferido, lesionado, tabela da
temporada em curso — pertencerá a um módulo `carreira` separado, que referencia o
catálogo sem alterá-lo.

## Por quê

Sem essa fronteira, dois saves se contaminam e não há como recomeçar uma carreira.
Ela é verificada por construção: nenhum controller do catálogo aceita POST, PUT ou
DELETE, e o `ModularidadeTest` reprova qualquer módulo que escreva em tabela alheia.

## Consequências

- Atributos de jogador são versionados por `(jogador_id, temporada_id)`, nunca
  sobrescritos — garantido pela constraint `uq_jogador_atributo`.
- Perfis de peso do overall (Plano 2) são versionados: rebalancear cria versão nova
  em vez de alterar a existente, para não mudar o equilíbrio de carreiras em curso.
- `jogador.chave_natural` com `unique` garante que reimportar não duplique.

## Como validar

```bash
./gradlew test --tests '*ModularidadeTest'

# Não deve imprimir nada: nenhum endpoint de escrita no catálogo
grep -rn "PostMapping\|PutMapping\|PatchMapping\|DeleteMapping" \
    src/main/java/br/com/api/footfirma/*/web/
```
