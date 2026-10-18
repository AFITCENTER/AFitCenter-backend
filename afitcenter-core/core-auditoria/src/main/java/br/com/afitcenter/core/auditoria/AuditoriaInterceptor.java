package br.com.afitcenter.core.auditoria;

import br.com.afitcenter.core.auth.jwt.TokenAcesso;
import br.com.afitcenter.core.base.security.Papel;
import br.com.afitcenter.core.mensageria.evento.AuditoriaEvento;
import br.com.afitcenter.core.web.seguranca.AutenticacaoRequisicaoFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UrlPathHelper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registra cada requisição {@code /v1/**} que chega a um controller: método, padrão da rota, variáveis da
 * rota, status da resposta, quem fez (do JWT) e o IP.
 *
 * <ul>
 *     <li>Leituras ({@code GET}) só com {@code afitcenter.auditoria.leituras=true}, ligado nos serviços com
 *     prontuário (LGPD: quem acessou os dados de saúde do paciente). Catálogos sem dado pessoal (alimentos,
 *     métricas...) ficam de fora com {@code afitcenter.auditoria.leituras-ignoradas} (padrões Ant).</li>
 *     <li>Corpo e query string nunca são registrados: podem ter nome, CPF ou dados clínicos.</li>
 *     <li>Requisições recusadas pelo filtro de segurança (401 de token inválido) não chegam aqui; o Gateway
 *     já barra a maioria delas.</li>
 * </ul>
 *
 * <p>Roda depois da resposta, numa transação própria. Uma falha ao gravar vai para o log e não muda a
 * resposta, que já foi enviada.</p>
 */
@Slf4j
public class AuditoriaInterceptor implements HandlerInterceptor {

    private static final AntPathMatcher PADROES = new AntPathMatcher();

    private final Auditoria auditoria;
    private final boolean leituras;
    private final List<String> leiturasIgnoradas;

    public AuditoriaInterceptor(Auditoria auditoria, boolean leituras, List<String> leiturasIgnoradas) {
        this.auditoria = auditoria;
        this.leituras = leituras;
        this.leiturasIgnoradas = leiturasIgnoradas.stream().map(String::trim).filter(p -> !p.isEmpty()).toList();
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        if (!(handler instanceof HandlerMethod)) return;
        String metodo = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(metodo)) return;
        if (Acoes.LEITURA.equals(Acoes.doMetodo(metodo)) && (!leituras || ignorada(request))) return;
        if (Boolean.TRUE.equals(request.getAttribute(AuditoriaRequisicao.IGNORAR))) return;

        try {
            auditoria.registrarRequisicao(montar(request, response, ex));
        } catch (RuntimeException e) {
            log.error("Não foi possível gravar o registro de auditoria de {} {}.", metodo, request.getRequestURI(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private AuditoriaEvento montar(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        String padrao = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (padrao == null) padrao = request.getRequestURI();
        var variaveis = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variaveis == null) variaveis = Map.of();
        RotaAuditada rota = RotaAuditada.de(padrao, variaveis);

        String respostaId = (String) request.getAttribute(AuditoriaRequisicao.RESPOSTA_ID);
        String recursoId = primeiro((String) request.getAttribute(AuditoriaRequisicao.RECURSO_ID), rota.recursoId(),
                respostaId);

        Map<String, String> detalhes = new LinkedHashMap<>(variaveis);
        if (respostaId != null && !respostaId.equals(recursoId)) detalhes.put("respostaId", respostaId);
        if (rota.admin()) detalhes.put("admin", "true");
        var extras = (Map<String, String>) request.getAttribute(AuditoriaRequisicao.DETALHES);
        if (extras != null) detalhes.putAll(extras);

        UUID pacienteId = primeiro((UUID) request.getAttribute(AuditoriaRequisicao.PACIENTE),
                uuid(variaveis.get("pacienteId")),
                "pacientes".equals(rota.recurso()) ? uuid(recursoId) : null,
                (UUID) request.getAttribute(AuditoriaRequisicao.RESPOSTA_PACIENTE));

        UUID usuarioId = null;
        UUID nutricionistaId = null;
        String papel = Acoes.PAPEL_ANONIMO;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TokenAcesso token) {
            usuarioId = token.usuarioId();
            papel = String.join(",", Papel.nomes(token.papeis()));
            if (token.possui(Papel.NUTRICIONISTA)) nutricionistaId = token.usuarioId();
        } else if (auth != null && AutenticacaoRequisicaoFilter.PRINCIPAL_INTERNO.equals(auth.getPrincipal())) {
            papel = Acoes.PAPEL_INTERNO;
        }
        usuarioId = primeiro((UUID) request.getAttribute(AuditoriaRequisicao.USUARIO), usuarioId);
        nutricionistaId = primeiro((UUID) request.getAttribute(AuditoriaRequisicao.NUTRICIONISTA), nutricionistaId);

        int status = response.getStatus();
        if (ex != null && status < 400) status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        String acao = primeiro((String) request.getAttribute(AuditoriaRequisicao.ACAO), Acoes.doMetodo(request.getMethod()));

        return new AuditoriaEvento(UUID.randomUUID(), Instant.now(), auditoria.servico(), acao, rota.recurso(),
                recursoId, pacienteId, nutricionistaId, usuarioId, papel, request.getMethod(), padrao, status,
                request.getRemoteAddr(), detalhes);
    }

    private boolean ignorada(HttpServletRequest request) {
        String caminho = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return leiturasIgnoradas.stream().anyMatch(padrao -> PADROES.match(padrao, caminho));
    }

    @SafeVarargs
    private static <T> T primeiro(T... valores) {
        for (T valor : valores)
            if (valor != null) return valor;
        return null;
    }

    private static UUID uuid(String valor) {
        if (valor == null) return null;
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
