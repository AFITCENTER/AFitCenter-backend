package br.com.afitcenter.core.base.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Papéis de acesso emitidos no JWT (claim {@code roles}).
 *
 * <p>Na v1 só o nutricionista usa o sistema; {@code PACIENTE} já existe para o futuro portal.</p>
 */
public enum Papel {
    NUTRICIONISTA,
    PACIENTE,
    ADMIN;

    public static final String CLAIM_ROLES = "roles";

    /** Converte nomes em papéis conhecidos. Valores desconhecidos são ignorados, nunca concedidos. */
    public static Set<Papel> parse(Collection<String> valores) {
        if (valores == null) return Set.of();
        Set<Papel> papeis = new LinkedHashSet<>();
        for (String valor : valores) {
            if (valor == null) continue;
            String normalizado = valor.trim().toUpperCase(Locale.ROOT);
            for (Papel papel : values()) {
                if (papel.name().equals(normalizado)) papeis.add(papel);
            }
        }
        return Set.copyOf(papeis);
    }

    public static List<String> nomes(Collection<Papel> papeis) {
        return papeis.stream().map(Papel::name).sorted().toList();
    }
}
