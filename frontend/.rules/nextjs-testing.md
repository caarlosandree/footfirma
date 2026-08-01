---
trigger: model_decision
globs: "**/*.{test,spec}.{ts,tsx}"
description: Estratégia de testes do frontend FootFirma — ainda não configurada; define a stack alvo, o que testar e como integrar ao Turborepo.
---

# FootFirma Frontend — Testes

## Situação atual

**Não há infraestrutura de teste neste monorepo.** Não existe runner instalado,
nem script `test` nos `package.json`, nem tarefa `test` no `turbo.json`.

Consequência prática: não escreva um arquivo `.test.tsx` esperando que ele rode.
Antes do primeiro teste, monte o setup descrito abaixo — e trate isso como uma
tarefa própria, não como efeito colateral de outra entrega.

## Stack alvo

| Camada | Ferramenta |
|---|---|
| Unitário e componente | Vitest + Testing Library (`@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom`) |
| Ambiente DOM | `jsdom` |
| Mock de rede | MSW |
| End-to-end | Playwright |

Vitest, não Jest: o monorepo é ESM (`"type": "module"`), usa TypeScript e Tailwind v4
— Vitest exige menos configuração aqui.

## Setup mínimo (quando for a hora)

1. Instale no pacote que vai testar, não na raiz:
   `pnpm add -D vitest @vitejs/plugin-react jsdom @testing-library/react @testing-library/user-event @testing-library/jest-dom -F web`
2. `apps/web/vitest.config.ts` com `environment: "jsdom"` e os aliases `@/*` e
   `@workspace/ui/*` espelhando o `tsconfig.json`.
3. Script `"test": "vitest run"` e `"test:watch": "vitest"` no `package.json` do pacote.
4. Tarefa `test` no `turbo.json` (com `dependsOn: ["^build"]` se necessário), para
   `pnpm test` funcionar na raiz.
5. Playwright em `apps/web/e2e/`, com `webServer` apontando para `pnpm dev`.

Faça o mesmo em `packages/ui` quando houver componente com lógica a testar.

## O que testar

| Vale a pena | Não vale |
|---|---|
| Server Action: validação Zod, rejeição sem sessão, erro da API | que o Next renderiza um `<div>` |
| Hook com lógica de estado | wrapper que só repassa props |
| Componente com ramificação real (estados, variantes, erro) | snapshot de markup inteiro |
| Utilitário de formatação, cálculo, parsing | biblioteca de terceiros |
| Fluxo crítico ponta a ponta (login, criar partida, inscrever) | toda combinação de props |

Server Components assíncronos não são bem cobertos por teste de unidade — o retorno
esperado é do E2E. Extraia a lógica para função pura em `_lib/` e teste a função.

## Padrões

- Arquivo ao lado do alvo: `partida-card.tsx` → `partida-card.test.tsx`.
- Descrição em português: `it("desabilita o botão quando não há vagas")`.
- Consulte por papel e texto acessível (`getByRole`, `getByLabelText`), nunca por
  classe CSS ou `data-testid` — se precisou de `testid`, provavelmente falta
  acessibilidade no componente.
- Interação com `userEvent`, não `fireEvent`.
- Mock só a fronteira (rede, via MSW). Mockar o próprio componente testado invalida o teste.
- Um comportamento por teste; sem dependência de ordem entre testes.

```tsx
it("desabilita a inscrição quando a partida está lotada", async () => {
  render(<PartidaCard partida={partidaLotada} />)

  expect(screen.getByRole("button", { name: /inscrever/i })).toBeDisabled()
})
```

## E2E com Playwright

- Cubra só fluxos críticos de negócio; suíte E2E grande é lenta e frágil.
- Seletores por papel e texto visível.
- Sem `waitForTimeout` — use as auto-waits do Playwright ou `expect(...).toBeVisible()`.
- Cada teste prepara o próprio estado e não depende do anterior.

## Enquanto não houver testes

O portão de qualidade do frontend é `pnpm lint` + `pnpm typecheck` + `pnpm build`.
Rode os três antes de considerar uma entrega pronta e diga explicitamente no resumo
que a mudança não tem cobertura automatizada — não deixe implícito.

## Módulos relacionados

- `.rules/nextjs-core.md` · `.rules/nextjs-ui.md` · `.rules/nextjs-checklist.md`
