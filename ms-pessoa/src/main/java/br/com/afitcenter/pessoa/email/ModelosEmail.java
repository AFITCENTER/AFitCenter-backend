package br.com.afitcenter.pessoa.email;

import br.com.afitcenter.pessoa.model.entity.Pessoa;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Textos dos e-mails. Os links apontam para o frontend ({@code afitcenter.email.url-frontend}) e levam o token no
 * fragmento ({@code #token=...}): o navegador não envia o fragmento a nenhum servidor, então o token não fica em
 * log de acesso nem em cabeçalho {@code Referer}. A página lê o token e o manda no corpo do POST.
 *
 * <pre>
 * {url-frontend}/redefinir-senha#token=...   → POST /ms-pessoa/v1/senha/redefinir { token, novaSenha }
 * {url-frontend}/confirmar-email#token=...   → POST /ms-pessoa/v1/conta/email/confirmacao { token }
 * </pre>
 */
@Component
public class ModelosEmail {

    private final String urlFrontend;

    public ModelosEmail(@Value("${afitcenter.email.url-frontend}") String urlFrontend) {
        this.urlFrontend = urlFrontend.trim().replaceAll("/+$", "");
    }

    public MensagemEmail redefinicaoSenha(Pessoa pessoa, String token, Duration validade) {
        String link = urlFrontend + "/redefinir-senha#token=" + token;
        return new MensagemEmail(pessoa.getEmail(), "AFitCenter: redefinição de senha", """
                Olá, %s.

                Recebemos um pedido para redefinir a senha da sua conta no AFitCenter. Para escolher uma senha \
                nova, abra o link abaixo em até %s:

                %s

                O link só pode ser usado uma vez. Se você não pediu a redefinição, ignore este e-mail: sua senha \
                continua a mesma.
                """.formatted(pessoa.getNome(), duracao(validade), link), token);
    }

    public MensagemEmail confirmacaoEmail(Pessoa pessoa, String token, Duration validade) {
        String link = urlFrontend + "/confirmar-email#token=" + token;
        return new MensagemEmail(pessoa.getEmail(), "AFitCenter: confirme seu e-mail", """
                Olá, %s.

                Sua conta no AFitCenter foi criada com este e-mail. Para confirmar que ele é seu, abra o link \
                abaixo em até %s:

                %s

                Se você não criou a conta, ignore este e-mail.
                """.formatted(pessoa.getNome(), duracao(validade), link), token);
    }

    /** {@code 30 minutos}, {@code 1 hora}, {@code 48 horas}. */
    static String duracao(Duration validade) {
        long minutos = validade.toMinutes();
        if (minutos % 60 != 0) return minutos == 1 ? "1 minuto" : minutos + " minutos";
        long horas = minutos / 60;
        return horas == 1 ? "1 hora" : horas + " horas";
    }
}
