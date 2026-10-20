package br.com.afitcenter.pessoa.email;

/**
 * E-mail de texto simples.
 *
 * @param segredo trecho do texto que não pode aparecer em log (o token do link). O {@link ProvedorEmailLog} o
 *                mascara fora do perfil local.
 */
public record MensagemEmail(String para, String assunto, String texto, String segredo) {

    /** Sem o texto e o segredo, que nunca vão para log nem para mensagens de erro. */
    @Override
    public String toString() {
        return "MensagemEmail[assunto=" + assunto + "]";
    }
}
