package br.com.afitcenter.planos;

import br.com.afitcenter.core.teste.TokensTeste;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogoPlanosTest extends IntegracaoBase {

    private final String codigo = "TESTE_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

    @Test
    void catalogoEPublicoNaOrdemDeExibicao() throws Exception {
        mvc.perform(get("/v1/planos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("GRATUITO"))
                .andExpect(jsonPath("$[0].gratuito").value(true))
                .andExpect(jsonPath("$[0].padrao").value(true))
                .andExpect(jsonPath("$[?(@.codigo == 'CLINICA')].limitePacientes").value(org.hamcrest.Matchers.contains(
                        org.hamcrest.Matchers.nullValue())));
        mvc.perform(get("/v1/planos/" + planoId("PROFISSIONAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precoMensal").value(89.90));
    }

    @Test
    void administracaoExigePapelAdmin() throws Exception {
        mvc.perform(get("/v1/admin/planos")).andExpect(status().isUnauthorized());
        getComo(ana, "/v1/admin/planos").andExpect(status().isForbidden());
        comoAdmin("GET", "/v1/admin/planos", null).andExpect(status().isOk());
    }

    @Test
    void adminCriaAtualizaEDesativaPlano() throws Exception {
        String corpo = plano(codigo, "49.90", "499.00", 50);
        var criado = comoAdmin("POST", "/v1/admin/planos", corpo).andExpect(status().isCreated());
        int id = corpo(criado).get("id").asInt();
        comoAdmin("POST", "/v1/admin/planos", corpo).andExpect(status().isConflict());

        comoAdmin("PUT", "/v1/admin/planos/" + id, plano(codigo, "59.90", "599.00", 80))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precoMensal").value(59.90))
                .andExpect(jsonPath("$.limitePacientes").value(80));
        comoAdmin("PUT", "/v1/admin/planos/" + id, plano("OUTRO_CODIGO", "59.90", null, 80))
                .andExpect(status().isUnprocessableEntity());

        comoAdmin("PATCH", "/v1/admin/planos/" + id + "/ativo", "{\"ativo\":false}")
                .andExpect(jsonPath("$.ativo").value(false));
        mvc.perform(get("/v1/planos/" + id)).andExpect(status().isNotFound());
        postComo(ana, "/v1/assinatura/mudanca-plano", "{\"planoId\":%d,\"ciclo\":\"MENSAL\",\"formaPagamento\":\"PIX\"}"
                .formatted(id)).andExpect(status().isNotFound());
    }

    @Test
    void planoPadraoContinuaGratuitoEAtivo() throws Exception {
        int gratuito = planoId("GRATUITO");
        comoAdmin("PATCH", "/v1/admin/planos/" + gratuito + "/ativo", "{\"ativo\":false}")
                .andExpect(status().isUnprocessableEntity());
        comoAdmin("PUT", "/v1/admin/planos/" + gratuito, plano("GRATUITO", "9.90", null, 10))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("O plano padrão precisa continuar gratuito."));
        comoAdmin("POST", "/v1/admin/planos", plano(codigo, "0.00", "10.00", 5))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void novosLimitesSaoRepublicadosParaQuemAssinaOPlano() throws Exception {
        int id = corpo(comoAdmin("POST", "/v1/admin/planos", plano(codigo, "39.90", null, 20))
                .andExpect(status().isCreated())).get("id").asInt();
        UUID fatura = pedirMudanca(ana, codigo, "MENSAL");
        eventoCobranca(fatura, "PAGA", java.time.Instant.now());
        long versaoAntes = ultimoEvento(ana).get("versao").asLong();

        comoAdmin("PUT", "/v1/admin/planos/" + id, plano(codigo, "39.90", null, 25)).andExpect(status().isOk());

        var evento = ultimoEvento(ana);
        assertThat(evento.get("limitePacientes").asInt()).isEqualTo(25);
        assertThat(evento.get("versao").asLong()).isGreaterThan(versaoAntes);
        // Só preço não muda limite: nenhum evento novo.
        comoAdmin("PUT", "/v1/admin/planos/" + id, plano(codigo, "44.90", null, 25)).andExpect(status().isOk());
        assertThat(ultimoEvento(ana).get("versao").asLong()).isEqualTo(evento.get("versao").asLong());

        getComo(ana, "/v1/assinatura").andExpect(jsonPath("$.limites.pacientes").value(25));
        mvc.perform(get("/v1/assinatura").header("Authorization", TokensTeste.nutricionista(bruno)))
                .andExpect(jsonPath("$.plano.codigo").value("GRATUITO"));
    }

    @Test
    void planoForaDoCatalogoNaoPodeSerContratado() throws Exception {
        int pago = corpo(comoAdmin("POST", "/v1/admin/planos", plano(codigo, "29.90", null, 40, false))
                .andExpect(status().isCreated())).get("id").asInt();
        int cortesia = corpo(comoAdmin("POST", "/v1/admin/planos", plano(codigo + "_C", "0.00", null, null, false))
                .andExpect(status().isCreated())).get("id").asInt();

        postComo(ana, "/v1/assinatura/mudanca-plano", "{\"planoId\":%d,\"ciclo\":\"MENSAL\",\"formaPagamento\":\"PIX\"}"
                .formatted(pago)).andExpect(status().isNotFound());
        // Gratuito valeria na hora, sem pagamento.
        postComo(ana, "/v1/assinatura/mudanca-plano", "{\"planoId\":%d}".formatted(cortesia))
                .andExpect(status().isNotFound());
        getComo(ana, "/v1/assinatura").andExpect(jsonPath("$.plano.codigo").value("GRATUITO"));
        verify(cobrancaClient, never()).criar(any());

        // Publicado no catálogo, passa a poder ser contratado.
        comoAdmin("PUT", "/v1/admin/planos/" + pago, plano(codigo, "29.90", null, 40)).andExpect(status().isOk());
        postComo(ana, "/v1/assinatura/mudanca-plano", "{\"planoId\":%d,\"ciclo\":\"MENSAL\",\"formaPagamento\":\"PIX\"}"
                .formatted(pago)).andExpect(status().isOk());
    }

    private String plano(String codigo, String mensal, String anual, int pacientes) {
        return plano(codigo, mensal, anual, pacientes, true);
    }

    private String plano(String codigo, String mensal, String anual, Integer pacientes, boolean publico) {
        return """
                {"codigo":"%s","nome":"Plano %s","precoMensal":%s,"precoAnual":%s,"limitePacientes":%s,
                 "limiteConsultorios":2,"publico":%s,"ordem":99}""".formatted(codigo, codigo, mensal, anual, pacientes,
                publico);
    }
}
