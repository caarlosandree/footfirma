<!-- BEGIN:nextjs-agent-rules -->
# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` before writing any code. Heed deprecation notices.
<!-- END:nextjs-agent-rules -->

# FootFirma — Frontend

Monorepo Turborepo + pnpm com o app web em Next.js 16 (App Router, React 19) e o
design system compartilhado em `packages/ui` (shadcn/ui estilo `base-nova` sobre
`@base-ui/react`, Tailwind v4).

Este arquivo é o índice. As regras completas estão em `.rules/` — **leia o arquivo
relevante antes de escrever código**, não trabalhe só por este resumo.

## Regras

| Arquivo | Leia antes de |
|---|---|
| `.rules/nextjs-core.md` | qualquer `.ts`/`.tsx` — arquitetura, organização por feature, Server Components, dados, cache, TypeScript |
| `.rules/nextjs-ui.md` | mexer em componente, estilo, tema ou acessibilidade |
| `.rules/nextjs-testing.md` | escrever teste (a infraestrutura ainda não existe) |
| `.rules/security.md` | Server Action, segredo, autenticação ou dado do usuário |
| `.rules/nextjs-checklist.md` | **encerrar qualquer tarefa** (obrigatório) |
| `.rules/commit.md` | fazer commit |
| `docs/runbooks/validation.md` | decidir **qual** validação rodar para a mudança em mãos |

## Documentação

`.rules/` diz **como trabalhar** no repositório; `docs/` diz **o que o sistema é**.
Toda nota em `docs/` segue o padrão obrigatório de `docs/README.md`:
`Status: verificado em AAAA-MM-DD`, `## Fontes` com caminhos reais e um comando de
validação. Nota sem fonte verificável não entra. Não resuma a documentação do Next
aqui — ela já está versionada em `node_modules/next/dist/docs/`.

## Guardrails automáticos

`.claude/settings.json` registra um hook `PreToolUse` (`.claude/hooks/guard.mjs`) que
**bloqueia de verdade**, não apenas avisa:

- subir serviços (`pnpm dev`, `next dev`, `turbo dev`, `docker compose up`)
- `git push` e force-push — **só o que publica no remote**; operação local é livre
- commitar segredo (PAT, chave de API, private key, URL de banco com senha, JWT)

Bloqueio não é sugestão de contornar por outro caminho. Se o usuário realmente quer o
comando, ele executa na própria sessão com `! <comando>`.

## Comandos

```bash
pnpm lint         # ESLint 9 flat config
pnpm typecheck    # tsc --noEmit em todos os pacotes
pnpm build        # turbo build
pnpm format       # Prettier (sem ponto e vírgula, aspas duplas, 80 colunas)
pnpm dev          # sobe apps/web em :3000 (só com pedido explícito)

pnpm dlx shadcn@latest add <componente> -c apps/web   # gera em packages/ui
```

## Estrutura

```
apps/web/
├── app/                    # App Router — feature por rota, com _components/ e _lib/
├── components/             # compartilhados entre features deste app
├── hooks/  lib/
└── next.config.ts          # transpilePackages: ["@workspace/ui"]

packages/
├── ui/src/                 # components/ hooks/ lib/ styles/globals.css (tokens Tailwind v4)
├── eslint-config/
└── typescript-config/      # strict + noUncheckedIndexedAccess
```

O projeto está em estágio inicial: existem `app/layout.tsx`, `app/page.tsx`,
`components/theme-provider.tsx` e o `Button` do design system. A primeira feature
real ainda será criada — siga `.rules/nextjs-core.md` ao criá-la.

## O que nunca fazer neste repositório

- Escrever `middleware.ts` — nesta versão é `proxy.ts`
- Tratar `params`/`searchParams` como síncronos
- Criar `tailwind.config.js` — Tailwind v4 é configurado por CSS em `globals.css`
- Usar cor crua (hex, rgb, `style` inline) em vez dos tokens do tema
- Marcar `'use client'` no topo da página para "facilitar"
- Buscar dado inicial com `useEffect` em vez de Server Component
- Colocar componente de domínio em `packages/ui`
- Importar por caminho relativo profundo em vez de `@workspace/ui/*` / `@/*`
- Usar `any`, ou `!` para calar o `noUncheckedIndexedAccess`
- Dar prefixo `NEXT_PUBLIC_` a segredo, ou importar módulo com segredo em arquivo cliente
- Rodar `npm`/`yarn` — o gerenciador é `pnpm`
- Subir servidor (`pnpm dev`) sem pedido explícito
- `git push` / force-push sem pedido explícito (`merge` local é livre)
- Adicionar `Co-authored-by:` em mensagem de commit

## Onde este repositório se encaixa

O backend Spring Boot vive em `../backend/footfirma`, no **mesmo repositório git** — a
raiz é um monorepo, não um agrupador de repositórios separados. Ele expõe a API em
`/api/v1/` (Swagger em `http://localhost:8080/swagger-ui.html`).
Tipos de request e response devem espelhar esse contrato real, validados com Zod no
boundary. Índice do sistema: `../AGENTS.md`.
