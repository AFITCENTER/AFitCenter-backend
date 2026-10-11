package br.com.afitcenter.core.web.erro;

/** 409: duplicidade ou estado que impede a operação (ex.: CPF já cadastrado, horário ocupado). */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
