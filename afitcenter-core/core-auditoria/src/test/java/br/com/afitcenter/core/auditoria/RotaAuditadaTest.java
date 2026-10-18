package br.com.afitcenter.core.auditoria;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RotaAuditadaTest {

    @Test
    void recursoEARaizDoAgregadoComOIdQueVemDepois() {
        assertThat(RotaAuditada.de("/v1/consultas/{consultaId}/analises/{id}", Map.of("consultaId", "c1", "id", "a1")))
                .isEqualTo(new RotaAuditada("consultas", "c1", false));
        assertThat(RotaAuditada.de("/v1/pacientes/{id}/condicoes", Map.of("id", "p1")))
                .isEqualTo(new RotaAuditada("pacientes", "p1", false));
    }

    @Test
    void colecaoSemIdEAdmin() {
        assertThat(RotaAuditada.de("/v1/pacientes", Map.of())).isEqualTo(new RotaAuditada("pacientes", null, false));
        assertThat(RotaAuditada.de("/v1/admin/planos/{id}", Map.of("id", "3")))
                .isEqualTo(new RotaAuditada("planos", "3", true));
    }

    @Test
    void variavelComExpressaoRegular() {
        assertThat(RotaAuditada.de("/v1/alimentos/{id:\\d+}", Map.of("id", "42")).recursoId()).isEqualTo("42");
    }

    @Test
    void rotaSemRecursoFixo() {
        assertThat(RotaAuditada.de("/v1", Map.of())).isEqualTo(new RotaAuditada(null, null, false));
    }

    @Test
    void acaoPadraoPorMetodo() {
        assertThat(Acoes.doMetodo("GET")).isEqualTo(Acoes.LEITURA);
        assertThat(Acoes.doMetodo("post")).isEqualTo(Acoes.CRIACAO);
        assertThat(Acoes.doMetodo("PATCH")).isEqualTo(Acoes.ALTERACAO);
        assertThat(Acoes.doMetodo("DELETE")).isEqualTo(Acoes.EXCLUSAO);
    }
}
