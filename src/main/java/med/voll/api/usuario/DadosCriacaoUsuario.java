package med.voll.api.usuario;

import jakarta.validation.constraints.NotBlank;

public record DadosCriacaoUsuario(
        @NotBlank String login,
        @NotBlank String senha
) {
}
