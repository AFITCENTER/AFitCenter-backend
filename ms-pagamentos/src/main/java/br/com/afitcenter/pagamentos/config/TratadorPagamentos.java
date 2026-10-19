package br.com.afitcenter.pagamentos.config;

import br.com.afitcenter.core.web.erro.Problemas;
import br.com.afitcenter.pagamentos.provedor.ProvedorPagamento.NotificacaoInvalidaException;
import br.com.afitcenter.pagamentos.service.ProvedorIndisponivelException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erros próprios do ms-pagamentos, consultados antes do tratador padrão do core-web. */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TratadorPagamentos {

    @ExceptionHandler(NotificacaoInvalidaException.class)
    public ProblemDetail notificacaoInvalida(NotificacaoInvalidaException ex, HttpServletRequest request) {
        log.warn("Webhook recusado em {}: {}", request.getRequestURI(), ex.getMessage());
        return Problemas.criar(HttpStatus.UNAUTHORIZED, "Notificação não autenticada", ex.getMessage(), request);
    }

    @ExceptionHandler(ProvedorIndisponivelException.class)
    public ProblemDetail provedorIndisponivel(ProvedorIndisponivelException ex, HttpServletRequest request) {
        log.error("Falha no provedor de pagamento ao processar {} {}.", request.getMethod(), request.getRequestURI(), ex);
        return Problemas.criar(HttpStatus.BAD_GATEWAY, "Falha no provedor de pagamento",
                ex.getMessage() + " Tente novamente em instantes.", request);
    }
}
