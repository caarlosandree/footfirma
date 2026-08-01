# Runbook de Validação — Frontend FootFirma

Status: verificado em 2026-07-31

## Fontes

- `package.json` (raiz), `apps/web/package.json`, `packages/ui/package.json`
- `turbo.json`
- `.rules/nextjs-testing.md`
- `.rules/nextjs-checklist.md`

## Regra geral

**Escolha a menor validação que cobre a mudança.** `pnpm build` a cada edição é
lento; nenhum comando é irresponsável. A tabela abaixo é o meio-termo.

O checklist (`.rules/nextjs-checklist.md`) diz *o que revisar*; este runbook diz
*o que executar*.

## Matriz

| Mudança | Validação mínima |
|---|---|
| `docs/`, `.rules/`, comentário | `git diff --check` |
| Classe Tailwind, texto, ajuste visual em componente existente | `pnpm lint` |
| Componente novo, mudança de props, hook | `pnpm lint` + `pnpm typecheck` |
| Server Action, `lib/api/`, schema Zod | `pnpm lint` + `pnpm typecheck` |
| Rota nova, `layout.tsx`, `loading.tsx`, `error.tsx` | acima + `pnpm build` |
| `next.config.ts`, `proxy.ts`, `turbo.json` | acima + `pnpm build` |
| Fronteira servidor/cliente (`'use client'`, `server-only`) | acima + `pnpm build` |
| Export novo em `packages/ui`, dependência, workspace | `pnpm install` + `pnpm build` |
| Qualquer coisa antes do push | `pnpm lint && pnpm typecheck && pnpm build` |

`pnpm build` não é burocracia nos casos marcados: é o único passo que detecta import
de módulo de servidor a partir de código cliente, rota mal formada e quebra de
tipagem gerada pelo Next. `lint` e `typecheck` passam felizes nesses casos.

## Não há testes automatizados

Este monorepo não tem runner configurado (ver `.rules/nextjs-testing.md`). Portanto:

- O portão de qualidade é `lint` + `typecheck` + `build`. Não existe rede de segurança
  além disso.
- **Diga explicitamente no fechamento** que a mudança não tem cobertura de teste. Não
  deixe implícito — quem lê presume que algo verificou o comportamento.

## O que não fazer

- `pnpm dev` para "ver se funciona" — o guard bloqueia. Se precisar mesmo, o usuário
  roda com `! pnpm dev`.
- `npm` ou `yarn` em vez de `pnpm` — quebra o lockfile.
- Ignorar erro de `typecheck` com `@ts-expect-error` sem comentário justificando.

## Registro de fechamento (obrigatório)

Ao terminar qualquer tarefa, declare explicitamente, mesmo que a resposta fique
menos elegante:

1. **Arquivos principais alterados** — os que importam, não a lista inteira.
2. **Validações executadas** — comando e resultado real. Se falhou, mostre a saída.
3. **Validações NÃO executadas e por quê** — inclusive "não há teste automatizado
   cobrindo isto".
4. **Alterações preexistentes no worktree** — o que já estava modificado antes de
   você começar (`git status`). Assim o usuário sabe o que é seu e o que não é.

Nunca escreva "tudo certo" sem ter rodado o comando que sustenta a frase.

## Relacionado

- `.rules/nextjs-checklist.md` — o que revisar antes de encerrar
- `.rules/nextjs-testing.md` — como montar a infraestrutura de teste quando for a hora
- `docs/README.md` — padrão de nota
