package br.com.afitcenter.pagamentos.controller;

import br.com.afitcenter.core.auditoria.AuditoriaRequisicao;
import br.com.afitcenter.core.web.paginacao.PaginaResponse;
import br.com.afitcenter.pagamentos.model.dto.Dtos.CobrancaResponse;
import br.com.afitcenter.pagamentos.model.enums.Enums.StatusCobranca;
import br.com.afitcenter.pagamentos.service.CobrancaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/cobrancas")
@RequiredArgsConstructor
@Tag(name = "Cobranças (administração)", description = "Todas as cobranças e estornos. Exige o papel ADMIN.")
public class CobrancaAdminController {

    private final CobrancaService service;

    @GetMapping
    @Operation(summary = "Listar cobranças", description = "Mais recentes primeiro, com filtros opcionais.")
    public PaginaResponse<CobrancaResponse> listar(@RequestParam(required = false) UUID nutricionistaId,
                                                   @RequestParam(required = false) StatusCobranca status,
                                                   @RequestParam(defaultValue = "0") int pagina,
                                                   @RequestParam(defaultValue = "50") int tamanho) {
        AuditoriaRequisicao.nutricionista(nutricionistaId);
        return service.listar(nutricionistaId, status, pagina, tamanho);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar cobrança")
    public CobrancaResponse buscar(@PathVariable UUID id) {
        CobrancaResponse cobranca = service.buscarAdmin(id);
        AuditoriaRequisicao.nutricionista(cobranca.nutricionistaId());
        return cobranca;
    }

    @PostMapping("/{id}/estorno")
    @Operation(summary = "Estornar cobrança paga",
            description = "Devolve o valor ao pagador. O serviço de origem é avisado (assinatura: o período pago deixa de valer).")
    public CobrancaResponse estornar(@PathVariable UUID id) {
        CobrancaResponse cobranca = service.estornar(id);
        AuditoriaRequisicao.nutricionista(cobranca.nutricionistaId());
        return cobranca;
    }
}
