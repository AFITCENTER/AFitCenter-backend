package br.com.afitcenter.core.assinatura;

/** Recursos limitados pelo plano. Cada um é aplicado pelo serviço dono do dado. */
public enum Limite {

    /** Fichas de paciente não excluídas (ms-paciente). */
    PACIENTES("pacientes"),

    /** Consultórios ativos (ms-nutricionista). */
    CONSULTORIOS("consultórios");

    private final String descricao;

    Limite(String descricao) {
        this.descricao = descricao;
    }

    public String descricao() {
        return descricao;
    }
}
