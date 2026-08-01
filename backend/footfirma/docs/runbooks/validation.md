# Runbook de Validação — Backend FootFirma

Status: verificado em 2026-07-31

## Fontes

- `build.gradle`
- `compose.yaml`
- `src/test/java/br/com/api/footfirma/TestcontainersConfiguration.java`
- `.rules/java-testing.md`
- `.rules/java-checklist.md`

## Regra geral

**Escolha a menor validação que cobre a mudança.** Rodar a suíte inteira a cada
edição desperdiça minutos e Docker; não rodar nada entrega código quebrado. A tabela
abaixo é o meio-termo.

O checklist (`.rules/java-checklist.md`) diz *o que revisar*; este runbook diz
*o que executar*.

## Matriz

| Mudança | Validação mínima |
|---|---|
| Comentário, Javadoc, `docs/`, `.rules/` | `git diff --check` |
| Regra de negócio em service, sem tocar schema | `./gradlew compileJava` + teste unitário do service |
| Controller, DTO, validação, tratador de erro | `./gradlew compileJava` + `--tests '*<Nome>ControllerTest'` |
| Entidade JPA, repository, query, migration | `./gradlew compileJava` + `--tests '*<Nome>RepositoryTest'` (**Docker ligado**) |
| Módulo novo, fronteira entre módulos, evento | acima + `--tests '*ModularidadeTest'` |
| `SecurityFilterChain`, autorização | acima + testes dos endpoints afetados com `@WithMockUser` |
| `build.gradle`, dependência, annotation processor | `./gradlew build` |
| Qualquer coisa antes do push | `./gradlew build` (compila + suíte completa) |

Teste específico:

```bash
./gradlew test --tests '*PartidaServiceTest'
```

## Docker

Tudo que toca persistência depende de Testcontainers, e Testcontainers depende de
Docker rodando. Confira antes:

```bash
docker ps
```

Docker parado **não** autoriza trocar por H2 nem por mock de repository — significa
que o teste não roda agora. Diga isso no fechamento em vez de contornar.

## O que não fazer

- `./gradlew bootRun` para "ver se funciona" — sobe Postgres e Redis, e o guard
  bloqueia. Se precisar mesmo, o usuário roda com `! ./gradlew bootRun`.
- `./gradlew test` a cada salvamento — o custo é alto e o retorno é o mesmo do teste
  específico.
- `./gradlew build --exclude-task test` para "passar mais rápido" — isso é não validar.

## Registro de fechamento (obrigatório)

Ao terminar qualquer tarefa, declare explicitamente, mesmo que a resposta fique
menos elegante:

1. **Arquivos principais alterados** — os que importam, não a lista inteira.
2. **Validações executadas** — comando e resultado real. Se falhou, mostre a saída.
3. **Validações NÃO executadas e por quê** — "não rodei os testes de integração
   porque o Docker está parado" é uma informação útil; silêncio sobre isso é
   enganoso.
4. **Alterações preexistentes no worktree** — o que já estava modificado antes de
   você começar (`git status`). Assim o usuário sabe o que é seu e o que não é.

Nunca escreva "tudo certo" sem ter rodado o comando que sustenta a frase.

## Relacionado

- `.rules/java-testing.md` — como escrever os testes que este runbook manda rodar
- `.rules/java-checklist.md` — o que revisar antes de encerrar
- `docs/README.md` — padrão de nota
