**Autor:** Maria Eduarda Alexandre Lustre
# Padrões de Projeto e Multimacadas 

## 1. Melhoria de Tratamento de Erros

**Problema:** Atualmente, o método tratarErro400 na classe TratadorDeErros cria uma lista de DadosErroValidacao diretamente a partir de cada erro encontrado, processando a lista de erros repetidamente sempre que o método é chamado. Isso aumenta o acoplamento entre as exceções e o controle de resposta, deixando o tratamento de erro menos coeso e dificultando a manutenção e expansão para novos tipos de erro.

**Padrão Sugerido:** Factory Method

**Código Original:**

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity tentarErro400(MethodArgumentNotValidException ex) {
    var erros = ex.getFieldErrors();
    return ResponseEntity.badRequest().body(erros.stream().map(DadosErroValidacao::new).toList());
}
```

**Solução Sugerida:** Implementar uma fábrica para construir objetos ResponseEntity com mensagens de erro padronizadas.Esse método reduz a repetição e facilita a adaptação de outros tipos de erro, simplificando o processo de tratamento.

```java
public class ErrorResponseFactory {
    public static ResponseEntity<?> createValidationError(List<FieldError> fieldErrors) {
        var errors = fieldErrors.stream().map(DadosErroValidacao::new).toList();
        return ResponseEntity.badRequest().body(errors);
    }
}
```

**Uso na Classe TratadorDeErros:**

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<?> tratarErro400(MethodArgumentNotValidException ex) {
    return ErrorResponseFactory.createValidationError(ex.getFieldErrors());
}
```

## 2. Reduzindo Duplicação de Lógica de Autenticação

**Problema:** A lógica de autenticação e geração de tokens JWT está duplicada nas classes AutenticacaoController e SecurityFilter, o que dificulta a manutenção do código, pois qualquer alteração nessa lógica exigiria mudanças em ambos os locais. Essa duplicação pode levar a inconsistências e a um código menos coeso.

**Padrão Sugerido:** Facade

**Código Original:**

```java
// Em AutenticacaoController
var authenticationToken = new UsernamePasswordAuthenticationToken(dados.login(), dados.senha());
var authentication = manager.authenticate(authenticationToken);
var tokenJWT = tokenService.gerarToken((Usuario) authentication.getPrincipal());

// Em SecurityFilter
var subject = tokenService.getSubject(tokenJWT);
var usuario = repository.findByLogin(subject);
var authentication = new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
SecurityContextHolder.getContext().setAuthentication(authentication);
```

**Solução Sugerida:** Criar uma fachada que centralize a lógica de autenticação e geração de tokens, simplificando o código e reduzindo a duplicação. Dessa forma, a lógica de autenticação estará concentrada em um único ponto, facilitando sua manutenção e modificações futuras.

```java
public class AuthenticationFacade {
    private final AuthenticationManager manager;
    private final TokenService tokenService;
    
    public AuthenticationFacade(AuthenticationManager manager, TokenService tokenService) {
        this.manager = manager;
        this.tokenService = tokenService;
    }

    public String authenticateAndGenerateToken(String login, String senha) {
        var authenticationToken = new UsernamePasswordAuthenticationToken(login, senha);
        var authentication = manager.authenticate(authenticationToken);
        return tokenService.gerarToken((Usuario) authentication.getPrincipal());
    }
}
```

**Uso na Classe AutenticacaoController:**

```java
@PostMapping
public ResponseEntity<?> efetuarLogin(@RequestBody @Valid DadosAutenticacao dados) {
    var tokenJWT = authenticationFacade.authenticateAndGenerateToken(dados.login(), dados.senha());
    return ResponseEntity.ok(new DadosTokenJWT(tokenJWT));
}
```

## 3. Validação dos Dados do Usuário

**Problema:** A lógica de validação de dados do usuário no UsuarioController está misturada com a lógica de atualização de dados do usuário, dificultando a coesão e o isolamento de responsabilidades. Ao validar e atualizar os dados na mesma função, o código torna-se mais difícil de entender e de modificar.

**Padrão Sugerido:** Strategy

**Código Original:**

```java
@PutMapping
@Transactional
public ResponseEntity<?> atualizarUsuario(@RequestBody @Valid DadosAtualizacaoUsuario dados) {
    var usuario = repository.findById(dados.id());
    if (usuario.isEmpty()) {
        return ResponseEntity.notFound().build();
    }
    var usuarioExistente = usuario.get();
    var senhaCriptografada = passwordEncoder.encode(dados.senha());
    usuarioExistente.atualizarInformacoes(dados.login(), senhaCriptografada);
    repository.save(usuarioExistente);
    return ResponseEntity.ok(new DadosDetalhamentoUsuario(usuarioExistente));
}
```

**Solução Sugerida:** Criar uma interface ValidacaoUsuario e implementar diferentes classes de validação para a criação e atualização de usuários, separando a lógica de validação e a lógica de atualização, tornando o código mais modular.

**Interface de Validação:**

```java
public interface ValidacaoUsuario {
    void validar(DadosUsuario dados);
}
```

**Implementação da Validação para Atualização:**

```java
public class ValidacaoAtualizacaoUsuario implements ValidacaoUsuario {
    @Override
    public void validar(DadosUsuario dados) {
        // Lógica de validação específica para atualização
        if (dados.login() == null || dados.login().isBlank()) {
            throw new IllegalArgumentException("Login não pode ser vazio.");
        }
        if (dados.senha() == null || dados.senha().length() < 6) {
            throw new IllegalArgumentException("A senha deve ter pelo menos 6 caracteres.");
        }
        // Adicione outras regras de validação conforme necessário
    }
}
```

**Uso na Classe UsuarioController:**

```java
public ResponseEntity<?> atualizarUsuario(@RequestBody @Valid DadosAtualizacaoUsuario dados) {
    validacaoUsuario.validar(dados);
    
    var usuario = repository.findById(dados.id());
    if (usuario.isEmpty()) {
        return ResponseEntity.notFound().build();
    }
    
    var usuarioExistente = usuario.get();
    var senhaCriptografada = passwordEncoder.encode(dados.senha());
    usuarioExistente.atualizarInformacoes(dados.login(), senhaCriptografada);
    repository.save(usuarioExistente);
    
    return ResponseEntity.ok(new DadosDetalhamentoUsuario(usuarioExistente));
}
```

## 4. Separação da Lógica de Token e Segurança

**Problema:** Na implementação atual, a classe TokenService é responsável por gerar e verificar tokens JWT. Esse acúmulo de responsabilidades viola o Princípio da Responsabilidade Única, que recomenda que uma classe ou método tenha uma única responsabilidade bem definida. Manter a geração de tokens e a verificação em uma única classe aumenta a complexidade e dificulta a manutenção, pois qualquer alteração nos detalhes de criação de token impacta diretamente a TokenService.

**Padrão Sugerido:** Builder

**Código Original:**

```java
public String gerarToken(Usuario usuario) {
    var algoritmo = Algorithm.HMAC256(secret);
    return JWT.create()
            .withIssuer("API Voll.med")
            .withSubject(usuario.getLogin())
            .withExpiresAt(dataExpiracao())
            .sign(algoritmo);
}
```

**Solução Sugerida:** Criar uma classe TokenBuilder que encapsula a lógica de construção do token em um único lugar, usando o padrão de projeto Builder. Com isso, a TokenService delega a responsabilidade de criação do token para o TokenBuilder, que constroi o token com as propriedades necessárias. O TokenBuilder permite que cada etapa da criação do token seja configurada de forma independente e flexível, o que facilita modificações futuras e amplia a reutilização.

**Classe TokenBuilder:**

```java
public class TokenBuilder {
    private String secret;
    private String issuer;
    private String subject;

    public TokenBuilder withSecret(String secret) {
        this.secret = secret;
        return this;
    }

    public TokenBuilder withIssuer(String issuer) {
        this.issuer = issuer;
        return this;
    }

    public TokenBuilder withSubject(String subject) {
        this.subject = subject;
        return this;
    }

    public String build() {
        var algorithm = Algorithm.HMAC256(secret);
        return JWT.create()
                .withIssuer(issuer)
                .withSubject(subject)
                .sign(algorithm);
    }
}
```

**Uso na Classe TokenService:**

```java
public String gerarToken(Usuario usuario) {
    return new TokenBuilder()
            .withSecret(secret)
            .withIssuer("API Voll.med")
            .withSubject(usuario.getLogin())
            .build();
}
```

## 5. Gerenciamento de Configurações Sensíveis

**Problema:** Atualmente, a TokenService acessa diretamente a variável secret para gerar o token JWT, e essa variável é obtida diretamente de uma configuração específica de ambiente. Esse método rígido torna difícil ajustar a configuração do token para diferentes ambientes, além de dificultar o gerenciamento seguro e flexível dos valores sensíveis.

**Padrão Sugerido:** Dependency Injection

**Código Original:**

```java
@Value("${api.security.token.secret}")
private String secret;
```

**Solução Sugerida:** Utilizar a Dependency Injection para injetar o valor de secret no construtor da TokenService. Isso permite que a classe receba a configuração automaticamente a partir do ambiente especificado e possibilita um gerenciamento mais seguro e escalável da configuração do token. Esse método facilita o uso de diferentes perfis de ambiente, ajustando automaticamente o valor do secret de acordo com o perfil ativo, além de melhorar a testabilidade da aplicação.

**Uso na Classe TokenService:**

```java
@Service
public class TokenService {
    private final String secret;

    public TokenService(@Value("${api.security.token.secret}") String secret) {
        this.secret = secret;
    }
}
```


















