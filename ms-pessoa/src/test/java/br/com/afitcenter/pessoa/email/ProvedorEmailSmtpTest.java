package br.com.afitcenter.pessoa.email;

import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailException;

import java.net.InetAddress;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProvedorEmailSmtpTest {

    private static final MensagemEmail EMAIL = new MensagemEmail("ana@exemplo.com", "AFitCenter: redefinição de senha",
            "Olá, Ana.\n\nhttp://localhost:5173/redefinir-senha#token=abc123", "abc123");

    @Test
    void enviaPeloServidorComRemetenteAssuntoETextoEmUtf8() throws Exception {
        try (SmtpFalso smtp = new SmtpFalso()) {
            new ProvedorEmailSmtp("127.0.0.1", smtp.porta(), "", "", false,
                    "AFitCenter Saúde <nao-responda@afitcenter.local>").enviar(EMAIL);

            MimeMessage recebida = smtp.unica();
            InternetAddress de = (InternetAddress) recebida.getFrom()[0];
            assertThat(de.getAddress()).isEqualTo("nao-responda@afitcenter.local");
            assertThat(de.getPersonal()).isEqualTo("AFitCenter Saúde");
            assertThat(recebida.getRecipients(Message.RecipientType.TO))
                    .extracting(destinatario -> ((InternetAddress) destinatario).getAddress())
                    .containsExactly("ana@exemplo.com");
            assertThat(recebida.getSubject()).isEqualTo("AFitCenter: redefinição de senha");
            assertThat(recebida.getContentType()).startsWith("text/plain").containsIgnoringCase("UTF-8");
            assertThat(recebida.getContent().toString()).contains("Olá, Ana.").contains("#token=abc123");
            assertThat(smtp.comandos()).contains("MAIL FROM:<nao-responda@afitcenter.local>", "RCPT TO:<ana@exemplo.com>")
                    .noneMatch(comando -> comando.startsWith("AUTH") || comando.startsWith("STARTTLS"));
        }
    }

    @Test
    void servidorForaDoArLancaExcecaoParaOEnvioIrParaOLog() throws Exception {
        int portaFechada;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            portaFechada = socket.getLocalPort();
        }
        ProvedorEmailSmtp provedor = new ProvedorEmailSmtp("127.0.0.1", portaFechada, "", "", false,
                "nao-responda@afitcenter.local");

        assertThatThrownBy(() -> provedor.enviar(EMAIL)).isInstanceOf(MailException.class);
    }

    @Test
    void semServidorOuSemRemetenteValidoOServicoNaoSobe() {
        assertThatThrownBy(() -> new ProvedorEmailSmtp(" ", 587, "", "", true, "nao-responda@afitcenter.local"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("AFIT_SMTP_HOST");
        assertThatThrownBy(() -> new ProvedorEmailSmtp("smtp.exemplo.com", 587, "", "", true, ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("AFIT_EMAIL_REMETENTE");
        assertThatThrownBy(() -> new ProvedorEmailSmtp("smtp.exemplo.com", 587, "", "", true, "sem arroba"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("AFIT_EMAIL_REMETENTE inválido");
    }
}
