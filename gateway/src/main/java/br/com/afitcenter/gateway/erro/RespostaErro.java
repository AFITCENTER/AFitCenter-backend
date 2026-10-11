package br.com.afitcenter.gateway.erro;

import br.com.afitcenter.core.base.error.ProblemaJson;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/** Respostas de erro do Gateway no mesmo formato Problem Details dos microsserviços. */
public final class RespostaErro {

    private RespostaErro() {
    }

    public static Mono<Void> escrever(ServerWebExchange exchange, HttpStatus status, String detalhe) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.parseMediaType(ProblemaJson.CONTENT_TYPE));
        byte[] corpo = ProblemaJson.serializar(status.value(), status.getReasonPhrase(), detalhe,
                exchange.getRequest().getURI().getPath()).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(corpo);
        return response.writeWith(Mono.just(buffer));
    }
}
