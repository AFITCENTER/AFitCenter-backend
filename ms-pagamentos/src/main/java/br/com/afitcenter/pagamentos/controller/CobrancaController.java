package br.com.afitcenter.pagamentos.controller;

import br.com.afitcenter.core.web.paginacao.PaginaResponse;
import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import br.com.afitcenter.pagamentos.model.dto.Dtos.CobrancaResponse;
import br.com.afitcenter.pagamentos.model.enums.Enums.StatusCobranca;
import br.com.afitcenter.pagamentos.service.CobrancaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/cobrancas")
@RequiredArgsConstructor
@Tag(name = "Cobranças", description = "Cobranças do nutricionista logado, com os dados para pagar. São criadas pelos "
        + "outros serviços (ex.: fatura da assinatura no ms-planos).")
public class CobrancaController {

    private final CobrancaService service;

    @GetMapping
    @Operation(summary = "Minhas cobranças", description = "Mais recentes primeiro.")
    public PaginaResponse<CobrancaResponse> listar(@RequestParam(required = false) StatusCobranca status,
                                                   @RequestParam(defaultValue = "0") int pagina,
                                                   @RequestParam(defaultValue = "20") int tamanho) {
        return service.listar(UsuarioAutenticado.nutricionistaId(), status, pagina, tamanho);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar cobrança", description = "Pix copia e cola, linha digitável ou página de pagamento.")
    public CobrancaResponse buscar(@PathVariable UUID id) {
        return service.buscar(UsuarioAutenticado.nutricionistaId(), id);
    }
}
