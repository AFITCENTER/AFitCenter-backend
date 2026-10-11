package br.com.afitcenter.core.web.seguranca;

import br.com.afitcenter.core.auth.jwt.JwtVerificador;
import br.com.afitcenter.core.auth.jwt.TokenAcesso;
import br.com.afitcenter.core.base.http.InternalHeaders;
import br.com.afitcenter.core.base.security.Papel;
import br.com.afitcenter.core.teste.ChavesTeste;
import br.com.afitcenter.core.teste.TokensTeste;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutenticacaoRequisicaoFilterTest {

    private final AutenticacaoRequisicaoFilter filter = new AutenticacaoRequisicaoFilter(
            new JwtVerificador(ChavesTeste.PUBLICA, ChavesTeste.ISSUER), ChavesTeste.TOKEN_INTERNO);

    @Test
    void jwtValidoConcedeUserEPapeisMasNuncaInternal() throws Exception {
        UUID nutri = UUID.randomUUID();
        var request = requisicao("/internal/pacientes/1");
        request.addHeader("Authorization", TokensTeste.nutricionista(nutri));

        Authentication auth = executar(request, new MockHttpServletResponse());

        assertThat(auth.getPrincipal()).isEqualTo(new TokenAcesso(nutri, java.util.Set.of(Papel.NUTRICIONISTA)));
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("USER", "NUTRICIONISTA");
    }

    @Test
    void jwtInvalidoResponde401SemSeguirACadeia() throws Exception {
        var request = requisicao("/v1/pacientes");
        request.addHeader("Authorization", "Bearer nao-e-um-jwt");
        var response = new MockHttpServletResponse();

        Authentication auth = executar(request, response);

        assertThat(auth).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
    }

    @Test
    void rotaInternaComTokenInternoConcedeInternal() throws Exception {
        var request = requisicao("/internal/pacientes/1");
        request.addHeader(InternalHeaders.INTERNAL_TOKEN, ChavesTeste.TOKEN_INTERNO);

        Authentication auth = executar(request, new MockHttpServletResponse());

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("INTERNAL");
    }

    @Test
    void rotaInternaSemTokenOuComTokenErradoResponde401() throws Exception {
        var semToken = new MockHttpServletResponse();
        executar(requisicao("/internal/pacientes/1"), semToken);

        var request = requisicao("/actuator/env");
        request.addHeader(InternalHeaders.INTERNAL_TOKEN, "errado");
        var tokenErrado = new MockHttpServletResponse();
        executar(request, tokenErrado);

        assertThat(semToken.getStatus()).isEqualTo(401);
        assertThat(tokenErrado.getStatus()).isEqualTo(401);
    }

    @Test
    void healthEInfoNaoSaoRotasInternas() {
        assertThat(AutenticacaoRequisicaoFilter.isRotaInterna("/actuator/health")).isFalse();
        assertThat(AutenticacaoRequisicaoFilter.isRotaInterna("/actuator/health/liveness")).isFalse();
        assertThat(AutenticacaoRequisicaoFilter.isRotaInterna("/actuator/info")).isFalse();
        assertThat(AutenticacaoRequisicaoFilter.isRotaInterna("/actuator/env")).isTrue();
        assertThat(AutenticacaoRequisicaoFilter.isRotaInterna("/internal")).isTrue();
        assertThat(AutenticacaoRequisicaoFilter.isRotaInterna("/internalx")).isFalse();
    }

    @Test
    void semCredenciaisSegueAnonimo() throws Exception {
        var response = new MockHttpServletResponse();
        assertThat(executar(requisicao("/v1/pacientes"), response)).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void nutricionistaIdExigePapelNutricionista() throws Exception {
        UUID paciente = UUID.randomUUID();
        var request = requisicao("/v1/qualquer");
        request.addHeader("Authorization", TokensTeste.comPapeis(paciente, Papel.PACIENTE));
        AtomicReference<Throwable> erro = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertThat(UsuarioAutenticado.usuarioId()).isEqualTo(paciente);
            try {
                UsuarioAutenticado.nutricionistaId();
            } catch (Throwable t) {
                erro.set(t);
            }
        });

        assertThat(erro.get()).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(UsuarioAutenticado::usuarioId)
                .as("o contexto é limpo ao final da requisição")
                .isInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
    }

    private static MockHttpServletRequest requisicao(String path) {
        var request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
    }

    /** Executa o filtro e devolve a autenticação vista pela cadeia (null se a cadeia não rodou ou foi anônima). */
    private Authentication executar(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        AtomicReference<Authentication> vista = new AtomicReference<>();
        filter.doFilter(request, response,
                (req, res) -> vista.set(SecurityContextHolder.getContext().getAuthentication()));
        return vista.get();
    }
}
