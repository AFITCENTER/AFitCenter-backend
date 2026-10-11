package br.com.afitcenter.core.web.seguranca;

import br.com.afitcenter.core.base.error.ProblemaJson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Escreve Problem Details direto no {@link HttpServletResponse}. Usado pelos filtros e
 * handlers do Spring Security, que rodam fora do {@code DispatcherServlet}.
 */
public final class RespostaProblema {

    private RespostaProblema() {
    }

    public static void escrever(HttpServletRequest request, HttpServletResponse response, int status, String detalhe)
            throws IOException {
        if (response.isCommitted()) return;

        HttpStatus httpStatus = HttpStatus.resolve(status);
        String titulo = httpStatus != null ? httpStatus.getReasonPhrase() : "Erro";

        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(ProblemaJson.CONTENT_TYPE);
        response.getWriter().write(ProblemaJson.serializar(status, titulo, detalhe, request.getRequestURI()));
        response.getWriter().flush();
    }
}
