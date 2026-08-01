---
description: Executa o checklist final do backend FootFirma antes de encerrar a tarefa
---

Você vai fechar a tarefa atual no backend FootFirma. Execute nesta ordem:

1. Leia `.rules/java-checklist.md` por inteiro.
2. Rode `./gradlew compileJava` e reporte o resultado real (não presuma sucesso).
3. Revise o diff atual (`git diff` e `git diff --staged`) contra cada seção do
   checklist.
4. Se houver teste tocando o que mudou, rode só ele:
   `./gradlew test --tests '*NomeDoTest'`. Não rode a suíte completa a menos que o
   usuário peça ou esteja prestes a fazer push.
5. Apresente o resultado como uma lista curta, marcando cada seção do checklist como
   ✅ atendida, ⚠️ com pendência ou — não aplicável. Para cada ⚠️, diga o arquivo e a
   linha e proponha a correção.

Não corrija nada automaticamente antes de mostrar o resultado, a menos que o usuário
tenha pedido. Se algum comando falhar, mostre a saída de erro em vez de resumir.
