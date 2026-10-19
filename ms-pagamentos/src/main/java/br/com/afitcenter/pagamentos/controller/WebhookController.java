package br.com.afitcenter.pagamentos.controller;

import br.com.afitcenter.pagamentos.service.NotificacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Notificações dos provedores de pagamento. Públicas: cada provedor assina as "
        + "suas, e notificações sem assinatura válida respondem 401.")
public class WebhookController {

    private final NotificacaoService service;

    /**
     * O corpo é lido como texto: a assinatura é conferida sobre os bytes exatos recebidos. Responde 200 (não 204):
     * o Asaas trata qualquer outro status como falha e pausa a fila de webhooks depois de 15 seguidas.
     */
    @PostMapping("/{provedor}")
    @Operation(summary = "Receber notificação do provedor", description = "Reentregas da mesma notificação são ignoradas.")
    public ResponseEntity<Void> receber(@PathVariable String provedor, @RequestHeader HttpHeaders headers,
                                        @RequestBody(required = false) String corpo) {
        service.processar(provedor, headers.toSingleValueMap(), corpo);
        return ResponseEntity.ok().build();
    }
}
