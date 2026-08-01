---
trigger: model_decision
globs: "src/main/java/br/com/api/footfirma/**/web/**/*.java"
description: Contratos HTTP da API FootFirma — controllers, DTOs, validação, ProblemDetail, paginação, OpenAPI e WebSocket.
---

# FootFirma Backend — API REST

Complementa `.rules/java-core.md`. Vale para tudo que está em `<feature>/web/`.

## Versionamento e rotas

- Todo endpoint sob `/api/v1/...`.
- Recurso no **plural**, em português, kebab-case quando composto:
  `/api/v1/partidas`, `/api/v1/tipos-de-quadra`.
- Verbo HTTP carrega a ação; a URL não: `POST /api/v1/partidas`, nunca
  `/api/v1/criarPartida`.
- Sub-recursos por aninhamento raso: `/api/v1/partidas/{id}/inscricoes`.
  Mais de dois níveis é sinal de recurso próprio.
- Mudança incompatível de contrato exige `/api/v2`, não alteração da v1.

## Controller

Fino: valida o contrato, delega ao service, devolve DTO com o status correto.

```java
package br.com.api.footfirma.partida.web;

@RestController
@RequestMapping("/api/v1/partidas")
@RequiredArgsConstructor
@Tag(name = "Partidas", description = "Gestão de partidas")
class PartidaController {

    private final PartidaService partidaService;

    @GetMapping
    @Operation(summary = "Lista partidas paginadas")
    Page<PartidaResumo> listar(@ParameterObject Pageable paginacao) {
        return partidaService.listar(paginacao);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca uma partida pelo id")
    PartidaDetalhe buscar(@PathVariable UUID id) {
        return partidaService.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria uma partida")
    PartidaDetalhe criar(@Valid @RequestBody NovaPartida comando) {
        return partidaService.criar(comando);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelar(@PathVariable UUID id) {
        partidaService.cancelar(id);
    }
}
```

Observe:

- A classe é **package-private** — o controller é detalhe interno do módulo
  (`web/` é subpacote, portanto não é API pública Modulith).
- Sem `@Transactional`.
- Sem `ResponseEntity` quando o status é fixo: use `@ResponseStatus`.
  Reserve `ResponseEntity` para quando o status varia em runtime ou há header custom.
- Sem `try/catch` — erros sobem para o `@RestControllerAdvice`.

## DTOs

- `record` sempre, no pacote `<feature>/dto/`.
- Um DTO por finalidade: `NovaPartida` (POST), `AtualizacaoPartida` (PUT/PATCH),
  `PartidaResumo` (listagem), `PartidaDetalhe` (busca por id).
- **Entidade JPA nunca aparece** em assinatura de controller, nem como parâmetro,
  nem como retorno, nem dentro de outro DTO.
- Conversão via MapStruct (`<feature>/mapper/`), nunca à mão no controller.

```java
public record NovaPartida(
        @NotBlank @Size(max = 120) String titulo,
        @NotNull @Future LocalDateTime inicio,
        @NotNull @Positive Integer vagas,
        @NotNull UUID quadraId
) {}
```

## Validação

- Bean Validation nos DTOs de entrada; `@Valid` no parâmetro do controller.
- Anotações usadas com frequência aqui: `@NotBlank`, `@NotNull`, `@Size`, `@Email`,
  `@Positive`, `@Future`, `@Pattern`.
- Regras que dependem de estado do banco (unicidade, saldo, conflito de horário)
  são **do service**, não de anotação.
- Valide também parâmetros de rota e query quando houver faixa esperada:
  `@Min(1) @RequestParam int pagina`.

## Erros — ProblemDetail (RFC 9457)

Padronize em um `@RestControllerAdvice` em `shared/exception/`.

```java
@RestControllerAdvice
class TratadorDeErros {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ProblemDetail naoEncontrado(RecursoNaoEncontradoException ex) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problema.setTitle("Recurso não encontrado");
        return problema;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalido(MethodArgumentNotValidException ex) {
        var problema = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problema.setTitle("Dados inválidos");
        problema.setProperty("campos", ex.getBindingResult().getFieldErrors().stream()
                .collect(toMap(FieldError::getField, FieldError::getDefaultMessage, (a, b) -> a)));
        return problema;
    }
}
```

- Mensagem para o cliente em português, sem stack trace, sem nome de classe, sem SQL.
- Detalhe técnico vai para o log (`log.error`), não para a resposta.
- Exceções de negócio ficam em `<feature>/` ou `shared/exception/`, herdando de uma
  base do projeto — não use `RuntimeException` crua.

### Status HTTP

| Situação | Status |
|---|---|
| Leitura bem-sucedida | 200 |
| Criação | 201 |
| Sucesso sem corpo (delete, cancelamento) | 204 |
| Payload malformado / parâmetro inválido | 400 |
| Sem autenticação | 401 |
| Autenticado sem permissão | 403 |
| Recurso inexistente | 404 |
| Conflito de estado (duplicidade, concorrência) | 409 |
| Falha de Bean Validation | 422 |
| Rate limit | 429 |
| Erro inesperado | 500 |

Nunca devolva 200 com `{"erro": ...}` no corpo.

## Paginação

- Toda listagem que pode crescer é paginada. Use `Pageable` + `Page<T>` do Spring Data.
- Paginação acontece **no banco**; nunca carregue tudo e corte em memória.
- Ordenação padrão explícita no repository ou via
  `@PageableDefault(size = 20, sort = "criadoEm", direction = DESC)`.
- Anote o parâmetro com `@ParameterObject` para o springdoc documentar corretamente.

## OpenAPI

`OpenApiConfig` já publica o documento. Swagger UI em `/swagger-ui.html`,
JSON em `/v3/api-docs`.

- `@Tag` na classe do controller, `@Operation(summary = ...)` em cada método.
- `@ApiResponse` apenas para respostas não óbvias (409, 422 com formato próprio).
- Descrições em português.
- Endpoint novo sem anotação de documentação é entrega incompleta.

## WebSocket

`spring-boot-starter-websocket` está no classpath.

- Endpoints STOMP/WebSocket ficam em `<feature>/web/`, com a configuração em `config/`.
- Payloads são DTOs (`record`), nunca entidades.
- Autentique o handshake: canal WebSocket não é exceção às regras de autorização.
- Mensagens devem ser pequenas; dados volumosos vão por REST e o WebSocket notifica.

## Módulos relacionados

- `.rules/java-core.md` — arquitetura, módulos, transações
- `.rules/security.md` — autenticação e autorização dos endpoints
- `.rules/java-testing.md` — testes de controller
- `.rules/java-checklist.md` — checklist final
