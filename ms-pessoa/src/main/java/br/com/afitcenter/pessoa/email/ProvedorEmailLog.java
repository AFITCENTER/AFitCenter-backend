package br.com.afitcenter.pessoa.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Provedor de mentira para desenvolvimento e homologação: nenhum e-mail sai do serviço, a mensagem só vai para o
 * log. O texto inteiro, com o link e o token, só aparece com {@code afitcenter.email.log.completo=true} (ligado no
 * perfil local); fora dele, o log mostra o assunto, o endereço mascarado e o começo do token.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "afitcenter.email.provedor", havingValue = ProvedorEmailLog.NOME)
public class ProvedorEmailLog implements ProvedorEmail {

    public static final String NOME = "LOG";

    private final boolean completo;

    public ProvedorEmailLog(@Value("${afitcenter.email.log.completo:false}") boolean completo) {
        this.completo = completo;
        log.warn("Provedor de e-mail LOG ativo: nenhum e-mail é enviado, as mensagens só aparecem neste log{}.",
                completo ? " (com os links)" : " (sem os links)");
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public void enviar(MensagemEmail email) {
        if (completo)
            log.info("E-mail para {}: {}\n{}", email.para(), email.assunto(), email.texto());
        else
            log.info("E-mail para {}: {} (token {})", mascararEndereco(email.para()), email.assunto(),
                    mascararSegredo(email.segredo()));
    }

    /** {@code ana.souza@exemplo.com} → {@code a***@exemplo.com}. */
    static String mascararEndereco(String endereco) {
        if (endereco == null) return "-";
        int arroba = endereco.indexOf('@');
        return arroba < 1 ? "***" : endereco.charAt(0) + "***" + endereco.substring(arroba);
    }

    /** Só os 4 primeiros caracteres: dá para relacionar duas linhas do log, mas não para usar o link. */
    static String mascararSegredo(String segredo) {
        if (segredo == null || segredo.isEmpty()) return "-";
        return segredo.length() <= 8 ? "***" : segredo.substring(0, 4) + "...";
    }
}
