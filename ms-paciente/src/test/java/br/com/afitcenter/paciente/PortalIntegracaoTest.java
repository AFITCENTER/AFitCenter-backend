package br.com.afitcenter.paciente;

import br.com.afitcenter.core.assinatura.AssinaturaInternoClient;
import br.com.afitcenter.core.base.security.Papel;
import br.com.afitcenter.core.teste.TesteIntegracao;
import br.com.afitcenter.core.teste.TokensTeste;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Portal do paciente: código de acesso gerado pelo nutricionista e fichas ligadas à conta do paciente. */
@TesteIntegracao
class PortalIntegracaoTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;

    /** Sem resposta (null): sem réplica, a criação de fichas segue sem limite. */
    @MockitoBean
    AssinaturaInternoClient assinaturaClient;

    private final UUID ana = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();
    private final UUID carlos = UUID.randomUUID();

    @Test
    void nutricionistaGeraOCodigoEOPacienteLigaAFichaAConta() throws Exception {
        UUID ficha = criarFicha(ana, "Carlos Portal");

        String codigo = gerarCodigo(ana, ficha).andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value(org.hamcrest.Matchers.matchesPattern("[A-Z2-9]{4}-[A-Z2-9]{4}")))
                .andExpect(jsonPath("$.expiraEm").isNotEmpty())
                .andReturn().getResponse().getContentAsString().transform(this::codigo);
        assertThat(jdbc.queryForObject("SELECT codigo_hash FROM codigo_acesso WHERE paciente_id = ?", String.class, ficha))
                .as("só o hash fica no banco").hasSize(64).doesNotContain(codigo.replace("-", ""));

        portal(carlos).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        // Minúsculas e sem hífen também valem.
        vincular(carlos, codigo.replace("-", "").toLowerCase(Locale.ROOT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ficha.toString()))
                .andExpect(jsonPath("$.nutricionistaId").value(ana.toString()))
                .andExpect(jsonPath("$.nomeExibicao").value("Carlos Portal"))
                .andExpect(jsonPath("$.vinculadaEm").isNotEmpty())
                .andExpect(jsonPath("$.observacoes").doesNotExist());

        portal(carlos).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(ficha.toString()));
        mvc.perform(get("/v1/pacientes/" + ficha).header("Authorization", TokensTeste.nutricionista(ana)))
                .andExpect(jsonPath("$.acessoAtivo").value(true))
                .andExpect(jsonPath("$.acessoDesde").isNotEmpty());

        // Uso único.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM codigo_acesso WHERE paciente_id = ?", Long.class, ficha)).isZero();
        vincular(UUID.randomUUID(), codigo).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void umaContaVeFichasDeVariosNutricionistasMasSoUmaDeCada() throws Exception {
        UUID daAna = criarFicha(ana, "Carlos na Ana");
        UUID outraDaAna = criarFicha(ana, "Carlos de novo na Ana");
        UUID doBruno = criarFicha(bruno, "Carlos no Bruno");

        vincular(carlos, codigoDe(ana, daAna)).andExpect(status().isCreated());
        vincular(carlos, codigoDe(ana, outraDaAna))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Sua conta já está ligada a uma ficha deste nutricionista."));
        vincular(carlos, codigoDe(bruno, doBruno)).andExpect(status().isCreated());

        portal(carlos).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nutricionistaId").value(ana.toString()))
                .andExpect(jsonPath("$[1].nutricionistaId").value(bruno.toString()));
        portal(UUID.randomUUID()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void codigoVencidoTrocadoOuDeFichaExcluidaNaoServe() throws Exception {
        UUID vencida = criarFicha(ana, "Vencida");
        String codigoVencido = codigoDe(ana, vencida);
        jdbc.update("UPDATE codigo_acesso SET expira_em = now() - interval '1 minute' WHERE paciente_id = ?", vencida);
        vincular(carlos, codigoVencido)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Código de acesso inválido ou vencido. Peça um novo ao seu nutricionista."));

        UUID trocada = criarFicha(ana, "Trocada");
        String primeiro = codigoDe(ana, trocada);
        String segundo = codigoDe(ana, trocada);
        vincular(carlos, primeiro).andExpect(status().isUnprocessableEntity()); // gerar outro invalida o anterior

        UUID excluida = criarFicha(bruno, "Excluída");
        String daExcluida = codigoDe(bruno, excluida);
        mvc.perform(delete("/v1/pacientes/" + excluida).header("Authorization", TokensTeste.nutricionista(bruno)))
                .andExpect(status().isNoContent());
        vincular(carlos, daExcluida).andExpect(status().isUnprocessableEntity());

        vincular(carlos, segundo).andExpect(status().isCreated());
    }

    @Test
    void desfazerOAcessoTiraAFichaDoPortalEPermiteNovoCodigo() throws Exception {
        UUID ficha = criarFicha(ana, "Com acesso");
        vincular(carlos, codigoDe(ana, ficha)).andExpect(status().isCreated());
        gerarCodigo(ana, ficha)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Este paciente já tem acesso ao portal. Desfaça o acesso atual para gerar outro código."));

        revogar(ana, ficha).andExpect(status().isNoContent());
        revogar(ana, ficha).andExpect(status().isNoContent()); // idempotente
        portal(carlos).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/v1/pacientes/" + ficha).header("Authorization", TokensTeste.nutricionista(ana)))
                .andExpect(jsonPath("$.acessoAtivo").value(false));

        // Código pendente também deixa de valer ao desfazer.
        String pendente = codigoDe(ana, ficha);
        revogar(ana, ficha).andExpect(status().isNoContent());
        vincular(carlos, pendente).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cadaPapelSoAlcancaAsPropriasRotas() throws Exception {
        UUID ficha = criarFicha(ana, "Isolada");

        gerarCodigo(bruno, ficha).andExpect(status().isNotFound());
        revogar(bruno, ficha).andExpect(status().isNotFound());

        mvc.perform(post("/v1/pacientes/" + ficha + "/acesso").header("Authorization", TokensTeste.paciente(carlos)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/pacientes").header("Authorization", TokensTeste.paciente(carlos)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/portal/fichas").header("Authorization", TokensTeste.nutricionista(ana)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/portal/fichas")).andExpect(status().isUnauthorized());

        // Nutricionista que também é paciente de um colega usa as duas áreas com o mesmo token.
        String ambos = TokensTeste.comPapeis(bruno, Papel.NUTRICIONISTA, Papel.PACIENTE);
        mvc.perform(get("/v1/pacientes").header("Authorization", ambos)).andExpect(status().isOk());
        mvc.perform(post("/v1/portal/vinculos").header("Authorization", ambos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(json.createObjectNode().put("codigo", codigoDe(ana, ficha)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nutricionistaId").value(ana.toString()));
    }

    @Test
    void muitosCodigosErradosBloqueiamAConta() throws Exception {
        String certo = codigoDe(ana, criarFicha(ana, "Alvo"));
        for (int i = 0; i < 5; i++)
            vincular(carlos, "ZZZZ-ZZZ" + (i + 2)).andExpect(status().isUnprocessableEntity());

        vincular(carlos, certo).andExpect(status().isTooManyRequests());
        vincular(UUID.randomUUID(), certo).andExpect(status().isCreated()); // outra conta não é afetada
    }

    @Test
    void vinculoEntraNaTrilhaDoNutricionistaSemOCodigo() throws Exception {
        UUID ficha = criarFicha(ana, "Trilha");
        String codigo = codigoDe(ana, ficha);
        vincular(carlos, codigo).andExpect(status().isCreated());

        JsonNode registro = json.readTree(jdbc.queryForObject("""
                SELECT payload::text FROM outbox_evento
                WHERE routing_key = 'auditoria.registro' AND payload->>'rota' = '/v1/portal/vinculos'
                  AND payload->>'usuarioId' = ?""", String.class, carlos.toString()));
        assertThat(registro.get("nutricionistaId").asText()).isEqualTo(ana.toString());
        assertThat(registro.get("pacienteId").asText()).isEqualTo(ficha.toString());
        assertThat(registro.get("papel").asText()).isEqualTo("PACIENTE");
        assertThat(registro.get("status").asInt()).isEqualTo(201);

        assertThat(jdbc.queryForList("""
                SELECT payload::text AS payload FROM outbox_evento
                WHERE routing_key = 'auditoria.registro' AND payload->>'pacienteId' = ?""", ficha.toString()))
                .isNotEmpty()
                .allSatisfy(r -> assertThat(r.get("payload").toString())
                        .doesNotContain(codigo).doesNotContain(codigo.replace("-", "")));
    }

    // ------------------------------------------------------------------ auxiliares

    private UUID criarFicha(UUID nutricionista, String nome) throws Exception {
        var corpo = json.createObjectNode()
                .put("nome", nome)
                .put("dataNascimento", "1990-05-20")
                .put("sexo", "M")
                .put("observacoes", "Anotação do nutricionista");
        String resposta = mvc.perform(post("/v1/pacientes").header("Authorization", TokensTeste.nutricionista(nutricionista))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(resposta).get("id").asText());
    }

    private ResultActions gerarCodigo(UUID nutricionista, UUID ficha) throws Exception {
        return mvc.perform(post("/v1/pacientes/" + ficha + "/acesso")
                .header("Authorization", TokensTeste.nutricionista(nutricionista)));
    }

    private String codigoDe(UUID nutricionista, UUID ficha) throws Exception {
        return gerarCodigo(nutricionista, ficha).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(this::codigo);
    }

    private ResultActions revogar(UUID nutricionista, UUID ficha) throws Exception {
        return mvc.perform(delete("/v1/pacientes/" + ficha + "/acesso")
                .header("Authorization", TokensTeste.nutricionista(nutricionista)));
    }

    private ResultActions vincular(UUID pessoa, String codigo) throws Exception {
        return mvc.perform(post("/v1/portal/vinculos").header("Authorization", TokensTeste.paciente(pessoa))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(json.createObjectNode().put("codigo", codigo))));
    }

    private ResultActions portal(UUID pessoa) throws Exception {
        return mvc.perform(get("/v1/portal/fichas").header("Authorization", TokensTeste.paciente(pessoa)));
    }

    private String codigo(String corpo) {
        try {
            return json.readTree(corpo).get("codigo").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
