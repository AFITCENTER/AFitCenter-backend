package br.com.afitcenter.consulta.model.enums;

import java.util.EnumSet;
import java.util.Set;

public final class Enums {

    private Enums() {
    }

    public enum TipoConsulta { PRIMEIRA, RETORNO }

    public enum Modalidade { PRESENCIAL, ONLINE }

    /** Ciclo de vida da consulta. REALIZADA, CANCELADA e FALTOU são finais. */
    public enum StatusConsulta {
        AGENDADA, CONFIRMADA, REALIZADA, CANCELADA, FALTOU;

        public Set<StatusConsulta> proximosPermitidos() {
            return switch (this) {
                case AGENDADA -> EnumSet.of(CONFIRMADA, REALIZADA, CANCELADA, FALTOU);
                case CONFIRMADA -> EnumSet.of(REALIZADA, CANCELADA, FALTOU);
                case REALIZADA, CANCELADA, FALTOU -> EnumSet.noneOf(StatusConsulta.class);
            };
        }

        /** Ocupa o horário na agenda (consultas canceladas ou com falta liberam o horário). */
        public boolean ocupaHorario() {
            return this != CANCELADA && this != FALTOU;
        }

        public boolean permiteReagendar() {
            return this == AGENDADA || this == CONFIRMADA;
        }
    }

    /** O que o nutricionista registrou durante a consulta (requisito 2). */
    public enum CategoriaAnalise { ANAMNESE, QUEIXA, ACHADO, DIAGNOSTICO_NUTRICIONAL, CONDUTA, OUTRO }
}
