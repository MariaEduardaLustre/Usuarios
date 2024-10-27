package med.voll.api.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DadosAtualizacaoUsuario(
        @NotNull Long id,
        @NotBlank String login,
        @NotBlank String senha
) {
}
