---
trigger: always_on
globs: "src/**/*.java"
description: Baseline de segurança do backend FootFirma — Spring Security, autorização por objeto, segredos, CORS, logs e dados pessoais.
---

# FootFirma Backend — Segurança

`spring-boot-starter-security` está no classpath: **todos os endpoints já nascem
protegidos**. Liberar rota é decisão explícita e deliberada, nunca efeito colateral
de um `permitAll()` amplo para "destravar" o desenvolvimento.

## Configuração do Spring Security

- `SecurityFilterChain` como `@Bean` em `config/`, não `WebSecurityConfigurerAdapter`
  (removido).
- Liste as rotas públicas uma a uma. Nada de `anyRequest().permitAll()`.
- Rotas que costumam ser públicas neste projeto: `/v3/api-docs/**`,
  `/swagger-ui/**`, `/swagger-ui.html`, `/actuator/health`.
  **`/actuator/**` inteiro não é público** — expõe métricas, beans e informação de
  ambiente.

```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/api/v1/auth/**").permitAll()
                    .anyRequest().authenticated())
            .csrf(csrf -> csrf.disable())   // válido apenas para API stateless com token no header
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .build();
}
```

`csrf().disable()` só é aceitável porque a API é stateless e autentica por header.
**Se em algum momento a autenticação passar a usar cookie, CSRF volta a ser
obrigatório** — não deixe a linha desabilitada por inércia.

## Autorização por objeto (IDOR/BOLA)

Estar autenticado não é estar autorizado sobre *aquele* recurso. O erro mais comum e
mais grave em API é confiar no id vindo da URL.

```java
// ❌ qualquer usuário autenticado lê a partida de qualquer outro
public PartidaDetalhe buscar(UUID id) {
    return mapper.paraDetalhe(repository.findById(id).orElseThrow());
}

// ✅ o dono é parte da consulta
public PartidaDetalhe buscar(UUID id, UUID usuarioAutenticadoId) {
    return repository.findByIdAndOrganizadorId(id, usuarioAutenticadoId)
            .map(mapper::paraDetalhe)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Partida não encontrada"));
}
```

- A verificação vive no **service**, não no controller.
- Recurso de outro usuário devolve **404**, não 403 — 403 confirma que o id existe.
- Nunca aceite `usuarioId` vindo do corpo da requisição para decidir permissão:
  a identidade vem do contexto de segurança.

## Senhas e credenciais

- Hash com `BCryptPasswordEncoder` (ou Argon2) — nunca MD5, SHA-1, SHA-256 puro.
- Senha nunca é logada, nunca aparece em DTO de resposta, nunca em `toString()`.
- Login inválido devolve mensagem genérica ("credenciais inválidas"), sem distinguir
  e-mail inexistente de senha errada.
- Rate limit no login e nos endpoints de recuperação de senha — o Redis do projeto
  serve para isso.

## Tokens

- Segredo de assinatura vem de variável de ambiente, validado na inicialização.
- Expiração curta no access token; refresh token com rotação e revogação.
- Claim de papel/permissão é verificada no servidor a cada requisição; nunca confie
  em claim para decidir sem revalidar contra o estado atual quando a permissão puder
  ter mudado.

## Segredos

- **Nenhum segredo em código, teste, log, comentário ou migration.**
- `application.properties` referencia variáveis: `${DATABASE_PASSWORD}`, `${JWT_SECRET}`.
- O `compose.yaml` contém senha de desenvolvimento local. Ela não vale para nenhum
  outro ambiente e não deve ser copiada para lugar nenhum.
- Segredo commitado por engano é segredo **rotacionado**, não apenas removido do
  próximo commit.

## Validação de entrada

- Toda entrada externa é validada no servidor. Validação de frontend é usabilidade,
  não segurança.
- Bean Validation nos DTOs (ver `.rules/java-api.md`).
- Queries sempre parametrizadas — JPQL/SQL por concatenação é proibido, mesmo com
  valor "controlado".
- Upload de arquivo (quando houver): valide tipo real, limite tamanho, gere nome
  próprio e nunca use o nome enviado pelo cliente na montagem do caminho.

## CORS

- Origens permitidas vêm de configuração, por ambiente:
  `${CORS_ALLOWED_ORIGINS:http://localhost:3000}` (o frontend Next.js roda em 3000).
- `allowedOrigins("*")` combinado com `allowCredentials(true)` é inválido e inseguro.
  Liste as origens.
- Configure CORS no `SecurityFilterChain`, em um único lugar.

## Logs

- Nunca logue: senha, token, cookie de sessão, CPF, cartão, corpo inteiro de
  requisição autenticada.
- Logue eventos de segurança: falha de login, negação de acesso, alteração de
  permissão — com identificador do usuário, não com o dado sensível.
- Erro para o cliente é genérico; o detalhe (stack trace, SQL) vai só para o log.

## Dados pessoais

- Colete o mínimo necessário.
- Dado sensível não vai para o Redis sem TTL curto e necessidade real.
- Exclusão de conta precisa remover ou anonimizar de fato, inclusive em cache.
- Não exponha e-mail ou telefone de terceiros em resposta de listagem pública.

## Dependências

- `./gradlew dependencies` para inspecionar; atualize versão vulnerável assim que
  identificada.
- Não adicione dependência nova sem necessidade clara — cada uma é superfície de ataque.

## Checklist rápido

- [ ] Endpoint novo tem regra de autorização explícita
- [ ] Acesso a recurso de terceiro é impossível (filtro por dono na query)
- [ ] Nenhum segredo literal no diff
- [ ] Entradas validadas no servidor
- [ ] Queries parametrizadas
- [ ] Mensagem de erro não vaza detalhe interno
- [ ] Nada sensível em log
- [ ] CORS não foi afrouxado

## Módulos relacionados

- `.rules/java-api.md` — validação e formato de erro
- `.rules/database.md` — dados sensíveis e SQL injection
- `.rules/java-checklist.md` — checklist final
