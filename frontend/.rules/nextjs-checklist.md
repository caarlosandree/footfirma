---
trigger: always_on
globs: "**/*.{ts,tsx}"
description: Checklist que o agente executa antes de encerrar qualquer tarefa no frontend FootFirma.
---

# Checklist Final — Frontend FootFirma

Percorra antes de dizer que a tarefa está pronta. Item que não se aplica é declarado
como não aplicável, não pulado em silêncio.

## 1. Portões automáticos

- [ ] `pnpm lint` passa
- [ ] `pnpm typecheck` passa
- [ ] `pnpm build` passa — obrigatório antes de push, e sempre que mexer em rota,
      `next.config.ts` ou fronteira servidor/cliente
- [ ] Nenhum `console.log` de depuração, código comentado ou `TODO` órfão no diff

## 2. Docs do framework

- [ ] Se a mudança envolve API do Next, o doc correspondente em
      `node_modules/next/dist/docs/` foi consultado
- [ ] Nenhum `middleware.ts` foi criado (é `proxy.ts`)
- [ ] `params` e `searchParams` foram tratados como assíncronos (`await`)
- [ ] Não há mistura entre o modelo de cache antigo e `use cache`

## 3. Organização

- [ ] Código novo está na feature que o usa (`app/<feature>/_components`, `_lib`)
- [ ] Nada subiu para `packages/ui` sem ser primitivo genérico e sem regra de negócio
- [ ] Nomes de arquivo em `kebab-case`; componentes com export nomeado
      (`export default` só em `page`/`layout`/`error`/`loading`)
- [ ] Imports usam `@workspace/ui/*` e `@/*` — nenhum caminho relativo profundo
- [ ] Novo export público de `packages/ui` foi declarado no campo `exports`

## 4. Server / Client

- [ ] `'use client'` só onde há estado, efeito, evento ou API de browser
- [ ] A diretiva está o mais fundo possível na árvore, não no topo da página
- [ ] Props que cruzam a fronteira são serializáveis e não carregam dado sobrando
- [ ] Nenhum módulo com segredo é alcançável a partir de arquivo cliente

## 5. Dados e estados

- [ ] Carga inicial é feita em Server Component, não em `useEffect`
- [ ] Chamada à API está em `lib/api/` ou no `_lib/` da feature
- [ ] Resposta validada com Zod
- [ ] Os quatro estados foram tratados: carregando, erro, vazio, sucesso
- [ ] Conteúdo lento está dentro de `<Suspense>` com fallback
- [ ] Mutação usa Server Action, com `refresh()` / `revalidateTag` / `redirect()`
      escolhido conscientemente

## 6. UI

- [ ] Componente shadcn foi adicionado via `pnpm dlx shadcn@latest add <x> -c apps/web`
- [ ] Nenhuma cor crua (hex, rgb, `style` inline) — só tokens do tema
- [ ] Funciona nos temas claro e escuro
- [ ] Variantes por `cva`, `cn()` ao final permitindo sobrescrita
- [ ] `data-slot` presente nas partes estilizáveis de componente do design system
- [ ] `next/image` com `alt`, `next/link` para navegação interna
- [ ] Nenhum `tailwind.config.js` foi criado

## 7. Acessibilidade

- [ ] Elemento semântico correto (`button` para ação, `a`/`Link` para navegação)
- [ ] Todo input tem label associada; botão só de ícone tem `aria-label`
- [ ] Foco visível preservado
- [ ] Erro de campo com `aria-invalid` + `aria-describedby`
- [ ] Layout testado em 375px

## 8. TypeScript

- [ ] Nenhum `any`; `unknown` + narrowing quando necessário
- [ ] `noUncheckedIndexedAccess` respeitado — sem `!` para calar o compilador
- [ ] Props tipadas explicitamente
- [ ] Tipos derivados de schema Zod (`z.infer`) em vez de duplicados à mão

## 9. Segurança

- [ ] Nenhum segredo com prefixo `NEXT_PUBLIC_`
- [ ] Server Action nova valida sessão, permissão e entrada
- [ ] Sem `dangerouslySetInnerHTML` não sanitizado
- [ ] Nada sensível em `localStorage` ou em log

## 10. Testes

- [ ] Ainda não há runner configurado neste monorepo — se a mudança pede cobertura,
      diga isso explicitamente no resumo em vez de deixar implícito
- [ ] Se montou setup de teste, `pnpm test` funciona a partir da raiz

## 11. Commit

- [ ] Um commit = uma mudança lógica
- [ ] Conventional Commits em português, sem `Co-authored-by`
- [ ] `pnpm-lock.yaml` só mudou se dependência mudou de propósito

## Antes do push

- [ ] `pnpm lint && pnpm typecheck && pnpm build` verdes
- [ ] Sem push, merge ou force-push sem pedido explícito do usuário

## Registro de fechamento (obrigatório)

Terminar a tarefa inclui **declarar o que foi e o que não foi verificado**. Na
resposta final, sempre:

1. **Arquivos principais alterados** — os que importam, não a lista inteira.
2. **Validações executadas** — comando e resultado real. Falhou? Mostre a saída, não
   resuma.
3. **Validações NÃO executadas e por quê** — incluindo, obrigatoriamente, "não há
   teste automatizado cobrindo isto", já que o monorepo não tem runner.
4. **Alterações preexistentes no worktree** — o que já estava modificado antes de
   você começar (`git status`), para o usuário separar o que é seu do que não é.

Não escreva "tudo certo", "pronto" ou "funcionando" sem ter rodado o comando que
sustenta a frase. Ver `docs/runbooks/validation.md`.

## Módulos relacionados

- `.rules/nextjs-core.md` · `.rules/nextjs-ui.md` · `.rules/nextjs-testing.md`
- `.rules/security.md` · `.rules/commit.md`
- `docs/runbooks/validation.md` — qual validação rodar para cada tipo de mudança
