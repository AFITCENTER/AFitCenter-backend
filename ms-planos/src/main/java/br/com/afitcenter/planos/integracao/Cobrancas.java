package br.com.afitcenter.planos.integracao;

import br.com.afitcenter.core.web.erro.ConflitoException;
import br.com.afitcenter.core.web.erro.RegraNegocioException;
import br.com.afitcenter.planos.model.dto.Dtos.CobrancaResponse;
import br.com.afitcenter.planos.model.dto.Dtos.CriarCobrancaRequest;
import br.com.afitcenter.planos.model.dto.Dtos.NutricionistaInternoResponse;
import br.com.afitcenter.planos.model.dto.Dtos.Pagador;
import br.com.afitcenter.planos.model.entity.Fatura;
import br.com.afitcenter.planos.model.enums.Enums.Ciclo;
import br.com.afitcenter.planos.model.enums.Enums.TipoFatura;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Cobranças das faturas no ms-pagamentos. Chame fora de transação de banco: é uma chamada de rede. */
@Component
@RequiredArgsConstructor
public class Cobrancas {

    /** Origem das cobranças de assinatura; os eventos de outras origens são ignorados pelo ms-planos. */
    public static final String ORIGEM = "ASSINATURA";

    private final CobrancaInternoClient client;
    private final NutricionistaInternoClient nutricionistas;

    /** O pagador (nome, CPF e e-mail) vem do ms-nutricionista a cada emissão: o ms-planos não guarda esses dados. */
    public CobrancaResponse emitir(Fatura fatura) {
        return client.criar(new CriarCobrancaRequest(fatura.chaveIdempotencia(), fatura.getNutricionistaId(), ORIGEM,
                fatura.getId(), descricao(fatura), fatura.getValor(), fatura.getVencimento(),
                fatura.getFormaPagamento(), pagador(fatura.getNutricionistaId())));
    }

    /** Cobrança inexistente: nada a cancelar. Já paga: 409, o pagamento ainda vai chegar por evento. */
    public void cancelar(UUID cobrancaId) {
        try {
            client.cancelar(cobrancaId);
        } catch (FeignException.Conflict e) {
            throw new ConflitoException("Esta fatura já foi paga; aguarde a confirmação do pagamento.");
        } catch (FeignException.NotFound e) {
            // Nunca chegou a ser criada no ms-pagamentos.
        }
    }

    private Pagador pagador(UUID nutricionistaId) {
        try {
            NutricionistaInternoResponse n = nutricionistas.buscar(nutricionistaId);
            return new Pagador(n.nome(), n.cpf(), n.email());
        } catch (FeignException.NotFound e) {
            throw new RegraNegocioException("Perfil de nutricionista não encontrado para emitir a cobrança.");
        }
    }

    static String descricao(Fatura fatura) {
        String ciclo = fatura.getCiclo() == Ciclo.ANUAL ? "anual" : "mensal";
        String tipo = fatura.getTipo() == TipoFatura.RENOVACAO ? "renovação" : "assinatura";
        return "AFitCenter - " + tipo + " do plano " + fatura.getPlano().getNome() + " (" + ciclo + ")";
    }
}
