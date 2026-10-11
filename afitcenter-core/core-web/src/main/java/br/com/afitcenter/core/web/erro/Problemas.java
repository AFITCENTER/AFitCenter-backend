package br.com.afitcenter.core.web.erro;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.OffsetDateTime;

/** Fábrica de {@link ProblemDetail} com os campos padrão ({@code instance} e {@code timestamp}). */
public final class Problemas {

    private Problemas() {
    }

    public static ProblemDetail criar(HttpStatusCode status, String titulo, String detalhe, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detalhe);
        problem.setTitle(titulo != null ? titulo : tituloPadrao(status));
        problem.setType(URI.create("about:blank"));
        return completar(problem, request.getRequestURI());
    }

    public static ProblemDetail completar(ProblemDetail problem, String path) {
        if (path != null) problem.setInstance(URI.create(path));
        problem.setProperty("timestamp", OffsetDateTime.now().toString());
        return problem;
    }

    static String tituloPadrao(HttpStatusCode status) {
        HttpStatus httpStatus = HttpStatus.resolve(status.value());
        return httpStatus != null ? httpStatus.getReasonPhrase() : "Erro";
    }
}
