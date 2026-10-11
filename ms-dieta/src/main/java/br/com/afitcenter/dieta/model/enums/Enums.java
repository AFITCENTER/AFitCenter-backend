package br.com.afitcenter.dieta.model.enums;

import java.util.EnumSet;
import java.util.Set;

public final class Enums {

    private Enums() {
    }

    /** Origem do alimento. TACO e TBCA são a base oficial (somente leitura); PROPRIO tem dono. */
    public enum FonteAlimento { TACO, TBCA, PROPRIO }

    /** Ciclo de vida da dieta do paciente. ENCERRADA é final (histórico): para mudar, crie uma nova versão. */
    public enum StatusDieta {
        RASCUNHO, ATIVA, ENCERRADA;

        public Set<StatusDieta> proximosPermitidos() {
            return switch (this) {
                case RASCUNHO -> EnumSet.of(ATIVA, ENCERRADA);
                case ATIVA -> EnumSet.of(ENCERRADA);
                case ENCERRADA -> EnumSet.noneOf(StatusDieta.class);
            };
        }
    }

    /** Por que um item da dieta conflita com uma condição do paciente. */
    public enum MotivoRestricao { ALIMENTO, GRUPO }
}
