package br.com.afitcenter.pessoa.email;

/**
 * Envio de e-mail. Para plugar outro meio (API de um serviço de e-mail, por exemplo), implemente esta interface como
 * bean ligado por {@code afitcenter.email.provedor} = {@link #nome()}. Só um provedor fica ativo.
 *
 * <ul>
 *     <li>{@link ProvedorEmailLog} ({@code LOG}): não envia nada, só registra no log. Padrão nos perfis local e
 *     homolog.</li>
 *     <li>{@link ProvedorEmailSmtp} ({@code SMTP}): envia pelo servidor configurado em {@code AFIT_SMTP_*}. Padrão
 *     nos demais perfis.</li>
 * </ul>
 *
 * <p>É chamada de rede: o {@link EnvioEmail} a faz depois do commit, fora da transação e da requisição. Falhas
 * devem lançar exceção; vão para o log, e quem pediu o e-mail pode pedir de novo.</p>
 */
public interface ProvedorEmail {

    /** Nome em maiúsculas, o valor de {@code afitcenter.email.provedor}. */
    String nome();

    void enviar(MensagemEmail email);
}
