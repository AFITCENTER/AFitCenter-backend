package br.com.afitcenter.core.assinatura;

import br.com.afitcenter.core.mensageria.evento.AssinaturaEvento;

import java.util.UUID;

/**
 * Assinatura replicada nos serviços que aplicam os limites do plano. É também o formato de
 * {@code GET /internal/assinaturas/{nutricionistaId}} do ms-planos.
 *
 * <p>Os limites já são os efetivos (assinatura suspensa traz os do plano gratuito). {@code null} = ilimitado.</p>
 */
public record AssinaturaRef(
        UUID nutricionistaId,
        UUID assinaturaId,
        String planoCodigo,
        String status,
        Integer limitePacientes,
        Integer limiteConsultorios,
        long versao
) {

    public static AssinaturaRef de(AssinaturaEvento evento) {
        return new AssinaturaRef(evento.nutricionistaId(), evento.assinaturaId(), evento.planoCodigo(),
                evento.status(), evento.limitePacientes(), evento.limiteConsultorios(), evento.versao());
    }

    public Integer limite(Limite limite) {
        return switch (limite) {
            case PACIENTES -> limitePacientes;
            case CONSULTORIOS -> limiteConsultorios;
        };
    }

    public boolean suspensa() {
        return "SUSPENSA".equals(status);
    }
}
