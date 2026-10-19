package br.com.afitcenter.pagamentos.controller.internal;

import br.com.afitcenter.pagamentos.model.dto.Dtos.CobrancaResponse;
import br.com.afitcenter.pagamentos.model.dto.Dtos.CriarCobrancaRequest;
import br.com.afitcenter.pagamentos.service.CobrancaService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Usado pelo ms-planos para cobrar as faturas da assinatura. O Gateway bloqueia estas rotas. */
@Hidden
@RestController
@RequestMapping("/internal/cobrancas")
@RequiredArgsConstructor
public class CobrancaInternoController {

    private final CobrancaService service;

    /** 200 também quando a chave já existia (idempotente). */
    @PostMapping
    public CobrancaResponse criar(@Valid @RequestBody CriarCobrancaRequest dados) {
        return service.criar(dados);
    }

    @PostMapping("/{id}/cancelamento")
    public ResponseEntity<Void> cancelar(@PathVariable UUID id) {
        service.cancelar(id);
        return ResponseEntity.noContent().build();
    }
}
