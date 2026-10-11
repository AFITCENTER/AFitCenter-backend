package br.com.afitcenter.core.web.erro;

/** 404. Também usado quando o recurso existe mas pertence a outro nutricionista. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
