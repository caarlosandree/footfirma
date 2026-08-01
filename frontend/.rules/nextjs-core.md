---
trigger: always_on
globs: "**/*.{ts,tsx}"
description: Arquitetura do monorepo FootFirma frontend — Turborepo, Next.js 16 App Router, organização por feature, Server Components, dados, cache e TypeScript.
---

# FootFirma Frontend — Core

Monorepo Turborepo com o app web Next.js 16 e o design system compartilhado.

## Antes de escrever qualquer código Next.js

Este **não** é o Next.js que você conhece. O projeto está no Next 16, que traz
mudanças que o seu treinamento provavelmente não cobre. Os docs correspondentes à
versão instalada estão no próprio repositório:

```
node_modules/next/dist/docs/01-app/
├── 01-getting-started/    # rotas, dados, cache, mutações, route handlers, proxy
├── 02-guides/             # autenticação, formulários, ISR, instrumentação, MCP
└── 03-api-reference/      # assinatura exata de cada API
```

Leia o arquivo relevante **antes** de escrever, não depois de errar. Diferenças que
mais pegam:

| Você provavelmente lembra | Na versão 16 |
|---|---|
| `middleware.ts` | **`proxy.ts`** na raiz do app, exportando `proxy` |
| `revalidate` / `fetch` com `next.revalidate` como modelo central | `use cache` + `cacheLife` + `cacheTag` (Cache Components) |
| `revalidatePath` para tudo | `refresh()` para atualizar o router, `updateTag`/`revalidateTag` para dados marcados |
| `params` / `searchParams` síncronos | **assíncronos** — precisam de `await` |

## Stack real

**As versões exatas vivem nos `package.json` e no `pnpm-lock.yaml` — são eles a fonte
da verdade.** Este documento cita apenas a major, porque dependência sobe a qualquer
momento. Precisa da versão precisa de algo? Leia o `package.json` do pacote; não
presuma pelo que está escrito aqui.

| Item | Valor |
|---|---|
| Monorepo | Turborepo 2 + pnpm 10 (workspaces) |
| App | `apps/web` — Next.js 16, App Router |
| Design system | `packages/ui` (`@workspace/ui`) |
| Configs compartilhadas | `packages/eslint-config`, `packages/typescript-config` |
| React | 19 |
| Estilo | Tailwind CSS v4 (sem `tailwind.config`; tokens em `packages/ui/src/styles/globals.css`) |
| Componentes | shadcn/ui estilo `base-nova` sobre `@base-ui/react` |
| Ícones | `lucide-react` |
| Tema | `next-themes` (`ThemeProvider` em `apps/web/components/theme-provider.tsx`) |
| Validação | Zod 4 |
| Node | >= 20 |

**TypeScript:** o alvo do time é **TypeScript 6**. Os `package.json` ainda declaram
`"typescript": "^5"` — ao migrar, bumpe os quatro pacotes de uma vez (raiz,
`apps/web`, `packages/ui`, `packages/eslint-config`) e rode `pnpm typecheck` no
monorepo inteiro.

## Comandos

Sempre a partir de `frontend/`:

```bash
pnpm dev          # turbo dev — sobe apps/web em :3000 (só com pedido explícito)
pnpm build        # turbo build
pnpm lint         # turbo lint (ESLint 9 flat config)
pnpm typecheck    # turbo typecheck (tsc --noEmit em todos os pacotes)
pnpm format       # turbo format (Prettier)
```

Adicionar componente shadcn — **sempre pela raiz do monorepo**, com `-c apps/web`:

```bash
pnpm dlx shadcn@latest add dialog -c apps/web
```

O componente cai em `packages/ui/src/components/`, não em `apps/web`.

## Organização: feature primeiro

Cada feature vive junto da rota que a usa. Nada de pastas técnicas globais
(`components/` gigante, `services/`, `types/` guarda-tudo).

```
apps/web/
├── app/
│   ├── layout.tsx                 # root layout: fontes, ThemeProvider
│   ├── page.tsx
│   ├── (auth)/                    # route group — não aparece na URL
│   │   └── login/
│   │       ├── page.tsx
│   │       ├── _components/       # pasta privada — nunca vira rota
│   │       ├── _lib/              # queries, tipos e helpers da feature
│   │       └── actions.ts         # 'use server'
│   └── partidas/
│       ├── page.tsx
│       ├── loading.tsx
│       ├── error.tsx
│       ├── [id]/page.tsx
│       ├── _components/
│       ├── _lib/
│       └── actions.ts
├── components/                    # só o que é usado por mais de uma feature deste app
├── hooks/                         # hooks compartilhados do app
├── lib/                           # api client, env, utilitários do app
└── proxy.ts                       # (quando necessário) — não "middleware.ts"

packages/ui/src/
├── components/                    # primitivos do design system (shadcn)
├── hooks/
├── lib/utils.ts                   # cn()
└── styles/globals.css             # tokens Tailwind v4
```

Regra de promoção, nesta ordem:

1. Nasce em `app/<feature>/_components/`.
2. Usado por outra feature do mesmo app → sobe para `apps/web/components/`.
3. É primitivo genérico, sem regra de negócio → vai para `packages/ui`.

Nunca comece pelo passo 3 "porque pode ser reaproveitado".

## Nomenclatura

- Arquivos e pastas: `kebab-case` (`partida-card.tsx`, `_lib/buscar-partidas.ts`).
- Componentes: `PascalCase`, exportação nomeada. Só `page.tsx`, `layout.tsx`,
  `error.tsx`, `loading.tsx` e similares usam `export default` (exigência do framework).
- Hooks: prefixo `use` (`use-inscricao.ts` → `useInscricao`).
- Server Actions: verbo no infinitivo (`criarPartida`, `cancelarInscricao`).
- Tipos e interfaces: `PascalCase`, sem prefixo `I`.
- Booleanos: `isCarregando`, `temVagas`, `podeInscrever`.

## Server Components por padrão

`'use client'` é exceção, escrita o mais fundo possível na árvore.

Justificativas válidas: `useState`/`useReducer`, `useEffect`, handler de evento,
API de browser (`window`, `localStorage`), hook de biblioteca cliente
(`useTheme` do next-themes).

Não são justificativas: "vai que precisa depois", "o componente pai é client",
"é mais simples".

```tsx
// ✅ Server Component busca; ilha client recebe por prop
export default async function PartidasPage() {
  const partidas = await buscarPartidas()
  return <ListaDePartidas partidas={partidas} />
}

// _components/filtro-partidas.tsx — só o filtro é client
"use client"
export function FiltroPartidas({ onChange }: FiltroPartidasProps) { /* ... */ }
```

- Server Component pode renderizar Client Component; o inverso só via `children`
  ou props.
- Props que cruzam a fronteira precisam ser serializáveis. Função só se for Server Action.
- Nunca importe módulo com segredo (token, URL interna) em arquivo `'use client'`.

## Dados

- Busque no Server Component, direto no corpo `async`. Não use `useEffect` + `fetch`
  para carga inicial.
- Chamadas à API do backend ficam em `lib/api/` ou no `_lib/` da feature — nunca
  `fetch` cru espalhado em componente.
- Todo dado que entra é validado com Zod no boundary. A resposta da API é `unknown`
  até o `parse`.
- Trate os quatro estados: carregando (`loading.tsx` ou `<Suspense>`), erro
  (`error.tsx`), vazio e sucesso.
- Dado lento vai dentro de `<Suspense>` com fallback, para o shell renderizar na hora.

```tsx
export default function PartidasPage() {
  return (
    <>
      <h1>Partidas</h1>
      <Suspense fallback={<PartidasSkeleton />}>
        <ListaDePartidas />
      </Suspense>
    </>
  )
}
```

## Cache

`cacheComponents` **ainda não está habilitado** em `apps/web/next.config.ts`.
Recomendado habilitar — é o modelo de renderização atual do framework e alinha o
projeto com a documentação instalada:

```ts
const nextConfig: NextConfig = {
  transpilePackages: ["@workspace/ui"],
  cacheComponents: true,
}
```

Com Cache Components ligado:

- `'use cache'` no topo da função para cachear dado ou UI; `cacheLife('hours')`
  define a janela; `cacheTag('partidas')` marca para invalidação seletiva.
- Argumentos e valores capturados entram na chave de cache.
- Componente que lê `cookies()`, `headers()`, `searchParams` ou `params` **não** pode
  ser cacheado — envolva em `<Suspense>`.
- Ler `docs/01-app/01-getting-started/08-caching.md` antes de aplicar.

Enquanto não estiver habilitado, siga
`docs/01-app/02-guides/caching-without-cache-components.md` — e não misture os dois
modelos no mesmo código.

## Mutações

Server Actions, não route handler criado só para o próprio formulário.

```ts
// app/partidas/actions.ts
"use server"

import { refresh } from "next/cache"
import { z } from "zod"

const esquemaNovaPartida = z.object({
  titulo: z.string().min(1).max(120),
  inicio: z.iso.datetime(),
  vagas: z.number().int().positive(),
})

export async function criarPartida(_estado: EstadoFormulario, dados: FormData) {
  const resultado = esquemaNovaPartida.safeParse(Object.fromEntries(dados))
  if (!resultado.success) {
    return { erros: z.treeifyError(resultado.error) }
  }

  // valide a sessão aqui — Server Action é um endpoint público
  await api.post("/api/v1/partidas", resultado.data)
  refresh()
  return { erros: null }
}
```

- **Toda Server Action revalida autenticação e autorização por conta própria.** Ela é
  acessível como endpoint HTTP; a checagem feita na página não a protege.
- Valide a entrada com Zod dentro da action, sempre.
- Depois da mutação: `refresh()` para atualizar o router, `updateTag`/`revalidateTag`
  para dados marcados, `redirect()` para navegar. Não são intercambiáveis — veja
  `docs/01-app/01-getting-started/07-mutating-data.md`.
- No cliente, use `useActionState` para erro e `useFormStatus` para estado pendente,
  antes de recorrer a biblioteca de formulário.

## Imports

```tsx
import { Button } from "@workspace/ui/components/button"   // design system
import { cn } from "@workspace/ui/lib/utils"
import { ThemeProvider } from "@/components/theme-provider" // dentro de apps/web
```

- `@workspace/ui/*` para o design system, `@/*` para o próprio app.
- Nunca importe por caminho relativo profundo (`../../../packages/ui/src/...`).
- `packages/ui` expõe apenas o que está em `exports` do seu `package.json`
  (`./components/*`, `./hooks/*`, `./lib/*`, `./globals.css`). Precisa de algo novo
  público? Acrescente ao `exports`, não fure com caminho direto.
- `apps/web` transpila `@workspace/ui` via `transpilePackages` — mantenha isso ao
  adicionar novo pacote interno.

## TypeScript

`strict: true` e `noUncheckedIndexedAccess: true` estão ligados em
`packages/typescript-config/base.json`. Consequências:

- `array[0]` é `T | undefined`. Trate — não silencie com `!`.
- `any` é proibido. Use `unknown` + narrowing, ou tipe de verdade.
- `as` só para casos que o compilador não tem como saber, com comentário do porquê.
- Props de componente sempre tipadas explicitamente.
- Tipos de request/response espelham o contrato real da API v1 do backend; derive de
  schema Zod (`z.infer`) em vez de manter dois lugares.

## Estilo e formatação

Prettier decide formatação: sem ponto e vírgula, aspas duplas, 2 espaços, 80 colunas,
classes Tailwind ordenadas pelo plugin (`cn` e `cva` incluídos). Não discuta
formatação — rode `pnpm format`.

## Variáveis de ambiente

- `NEXT_PUBLIC_` **apenas** para valor que pode ir ao browser. Tudo com esse prefixo
  é público, inclusive no bundle.
- Segredo (token de serviço, chave privada) nunca leva o prefixo e nunca é importado
  em arquivo `'use client'`.
- Valide o ambiente com Zod em `lib/env.ts` e falhe cedo se faltar variável.
- **Atenção:** o `.gitignore` deste repositório ignora `.env*`, o que inclui
  `.env.example`. Para versionar o exemplo, adicione `!.env.example`.

## Comentários

Explique o porquê, não o quê. Comentário que descreve a linha abaixo é ruído; o que
registra uma decisão não óbvia (workaround, restrição do backend, limitação do
framework) vale ouro.

## Restrições operacionais

- **Não rode `pnpm dev`, `pnpm build` ou suba servidor sem pedido explícito.**
- Não instale dependência nova sem necessidade clara; sempre com `pnpm`, nunca
  `npm`/`yarn` (quebra o lockfile).
- Não edite `pnpm-lock.yaml` à mão.
- Não crie `tailwind.config.js` — Tailwind v4 configura por CSS em
  `packages/ui/src/styles/globals.css`.
- Não escreva `middleware.ts`. É `proxy.ts`.
- `git push` / `merge` / force-push só com pedido explícito.

## Módulos relacionados

- `.rules/nextjs-ui.md` — design system, tokens, Tailwind v4, acessibilidade
- `.rules/nextjs-testing.md` — estratégia de testes (ainda não configurada)
- `.rules/security.md` — XSS, segredos, Server Actions, autorização
- `.rules/nextjs-checklist.md` — checklist antes de encerrar a tarefa
- `.rules/commit.md` — padrão de commit
