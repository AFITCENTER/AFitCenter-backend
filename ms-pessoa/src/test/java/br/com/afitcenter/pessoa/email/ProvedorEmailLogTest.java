package br.com.afitcenter.pessoa.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ProvedorEmailLogTest {

    private static final String TOKEN = "Zx81qWvB4rTnLp0aKsDfGhJkLmNoPqRsTuVwXyZ_-12";
    private static final MensagemEmail EMAIL = new MensagemEmail("ana.souza@exemplo.com", "AFitCenter: assunto",
            "Olá, Ana Souza.\n\nhttp://localhost:5173/redefinir-senha#token=" + TOKEN, TOKEN);

    @Test
    void foraDoPerfilLocalOLogNaoTemOTokenNemDadosPessoais(CapturedOutput saida) {
        new ProvedorEmailLog(false).enviar(EMAIL);

        assertThat(saida.getAll())
                .contains("AFitCenter: assunto")
                .contains("a***@exemplo.com")
                .contains("Zx81...")
                .doesNotContain(TOKEN)
                .doesNotContain("ana.souza")
                .doesNotContain("Ana Souza");
    }

    @Test
    void noPerfilLocalOLogTemOTextoInteiro(CapturedOutput saida) {
        new ProvedorEmailLog(true).enviar(EMAIL);

        assertThat(saida.getAll()).contains("ana.souza@exemplo.com").contains("#token=" + TOKEN);
    }

    @Test
    void mensagemNuncaMostraOTextoNoToString() {
        assertThat(EMAIL.toString()).doesNotContain(TOKEN).doesNotContain("ana.souza");
    }
}
