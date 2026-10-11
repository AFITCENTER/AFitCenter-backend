package br.com.afitcenter.core.base.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializa erros no formato Problem Details (RFC 9457) sem depender do Spring MVC.
 *
 * <p>Usado onde o {@code @RestControllerAdvice} não alcança: filtros de segurança dos
 * serviços e filtros reativos do Gateway. Os controllers usam o {@code ProblemDetail}
 * do Spring, que gera o mesmo formato.</p>
 */
public final class ProblemaJson {

    public static final String CONTENT_TYPE = "application/problem+json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProblemaJson() {
    }

    public static String serializar(int status, String titulo, String detalhe, String path) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("type", "about:blank");
        corpo.put("title", titulo);
        corpo.put("status", status);
        corpo.put("detail", detalhe);
        corpo.put("instance", path);
        corpo.put("timestamp", OffsetDateTime.now().toString());
        try {
            return MAPPER.writeValueAsString(corpo);
        } catch (JsonProcessingException e) {
            return "{\"status\":" + status + "}";
        }
    }
}
