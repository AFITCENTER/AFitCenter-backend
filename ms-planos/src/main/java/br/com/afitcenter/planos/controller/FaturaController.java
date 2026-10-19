package br.com.afitcenter.planos.controller;

import br.com.afitcenter.core.web.paginacao.PaginaResponse;
import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import br.com.afitcenter.planos.model.dto.Dtos.CobrancaResponse;
import br.com.afitcenter.planos.model.dto.Dtos.FaturaResponse;
import br.com.afitcenter.planos.model.dto.Dtos.NovaCobrancaRequest;
import br.com.afitcenter.planos.service.FaturaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/assinatura/faturas")
@RequiredArgsConstructor
@Tag(name = "Faturas", description = "Faturas da assinatura do nutricionista logado. Os dados de pagamento de cada "
        + "cobrança estão em /ms-pagamentos/v1/cobrancas/{cobrancaId}.")
public class FaturaController {

    private final FaturaService service;

    @GetMapping
    @Operation(summary = "Minhas faturas", description = "Mais recentes primeiro.")
    public PaginaResponse<FaturaResponse> listar(@RequestParam(defaultValue = "0") int pagina,
                                                 @RequestParam(defaultValue = "20") int tamanho) {
        return service.listar(UsuarioAutenticado.nutricionistaId(), pagina, tamanho);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar fatura")
    public FaturaResponse buscar(@PathVariable UUID id) {
        return service.buscar(UsuarioAutenticado.nutricionistaId(), id);
    }

    @PostMapping("/{id}/cobrancas")
    @Operation(summary = "Gerar nova cobrança",
            description = "Para uma fatura em aberto: Pix ou boleto vencido, cartão recusado ou troca da forma de "
                    + "pagamento. A cobrança anterior é cancelada.")
    public ResponseEntity<CobrancaResponse> novaCobranca(@PathVariable UUID id,
                                                         @Valid @RequestBody NovaCobrancaRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.novaCobranca(UsuarioAutenticado.nutricionistaId(), id, dados.formaPagamento()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancelar mudança de plano não paga",
            description = "Só faturas de mudança de plano em aberto. Renovações: cancele a assinatura.")
    public ResponseEntity<Void> cancelar(@PathVariable UUID id) {
        service.cancelar(UsuarioAutenticado.nutricionistaId(), id);
        return ResponseEntity.noContent().build();
    }
}
