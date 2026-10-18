package br.com.afitcenter.planos.controller;

import br.com.afitcenter.core.web.seguranca.UsuarioAutenticado;
import br.com.afitcenter.planos.model.dto.Dtos.AssinaturaResponse;
import br.com.afitcenter.planos.model.dto.Dtos.MudancaPlanoRequest;
import br.com.afitcenter.planos.model.dto.Dtos.MudancaPlanoResponse;
import br.com.afitcenter.planos.service.AssinaturaService;
import br.com.afitcenter.planos.service.MudancaPlanoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/assinatura")
@RequiredArgsConstructor
@Tag(name = "Assinatura", description = "Plano do nutricionista logado, mudança de plano e cancelamento.")
public class AssinaturaController {

    private final AssinaturaService assinaturaService;
    private final MudancaPlanoService mudancaPlanoService;

    @GetMapping
    @Operation(summary = "Minha assinatura",
            description = "Quem nunca assinou está no plano padrão (gratuito). Inclui os limites que valem agora.")
    public AssinaturaResponse minha() {
        return assinaturaService.minha(UsuarioAutenticado.nutricionistaId());
    }

    @PostMapping("/mudanca-plano")
    @Operation(summary = "Mudar de plano",
            description = "Plano pago: devolve a fatura e os dados de pagamento; o plano muda quando o pagamento for "
                    + "confirmado e o novo período começa nesse momento (sem pró-rata). Plano gratuito: equivale ao "
                    + "cancelamento.")
    public MudancaPlanoResponse mudar(@Valid @RequestBody MudancaPlanoRequest dados) {
        return mudancaPlanoService.mudar(UsuarioAutenticado.nutricionistaId(), dados);
    }

    @PostMapping("/cancelamento")
    @Operation(summary = "Cancelar a assinatura",
            description = "Volta ao plano gratuito no fim do período já pago (na hora, se a assinatura estiver "
                    + "inadimplente ou suspensa). Os dados não são apagados; valem os limites do plano gratuito.")
    public AssinaturaResponse cancelar() {
        return mudancaPlanoService.cancelar(UsuarioAutenticado.nutricionistaId());
    }

    @DeleteMapping("/cancelamento")
    @Operation(summary = "Desfazer o cancelamento agendado")
    public AssinaturaResponse desfazerCancelamento() {
        return mudancaPlanoService.desfazerCancelamento(UsuarioAutenticado.nutricionistaId());
    }
}
