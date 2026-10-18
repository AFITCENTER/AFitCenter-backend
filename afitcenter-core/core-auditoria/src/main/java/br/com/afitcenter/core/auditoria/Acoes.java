package br.com.afitcenter.core.auditoria;

import java.util.Locale;

/**
 * Ações usadas na trilha. São texto no evento (o ms-auditoria aceita ações novas sem mudar), mas os
 * serviços usam estas constantes.
 */
public final class Acoes {

    public static final String LEITURA = "LEITURA";
    public static final String CRIACAO = "CRIACAO";
    public static final String ALTERACAO = "ALTERACAO";
    public static final String EXCLUSAO = "EXCLUSAO";
    public static final String LOGIN = "LOGIN";

    /** Papel de quem agiu quando não há usuário: agendadores e eventos recebidos. */
    public static final String PAPEL_SISTEMA = "SISTEMA";
    public static final String PAPEL_ANONIMO = "ANONIMO";
    public static final String PAPEL_INTERNO = "INTERNO";

    private Acoes() {
    }

    /** Ação padrão de cada método HTTP. */
    public static String doMetodo(String metodo) {
        return switch (metodo.toUpperCase(Locale.ROOT)) {
            case "GET", "HEAD" -> LEITURA;
            case "POST" -> CRIACAO;
            case "DELETE" -> EXCLUSAO;
            default -> ALTERACAO;
        };
    }
}
