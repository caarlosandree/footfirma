---
trigger: always_on
globs: "src/**/*.java"
description: Checklist que o agente executa antes de encerrar qualquer tarefa no backend FootFirma.
---

# Checklist Final — Backend FootFirma

Percorra este checklist **antes de dizer que a tarefa está pronta**. Item que não se
aplica é declarado como não aplicável, não ignorado em silêncio.

## 1. Compila

- [ ] `./gradlew compileJava` passa sem erro
- [ ] Nenhum warning novo do MapStruct (lembre: `unmappedTargetPolicy=ERROR` quebra o build)
- [ ] Sem imports não usados, código comentado ou `System.out.println`

## 2. Arquitetura e módulos

- [ ] Código novo está dentro do módulo de domínio correto, em `br.com.api.footfirma.<feature>`
- [ ] Nenhum pacote técnico global foi criado na raiz (`controllers/`, `services/`, `entities/`)
- [ ] Nenhum módulo importa tipo de subpacote interno de outro módulo
- [ ] Comunicação entre módulos usa evento de domínio ou interface pública do módulo
- [ ] `ModularidadeTest` continua verde (se ainda não existe e há mais de um módulo, crie)

## 3. Camadas

- [ ] Controller é fino: sem regra de negócio, sem repository, sem `@Transactional`
- [ ] Service concentra o caso de uso e abre a transação
- [ ] Entidade JPA não aparece em nenhuma assinatura de controller nem dentro de DTO
- [ ] Conversão Entity ↔ DTO passa por MapStruct

## 4. API

- [ ] Rota sob `/api/v1/`, recurso no plural
- [ ] Status HTTP corretos (201 na criação, 204 sem corpo, 422 em falha de validação)
- [ ] DTO de entrada tem Bean Validation e o controller usa `@Valid`
- [ ] Listagem é paginada com `Pageable` / `Page<T>`
- [ ] Erro tratado no `@RestControllerAdvice`, resposta em `ProblemDetail`
- [ ] `@Tag` e `@Operation` presentes; Swagger reflete a mudança

## 5. Banco

- [ ] Mudança de schema veio com migration `V{n}__descricao.sql` no mesmo commit
- [ ] Nenhuma migration já aplicada foi editada
- [ ] Constraints e índices acompanham as regras novas
- [ ] `@ManyToOne` / `@OneToOne` são `LAZY`
- [ ] Sem N+1 introduzido (use `@EntityGraph` ou `join fetch`)
- [ ] Nenhuma query montada por concatenação de string
- [ ] Chave nova no Redis tem TTL

## 6. Transações

- [ ] Leitura com `@Transactional(readOnly = true)`, escrita com `@Transactional`
- [ ] Nenhuma chamada HTTP, e-mail ou fila dentro da transação
- [ ] Concorrência tratada onde há disputa pelo mesmo recurso

## 7. Segurança

- [ ] Endpoint novo tem autorização explícita — nada caiu em `permitAll()` por descuido
- [ ] Recurso de outro usuário é inacessível (dono faz parte da query)
- [ ] Nenhum segredo, senha ou token no diff
- [ ] Entradas validadas no servidor
- [ ] Erro devolvido ao cliente não vaza detalhe interno
- [ ] Nada sensível em log

## 8. Testes

- [ ] Regra de negócio nova tem teste unitário
- [ ] Persistência nova tem teste com Testcontainers (nunca H2, nunca mock de repository)
- [ ] `@DataJpaTest` usa `Replace.NONE`
- [ ] Testes usam `@MockitoBean`, não `@MockBean`
- [ ] Teste específico da mudança roda verde: `./gradlew test --tests '*NomeDoTest'`
- [ ] Nenhum teste foi enfraquecido ou removido para "passar"

## 9. Documentação

- [ ] Variável de ambiente nova está documentada
- [ ] Decisão arquitetural relevante foi registrada
- [ ] `AGENTS.md` ou `.rules/` atualizados se uma convenção mudou

## 10. Commit

- [ ] Um commit = uma mudança lógica
- [ ] Mensagem em Conventional Commits, em português, sem `Co-authored-by`
- [ ] Nada de arquivo temporário, `.idea/`, `build/` ou log no diff

## Antes do push

- [ ] `./gradlew build` passa (compila + suíte completa, exige Docker)
- [ ] Não há push ou force-push sem pedido explícito do usuário (merge local é livre)

## Registro de fechamento (obrigatório)

Terminar a tarefa inclui **declarar o que foi e o que não foi verificado**. Na
resposta final, sempre:

1. **Arquivos principais alterados** — os que importam, não a lista inteira.
2. **Validações executadas** — comando e resultado real. Falhou? Mostre a saída, não
   resuma.
3. **Validações NÃO executadas e por quê** — "não rodei os testes de integração
   porque o Docker está parado" é informação útil; omitir isso é enganoso.
4. **Alterações preexistentes no worktree** — o que já estava modificado antes de
   você começar (`git status`), para o usuário separar o que é seu do que não é.

Não escreva "tudo certo", "pronto" ou "funcionando" sem ter rodado o comando que
sustenta a frase. Ver `docs/runbooks/validation.md`.

## Módulos relacionados

- `.rules/java-core.md` · `.rules/java-api.md` · `.rules/java-testing.md`
- `.rules/database.md` · `.rules/security.md` · `.rules/commit.md`
- `docs/runbooks/validation.md` — qual validação rodar para cada tipo de mudança
