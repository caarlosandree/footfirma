---
description: Executa o checklist final do frontend FootFirma antes de encerrar a tarefa
---

Você vai fechar a tarefa atual no frontend FootFirma. Execute nesta ordem:

1. Leia `.rules/nextjs-checklist.md` por inteiro.
2. Rode `pnpm lint` e `pnpm typecheck` e reporte a saída real (não presuma sucesso).
3. Se a mudança tocou rota, `next.config.ts`, `proxy.ts` ou a fronteira
   servidor/cliente, rode também `pnpm build`.
4. Revise o diff atual (`git diff` e `git diff --staged`) contra cada seção do
   checklist — com atenção especial a: `'use client'` desnecessário, cor crua fora
   dos tokens, `any`, e segredo alcançável pelo cliente.
5. Apresente o resultado como uma lista curta, marcando cada seção como ✅ atendida,
   ⚠️ com pendência ou — não aplicável. Para cada ⚠️, cite arquivo e linha e proponha
   a correção.

Não corrija nada automaticamente antes de mostrar o resultado, a menos que o usuário
tenha pedido. Se algum comando falhar, mostre a saída de erro em vez de resumir.
