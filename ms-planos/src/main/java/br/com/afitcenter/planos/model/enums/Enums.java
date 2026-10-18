package br.com.afitcenter.planos.model.enums;

import java.time.Instant;
import java.time.ZoneOffset;

public final class Enums {

    private Enums() {
    }

    /** Periodicidade da cobrança de um plano pago. */
    public enum Ciclo {
        MENSAL, ANUAL;

        /** Fim do período que começa em {@code inicio} (mês e ano de calendário, em UTC). */
        public Instant somar(Instant inicio) {
            var data = inicio.atOffset(ZoneOffset.UTC);
            return (this == MENSAL ? data.plusMonths(1) : data.plusYears(1)).toInstant();
        }
    }

    /**
     * Situação da assinatura. Cancelar não é uma situação: a assinatura volta ao plano padrão (gratuito) no
     * fim do período pago e continua existindo.
     *
     * <ul>
     *     <li>ATIVA: em dia (ou no plano gratuito, que não vence);</li>
     *     <li>INADIMPLENTE: o período venceu sem pagamento da renovação; os limites do plano continuam valendo
     *     durante a carência;</li>
     *     <li>SUSPENSA: a carência acabou; valem os limites do plano padrão até o pagamento.</li>
     * </ul>
     */
    public enum StatusAssinatura { ATIVA, INADIMPLENTE, SUSPENSA }

    /** MUDANCA_PLANO: o nutricionista escolheu um plano pago. RENOVACAO: próximo período do plano atual. */
    public enum TipoFatura { MUDANCA_PLANO, RENOVACAO }

    public enum StatusFatura { ABERTA, PAGA, CANCELADA, ESTORNADA }

    /** Mesmos valores do ms-pagamentos. O cartão é digitado na página do provedor, nunca passa por aqui. */
    public enum FormaPagamento { PIX, BOLETO, CARTAO_CREDITO }
}
