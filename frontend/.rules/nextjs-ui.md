---
trigger: model_decision
globs: "{apps/web/**,packages/ui/**}/*.tsx"
description: Design system do FootFirma — packages/ui, shadcn base-nova sobre Base UI, tokens Tailwind v4, tema, acessibilidade, imagens e links.
---

# FootFirma Frontend — UI

Complementa `.rules/nextjs-core.md`. Vale para todo `.tsx`.

## Onde cada componente mora

| Camada | Local | Contém |
|---|---|---|
| Primitivos | `packages/ui/src/components/` | `Button`, `Dialog`, `Input` — genéricos, sem regra de negócio |
| Compartilhados do app | `apps/web/components/` | usados por 2+ features (ex.: `theme-provider.tsx`) |
| Da feature | `apps/web/app/<feature>/_components/` | tudo que só aquela rota usa |

Componente de domínio (`PartidaCard`, `InscricaoForm`) **não** entra em
`packages/ui`. Lá só vive o que sobreviveria a uma troca de produto.

## Adicionar componente shadcn

```bash
pnpm dlx shadcn@latest add dialog -c apps/web
```

Executado na raiz do monorepo, com `-c apps/web`. O arquivo é gerado em
`packages/ui/src/components/` conforme `apps/web/components.json`.

Configuração vigente (não altere sem motivo): estilo `base-nova`, `rsc: true`,
`baseColor: neutral`, `cssVariables: true`, ícones `lucide`, `utils` apontando para
`@workspace/ui/lib/utils`.

## Padrão de componente do design system

Siga o formato do `button.tsx` existente — ele é a referência:

```tsx
import { Dialog as DialogPrimitive } from "@base-ui/react/dialog"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@workspace/ui/lib/utils"

const cardVariants = cva("rounded-lg border bg-card text-card-foreground", {
  variants: {
    padding: { none: "", sm: "p-3", default: "p-4" },
  },
  defaultVariants: { padding: "default" },
})

function Card({
  className,
  padding,
  ...props
}: React.ComponentProps<"div"> & VariantProps<typeof cardVariants>) {
  return (
    <div
      data-slot="card"
      className={cn(cardVariants({ padding, className }))}
      {...props}
    />
  )
}

export { Card, cardVariants }
```

Pontos obrigatórios:

- Primitivo vem de `@base-ui/react` (este projeto **não** usa Radix).
- Variantes por `cva`, nunca por `if` montando string de classe.
- `cn(...)` sempre por último, com `className` no fim, para o consumidor poder sobrescrever.
- `data-slot="..."` em cada parte estilizável — o estilo `base-nova` depende disso.
- Props derivadas do primitivo (`ButtonPrimitive.Props`) ou de
  `React.ComponentProps<"tag">`; nunca redeclare props manualmente.
- Export **nomeado**, junto com as variantes.
- Componente do design system é Server Component por padrão. Só marque `'use client'`
  se o primitivo exigir.

## Tokens de cor — nunca cor crua

`packages/ui/src/styles/globals.css` define os tokens em OKLCH, em `:root` e `.dark`,
expostos via `@theme inline`.

Disponíveis: `background`, `foreground`, `card`, `popover`, `primary`, `secondary`,
`muted`, `accent`, `destructive`, `border`, `input`, `ring`, `chart-1..5`,
`sidebar*`, além dos raios `radius-sm` … `radius-4xl`.

```tsx
// ✅
<div className="bg-card text-card-foreground border-border rounded-lg border" />

// ❌ nunca
<div className="bg-[#ffffff] text-[rgb(20,20,20)]" />
<div style={{ backgroundColor: "#fff" }} />
```

Cor nova é **token novo** em `globals.css` (nos dois temas), não valor arbitrário no
componente. Toda cor precisa de par claro/escuro.

## Tailwind v4

- **Não existe `tailwind.config.js` neste projeto** e não deve existir. A
  configuração é CSS-first, em `globals.css` (`@theme`, `@custom-variant`, `@source`).
- Ao criar um novo pacote com componentes, acrescente a linha `@source` correspondente.
- Classe utilitária direta no JSX. Só extraia para `cva` quando houver variantes reais.
- Ordenação de classes é responsabilidade do `prettier-plugin-tailwindcss` — rode
  `pnpm format`.

## Tema claro/escuro

`ThemeProvider` (`apps/web/components/theme-provider.tsx`) usa `next-themes` com
`attribute="class"`, `defaultTheme="system"` e um atalho de teclado (tecla `d`) para
alternar.

- `suppressHydrationWarning` no `<html>` do root layout é intencional — não remova.
- Todo componente novo precisa funcionar nos dois temas. Verifique com a tecla `d`.
- Nunca condicione estilo lendo o tema em JS quando `dark:` resolve em CSS —
  `useTheme` força `'use client'` e causa flash.

## Acessibilidade

- Elemento semântico primeiro: `<button>` para ação, `<a>`/`Link` para navegação.
  `<div onClick>` é bug de acessibilidade.
- Todo input tem `<label htmlFor>` associado. `placeholder` não é label.
- Ícone sozinho em botão precisa de `aria-label`.
- Foco visível nunca é removido — os primitivos já trazem `focus-visible:ring`.
  Se sobrescrever, preserve.
- Estado de erro em campo: `aria-invalid` + mensagem associada por `aria-describedby`.
- Contraste mínimo 4.5:1 para texto; use os pares `*-foreground` dos tokens.
- Modal, menu e popover: use o primitivo do Base UI, que já resolve foco preso,
  `Esc` e leitura por leitor de tela. Não recrie na mão.

## Responsividade

- Mobile-first: estilo base é mobile, breakpoints adicionam (`md:`, `lg:`).
- Fluxos principais precisam funcionar em 375px de largura.
- Alvo de toque mínimo de 44px em mobile — cuidado com `size="xs"` e `size="sm"` do
  `Button` em interface móvel.
- Tabela larga vai em contêiner com `overflow-x-auto`; a página nunca rola na horizontal.

## Imagens, links e fontes

- `next/image` sempre, com `alt` descritivo (`alt=""` só para imagem decorativa) e
  `width`/`height` ou `fill` + contêiner posicionado. Nada de `<img>`.
- `next/link` para navegação interna. `<a>` só para link externo, com
  `rel="noopener noreferrer"` quando `target="_blank"`.
- Fontes vêm de `next/font/google` no root layout (`Geist` e `Geist_Mono`, expostas
  como `--font-sans` e `--font-mono`). Não adicione `<link>` para Google Fonts.

## Estados da interface

Toda tela que carrega dado trata os quatro: carregando, erro, vazio e sucesso.

- Carregando: `loading.tsx` no segmento ou `<Suspense>` com skeleton que tenha a
  forma do conteúdo real.
- Erro: `error.tsx` no segmento, com ação de tentar de novo.
- Vazio: mensagem que explique o que fazer, não só "nenhum resultado".

## Módulos relacionados

- `.rules/nextjs-core.md` — arquitetura, Server Components, dados
- `.rules/security.md` — XSS e `dangerouslySetInnerHTML`
- `.rules/nextjs-checklist.md` — checklist final
