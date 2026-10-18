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

    public static final String ASSINATURA_CRIADA = "assinatura.criada";
    public static final String ASSINATURA_ATUALIZADA = "assinatura.atualizada";
    public static final String PADRAO_ASSINATURA = "assinatura.*";

    public static final String COBRANCA_PAGA = "cobranca.paga";
    public static final String COBRANCA_RECUSADA = "cobranca.recusada";
    public static final String COBRANCA_VENCIDA = "cobranca.vencida";
    public static final String COBRANCA_CANCELADA = "cobranca.cancelada";
    public static final String COBRANCA_ESTORNADA = "cobranca.estornada";
    public static final String PADRAO_COBRANCA = "cobranca.*";

    /** Trilha de auditoria: todos os serviços publicam, só o ms-auditoria consome. */
    public static final String AUDITORIA_REGISTRO = "auditoria.registro";
    public static final String PADRAO_AUDITORIA = "auditoria.*";

    private Eventos() {
    }
}
