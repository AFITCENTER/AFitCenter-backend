package br.com.afitcenter.avaliacao.model.enums;

public final class Enums {

    private Enums() {
    }

    /** Como a avaliação foi feita: aparelho de bioimpedância, fita/adipômetro ou os dois. */
    public enum MetodoAvaliacao { BIOIMPEDANCIA, ANTROPOMETRIA, MISTA }

    public enum TipoEquipamento { BIOIMPEDANCIA, BALANCA, ADIPOMETRO, FITA, OUTRO }

    public enum StatusMeta { ATIVA, ATINGIDA, CANCELADA }
}
