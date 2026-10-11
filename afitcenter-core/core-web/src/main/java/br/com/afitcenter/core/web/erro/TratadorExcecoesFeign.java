package br.com.afitcenter.core.web.erro;

import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Falhas em chamadas Feign não tratadas pelo serviço viram {@code 502 Bad Gateway}, sem expor
 * detalhes internos. Quando a resposta de outro serviço tem significado de negócio (ex.: 409 de
 * e-mail duplicado), trate a {@code FeignException} específica no próprio service.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class TratadorExcecoesFeign {

    @ExceptionHandler(FeignException.class)
    public ProblemDetail falhaIntegracao(FeignException ex, HttpServletRequest request) {
        log.error("Falha na chamada a outro microsserviço ao processar {} {} (status {}).",
                request.getMethod(), request.getRequestURI(), ex.status(), ex);
        return Problemas.criar(HttpStatus.BAD_GATEWAY, "Falha de integração",
                "Não foi possível concluir a operação em um serviço dependente.", request);
    }
}
