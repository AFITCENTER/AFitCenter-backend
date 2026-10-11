package br.com.afitcenter.paciente.model.enums;

/** Domínios fechados do ms-paciente (espelham os CHECKs da migração). */
public final class Enums {

    private Enums() {
    }

    /** Sexo biológico, usado nas equações de composição corporal e gasto energético. */
    public enum Sexo { F, M }

    public enum StatusPaciente { ATIVO, INATIVO }

    /** Fator de atividade para o gasto energético total (GET = TMB × fator). */
    public enum NivelAtividade { SEDENTARIO, LEVE, MODERADO, INTENSO, MUITO_INTENSO }

    public enum TipoContato { CELULAR, WHATSAPP, TELEFONE, EMAIL, INSTAGRAM, OUTRO }

    public enum Gravidade { LEVE, MODERADA, GRAVE }

    public enum TipoMedicamento { MEDICAMENTO, SUPLEMENTO, FITOTERAPICO }
}
