package br.com.afitcenter.pessoa.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;

/**
 * Envio por SMTP (qualquer servidor: Amazon SES, SendGrid, Mailgun, o do domínio...). Configurado por
 * {@code AFIT_SMTP_HOST}, {@code AFIT_SMTP_PORT}, {@code AFIT_SMTP_USUARIO}, {@code AFIT_SMTP_SENHA},
 * {@code AFIT_SMTP_STARTTLS} e {@code AFIT_EMAIL_REMETENTE}; só o container do ms-pessoa os recebe.
 *
 * <p>Sem servidor ou sem remetente o serviço não sobe: um ambiente que deveria enviar e-mails não fica, sem
 * ninguém perceber, sem recuperação de senha.</p>
 *
 * <p>O cliente SMTP é criado aqui, e não pela configuração automática do Spring ({@code spring.mail.*}), para o
 * health check do serviço não depender do servidor de e-mail.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "afitcenter.email.provedor", havingValue = ProvedorEmailSmtp.NOME)
public class ProvedorEmailSmtp implements ProvedorEmail {

    public static final String NOME = "SMTP";

    private static final Duration TIMEOUT_CONEXAO = Duration.ofSeconds(5);
    private static final Duration TIMEOUT_LEITURA = Duration.ofSeconds(10);

    private final JavaMailSenderImpl sender;
    private final InternetAddress remetente;

    public ProvedorEmailSmtp(@Value("${afitcenter.email.smtp.host:}") String host,
                             @Value("${afitcenter.email.smtp.porta:587}") int porta,
                             @Value("${afitcenter.email.smtp.usuario:}") String usuario,
                             @Value("${afitcenter.email.smtp.senha:}") String senha,
                             @Value("${afitcenter.email.smtp.starttls:true}") boolean starttls,
                             @Value("${afitcenter.email.remetente:}") String remetente) {
        if (host == null || host.isBlank())
            throw new IllegalStateException("Envio de e-mail por SMTP sem servidor: defina AFIT_SMTP_HOST "
                    + "(ou AFIT_EMAIL_PROVEDOR=LOG, que não envia nada).");
        this.remetente = endereco(remetente);
        this.sender = new JavaMailSenderImpl();
        sender.setHost(host.trim());
        sender.setPort(porta);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        boolean autenticado = usuario != null && !usuario.isBlank();
        if (autenticado) {
            sender.setUsername(usuario.trim());
            sender.setPassword(senha);
        }
        Properties propriedades = sender.getJavaMailProperties();
        propriedades.put("mail.smtp.auth", String.valueOf(autenticado));
        propriedades.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        propriedades.put("mail.smtp.starttls.required", String.valueOf(starttls));
        propriedades.put("mail.smtp.connectiontimeout", String.valueOf(TIMEOUT_CONEXAO.toMillis()));
        propriedades.put("mail.smtp.timeout", String.valueOf(TIMEOUT_LEITURA.toMillis()));
        propriedades.put("mail.smtp.writetimeout", String.valueOf(TIMEOUT_LEITURA.toMillis()));
        log.info("E-mails enviados por SMTP em {}:{} (STARTTLS {}).", host.trim(), porta, starttls ? "exigido" : "desligado");
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public void enviar(MensagemEmail email) {
        MimeMessage mensagem = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, false, StandardCharsets.UTF_8.name());
            helper.setFrom(remetente);
            helper.setTo(email.para());
            helper.setSubject(email.assunto());
            helper.setText(email.texto(), false);
        } catch (MessagingException e) {
            throw new MailPreparationException("Não foi possível montar o e-mail '" + email.assunto() + "'.", e);
        }
        sender.send(mensagem);
    }

    /** Aceita {@code nao-responda@dominio} e {@code AFitCenter <nao-responda@dominio>}. */
    private static InternetAddress endereco(String remetente) {
        if (remetente == null || remetente.isBlank())
            throw new IllegalStateException("Envio de e-mail por SMTP sem remetente: defina AFIT_EMAIL_REMETENTE "
                    + "(ex.: AFitCenter <nao-responda@seu-dominio>).");
        try {
            InternetAddress endereco = new InternetAddress(remetente.trim(), true);
            if (endereco.getPersonal() != null)
                endereco.setPersonal(endereco.getPersonal(), StandardCharsets.UTF_8.name());
            return endereco;
        } catch (Exception e) {
            throw new IllegalStateException("AFIT_EMAIL_REMETENTE inválido: use nome@dominio ou Nome <nome@dominio>.", e);
        }
    }
}
