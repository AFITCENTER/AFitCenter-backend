package br.com.afitcenter.core.auditoria;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.RecordComponent;
import java.util.UUID;

/**
 * Lê {@code id} e {@code pacienteId} do corpo da resposta (records dos DTOs) para o registro de auditoria.
 * É assim que uma criação ({@code POST /v1/pacientes}) fica registrada com o id do que foi criado e uma
 * consulta ou dieta fica ligada ao paciente.
 */
@ControllerAdvice
public class AuditoriaRespostaAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(@NonNull MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, @NonNull MethodParameter returnType, @NonNull MediaType contentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> converterType,
                                  @NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response) {
        if (body instanceof Record registro && request instanceof ServletServerHttpRequest servlet) {
            HttpServletRequest http = servlet.getServletRequest();
            Object id = componente(registro, "id");
            if (id != null) http.setAttribute(AuditoriaRequisicao.RESPOSTA_ID, id.toString());
            if (componente(registro, "pacienteId") instanceof UUID paciente)
                http.setAttribute(AuditoriaRequisicao.RESPOSTA_PACIENTE, paciente);
        }
        return body;
    }

    private static Object componente(Record registro, String nome) {
        for (RecordComponent componente : registro.getClass().getRecordComponents()) {
            if (!componente.getName().equals(nome)) continue;
            try {
                return componente.getAccessor().invoke(registro);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }
}
