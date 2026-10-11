package br.com.afitcenter.core.mensageria;

/**
 * Exchange e routing keys dos eventos de domínio.
 *
 * <p>Todos os eventos vão para um único exchange do tipo topic. Cada consumidor tem a própria
 * fila, ligada aos padrões que lhe interessam ({@code paciente.*}), com DLQ para mensagens que
 * falharam depois das novas tentativas.</p>
 */
public final class Eventos {

    public static final String EXCHANGE = "afitcenter.eventos";
    public static final String DLX = "afitcenter.eventos.dlx";

    public static final String PACIENTE_CRIADO = "paciente.criado";
    public static final String PACIENTE_ATUALIZADO = "paciente.atualizado";
    public static final String PACIENTE_EXCLUIDO = "paciente.excluido";
    public static final String PADRAO_PACIENTE = "paciente.*";

    public static final String CONSULTORIO_CRIADO = "consultorio.criado";
    public static final String CONSULTORIO_ATUALIZADO = "consultorio.atualizado";
    public static final String CONSULTORIO_DESATIVADO = "consultorio.desativado";
    public static final String PADRAO_CONSULTORIO = "consultorio.*";

    private Eventos() {
    }
}
