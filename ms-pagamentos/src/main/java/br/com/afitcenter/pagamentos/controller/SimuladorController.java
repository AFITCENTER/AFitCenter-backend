package br.com.afitcenter.pagamentos.controller;

import br.com.afitcenter.pagamentos.model.dto.Dtos.CobrancaResponse;
import br.com.afitcenter.pagamentos.service.SimuladorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Só existe com {@code afitcenter.pagamentos.simulador.endpoints-habilitados=true} (perfis local e homolog). */
@RestController
@RequestMapping("/v1/simulador/cobrancas")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "afitcenter.pagamentos.simulador.endpoints-habilitados", havingValue = "true")
@Tag(name = "Simulador de pagamento", description = "Desenvolvimento e homologação: confirma ou recusa cobranças do "
        + "provedor simulado como se o pagador tivesse pago. Não existe em produção.")
public class SimuladorController {

    private final SimuladorService service;

    @PostMapping("/{id}/pagamento")
    @Operation(summary = "Simular pagamento", description = "Dispara a mesma notificação que o provedor enviaria.")
    public CobrancaResponse pagar(@PathVariable UUID id) {
        return service.pagar(id);
    }

    @PostMapping("/{id}/recusa")
    @Operation(summary = "Simular cartão recusado")
    public CobrancaResponse recusar(@PathVariable UUID id) {
        return service.recusar(id);
    }
}
