package br.com.afitcenter.core.mensageria.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * Estado atual da assinatura do nutricionista, com os limites que valem agora.
 *
 * <p>Os limites já consideram a situação: assinatura suspensa por falta de pagamento carrega os limites
 * do plano padrão (gratuito). Assim os consumidores só comparam o uso com o limite, sem conhecer regras
 * de cobrança. {@code null} em um limite = ilimitado. Mesma regra de versão do {@link PacienteEvento}.</p>
 */
public record AssinaturaEvento(
        UUID eventoId,
        TipoAlteracao tipo,
        Instant ocorridoEm,
        UUID assinaturaId,
        UUID nutricionistaId,
        String planoCodigo,
        String status,
        Integer limitePacientes,
        Integer limiteConsultorios,
        long versao
) {
}
