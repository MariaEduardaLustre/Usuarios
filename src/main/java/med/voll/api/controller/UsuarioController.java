package med.voll.api.controller;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import med.voll.api.usuario.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Optional;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping
    public ResponseEntity<Usuario> criarUsuario(@RequestBody @Valid DadosCriacaoUsuario dados) {
        var senhaCriptografada = passwordEncoder.encode(dados.senha());
        var usuario = new Usuario(null, dados.login(), senhaCriptografada);
        repository.save(usuario);
        return ResponseEntity.created(URI.create("/usuarios/" + usuario.getId())).body(usuario);
    }

    @PutMapping
    @Transactional
    public ResponseEntity<?> atualizarUsuario(@RequestBody @Valid DadosAtualizacaoUsuario dados) {
        // Verifique se o ID fornecido no corpo da solicitação é válido
        var usuario = repository.findById(dados.id());

        // Verifique se o usuário existe
        if (usuario.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var usuarioExistente = usuario.get();

        // Atualize as informações do usuário
        // Certifique-se de que você está criptografando a senha, se necessário
        var senhaCriptografada = passwordEncoder.encode(dados.senha());
        usuarioExistente.atualizarInformacoes(dados.login(), senhaCriptografada);

        // Salve as alterações
        repository.save(usuarioExistente);

        // Retorne a resposta com os detalhes do usuário atualizado
        return ResponseEntity.ok(new DadosDetalhamentoUsuario(usuarioExistente));
    }
}