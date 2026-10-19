package br.com.afitcenter.core.auditoria;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Complementa o registro da requisição atual, para o que o interceptor não consegue deduzir sozinho.
 * Fora de uma requisição HTTP (agendador, listener) as chamadas não fazem nada.
 *
 * <pre>
 * // login: a chamada é anônima, mas o registro deve dizer quem entrou
 * AuditoriaRequisicao.usuario(usuario.getId());
 * AuditoriaRequisicao.acao(Acoes.LOGIN);
 * </pre>
 */
public final class AuditoriaRequisicao {

    static final String PREFIXO = AuditoriaRequisicao.class.getName() + ".";
    static final String USUARIO = PREFIXO + "usuario";
    static final String NUTRICIONISTA = PREFIXO + "nutricionista";
    static final String ACAO = PREFIXO + "acao";
    static final String PACIENTE = PREFIXO + "paciente";
    static final String RECURSO_ID = PREFIXO + "recursoId";
    static final String DETALHES = PREFIXO + "detalhes";
    static final String IGNORAR = PREFIXO + "ignorar";
    /** Preenchidos pelo {@link AuditoriaRespostaAdvice} a partir do corpo da resposta. */
    static final String RESPOSTA_ID = PREFIXO + "respostaId";
    static final String RESPOSTA_PACIENTE = PREFIXO + "respostaPaciente";

    private AuditoriaRequisicao() {
    }

    /** Quem agiu, quando a requisição é anônima (login, cadastro). */
    public static void usuario(UUID usuarioId) {
        definir(USUARIO, usuarioId);
    }

    /** Tenant dono do dado, quando não vem do token. */
    public static void nutricionista(UUID nutricionistaId) {
        definir(NUTRICIONISTA, nutricionistaId);
    }

    public static void acao(String acao) {
        definir(ACAO, acao);
    }

    public static void paciente(UUID pacienteId) {
        definir(PACIENTE, pacienteId);
    }

    public static void recursoId(Object id) {
        definir(RECURSO_ID, id == null ? null : id.toString());
    }

    /** Informação extra, sem dados pessoais (nada de nome, CPF, e-mail ou conteúdo clínico). */
    @SuppressWarnings("unchecked")
    public static void detalhe(String chave, String valor) {
        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
        if (atributos == null) return;
        var detalhes = (Map<String, String>) atributos.getAttribute(DETALHES, RequestAttributes.SCOPE_REQUEST);
        if (detalhes == null) {
            detalhes = new LinkedHashMap<>();
            atributos.setAttribute(DETALHES, detalhes, RequestAttributes.SCOPE_REQUEST);
        }
        detalhes.put(chave, valor);
    }

    /** Não registra esta requisição (ex.: consultas públicas de catálogo). */
    public static void ignorar() {
        definir(IGNORAR, Boolean.TRUE);
    }

    private static void definir(String nome, Object valor) {
        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
        if (atributos != null && valor != null)
            atributos.setAttribute(nome, valor, RequestAttributes.SCOPE_REQUEST);
    }
}
