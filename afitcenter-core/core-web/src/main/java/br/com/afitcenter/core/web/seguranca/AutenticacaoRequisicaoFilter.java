package br.com.afitcenter.core.web.seguranca;

import br.com.afitcenter.core.auth.jwt.JwtVerificador;
import br.com.afitcenter.core.auth.jwt.TokenAcesso;
import br.com.afitcenter.core.auth.jwt.TokenInvalidoException;
import br.com.afitcenter.core.base.http.InternalHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Autentica as requisições que chegam aos microsserviços.
 *
 * <ul>
 *     <li>Com {@code Authorization}: o próprio serviço valida o JWT com a chave pública e concede
 *     {@code USER} e os papéis do token, mas <b>nunca</b> {@code INTERNAL}, mesmo em rota interna.
 *     Um serviço comprometido não consegue se passar por um usuário porque não tem a chave privada.</li>
 *     <li>Sem JWT, em rota interna ({@code /internal/**} e Actuator, exceto health/info): exige o
 *     {@code X-Internal-Token} e concede {@code INTERNAL}.</li>
 *     <li>Sem nenhum dos dois: segue anônima; o {@code SecurityFilterChain} decide se a rota é pública.</li>
 * </ul>
 *
 * <p>Registrado só dentro do {@code SecurityFilterChain} (a auto-configuração desliga o registro
 * direto no container servlet, que o faria rodar duas vezes).</p>
 */
public class AutenticacaoRequisicaoFilter extends OncePerRequestFilter {

    public static final String AUTHORITY_INTERNAL = "INTERNAL";
    public static final String AUTHORITY_USER = "USER";
    public static final String PRINCIPAL_INTERNO = "servico-interno";

    private final JwtVerificador jwtVerificador;
    private final byte[] tokenInterno;

    public AutenticacaoRequisicaoFilter(JwtVerificador jwtVerificador, String tokenInterno) {
        if (tokenInterno == null || tokenInterno.isBlank())
            throw new IllegalStateException("A propriedade afitcenter.seguranca.token-interno precisa ser configurada.");
        this.jwtVerificador = jwtVerificador;
        this.tokenInterno = tokenInterno.getBytes(StandardCharsets.UTF_8);
    }

    /** Rotas que só outros microsserviços acessam. Caminho já decodificado. */
    static boolean isRotaInterna(String path) {
        if (path.equals("/internal") || path.startsWith("/internal/")) return true;
        boolean actuator = path.equals("/actuator") || path.startsWith("/actuator/");
        return actuator && !path.equals("/actuator/info")
                && !path.equals("/actuator/health") && !path.startsWith("/actuator/health/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

            if (authorization != null && !authorization.isBlank()) {
                final TokenAcesso token;
                try {
                    token = jwtVerificador.verificar(authorization);
                } catch (TokenInvalidoException e) {
                    RespostaProblema.escrever(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                            "Token ausente, inválido ou expirado.");
                    return;
                }
                List<GrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority(AUTHORITY_USER));
                token.papeis().forEach(papel -> authorities.add(new SimpleGrantedAuthority(papel.name())));
                autenticar(token, authorities);
            } else if (isRotaInterna(caminho(request))) {
                if (!tokenInternoValido(request.getHeader(InternalHeaders.INTERNAL_TOKEN))) {
                    RespostaProblema.escrever(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                            "Token interno ausente ou inválido.");
                    return;
                }
                autenticar(PRINCIPAL_INTERNO, List.of(new SimpleGrantedAuthority(AUTHORITY_INTERNAL)));
            }

            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static void autenticar(Object principal, List<GrantedAuthority> authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private boolean tokenInternoValido(String recebido) {
        return recebido != null && MessageDigest.isEqual(tokenInterno, recebido.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Caminho decodificado e sem parâmetros de matriz ({@code ;...}), o mesmo que o Spring MVC usa
     * para achar o controller. Evita que {@code /%69nternal/...} escape da regra.
     */
    private static String caminho(HttpServletRequest request) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return path.isEmpty() ? "/" : path;
    }
}
