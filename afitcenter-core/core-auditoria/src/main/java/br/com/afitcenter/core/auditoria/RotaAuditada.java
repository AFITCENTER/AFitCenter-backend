package br.com.afitcenter.core.auditoria;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Recurso de uma rota: a raiz do agregado (primeiro segmento fixo depois de {@code /v1}, ignorando
 * {@code admin}) e o id que vem logo depois dele.
 *
 * <pre>
 * /v1/consultas/{consultaId}/analises/{id}  → consultas, consultaId
 * /v1/pacientes/{id}/condicoes              → pacientes, id
 * /v1/admin/planos/{id}                     → planos, id (admin)
 * /v1/assinatura                            → assinatura, sem id
 * </pre>
 *
 * <p>A rota inteira também vai para o registro, então a ação exata ({@code .../analises/{id}}) não se perde.</p>
 */
record RotaAuditada(String recurso, String recursoId, boolean admin) {

    static RotaAuditada de(String padrao, Map<String, String> variaveis) {
        List<String> segmentos = Arrays.stream(padrao.split("/")).filter(s -> !s.isEmpty()).toList();
        int i = 0;
        if (i < segmentos.size() && segmentos.get(i).matches("v\\d+")) i++;
        boolean admin = i < segmentos.size() && segmentos.get(i).equals("admin");
        if (admin) i++;
        if (i >= segmentos.size() || variavel(segmentos.get(i)))
            return new RotaAuditada(null, null, admin);

        String recurso = segmentos.get(i);
        String recursoId = null;
        if (i + 1 < segmentos.size() && variavel(segmentos.get(i + 1)))
            recursoId = variaveis.get(nome(segmentos.get(i + 1)));
        return new RotaAuditada(recurso, recursoId, admin);
    }

    private static boolean variavel(String segmento) {
        return segmento.startsWith("{") && segmento.endsWith("}");
    }

    /** {@code {id}} → {@code id}; {@code {id:\d+}} → {@code id}. */
    private static String nome(String segmento) {
        String nome = segmento.substring(1, segmento.length() - 1);
        int doisPontos = nome.indexOf(':');
        return doisPontos < 0 ? nome : nome.substring(0, doisPontos);
    }
}
