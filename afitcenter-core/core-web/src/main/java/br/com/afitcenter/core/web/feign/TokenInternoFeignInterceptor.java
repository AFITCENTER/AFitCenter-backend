package br.com.afitcenter.core.web.feign;

import br.com.afitcenter.core.base.http.InternalHeaders;
import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * Chamadas serviço-a-serviço levam apenas o token interno. O JWT do usuário nunca é repassado:
 * com ele, o destino trataria a chamada como usuário e negaria o acesso às rotas {@code /internal/**}.
 */
public class TokenInternoFeignInterceptor implements RequestInterceptor {

    private final String tokenInterno;

    public TokenInternoFeignInterceptor(String tokenInterno) {
        this.tokenInterno = tokenInterno;
    }

    @Override
    public void apply(RequestTemplate template) {
        template.removeHeader("Authorization");
        template.header(InternalHeaders.INTERNAL_TOKEN, tokenInterno);
    }
}
