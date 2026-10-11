package br.com.afitcenter.core.web.erro;

/** 422: dados válidos no formato, mas que violam uma regra de negócio. */
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
