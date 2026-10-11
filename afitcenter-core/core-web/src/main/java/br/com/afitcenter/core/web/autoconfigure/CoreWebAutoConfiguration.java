package br.com.afitcenter.core.web.autoconfigure;

import br.com.afitcenter.core.auth.jwt.JwtVerificador;
import br.com.afitcenter.core.web.erro.TratadorExcecoes;
import br.com.afitcenter.core.web.erro.TratadorExcecoesFeign;
import br.com.afitcenter.core.web.feign.TokenInternoFeignInterceptor;
import br.com.afitcenter.core.web.seguranca.AutenticacaoRequisicaoFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Componentes comuns dos microsserviços servlet. Basta adicionar {@code core-web} ao serviço e
 * declarar o {@code SecurityFilterChain} com {@link br.com.afitcenter.core.web.seguranca.SegurancaPadrao}.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(AfitcenterProperties.class)
public class CoreWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtVerificador jwtVerificador(AfitcenterProperties props) {
        return new JwtVerificador(props.chavePublica(), props.issuer());
    }

    @Bean
    @ConditionalOnMissingBean
    public AutenticacaoRequisicaoFilter autenticacaoRequisicaoFilter(JwtVerificador verificador, AfitcenterProperties props) {
        return new AutenticacaoRequisicaoFilter(verificador, props.tokenInterno());
    }

    /** O filtro roda só dentro do SecurityFilterChain; sem isto o Boot o registraria também no container. */
    @Bean
    public FilterRegistrationBean<AutenticacaoRequisicaoFilter> autenticacaoRequisicaoFilterRegistro(
            AutenticacaoRequisicaoFilter filter) {
        FilterRegistrationBean<AutenticacaoRequisicaoFilter> registro = new FilterRegistrationBean<>(filter);
        registro.setEnabled(false);
        return registro;
    }

    @Bean
    @ConditionalOnMissingBean
    public TratadorExcecoes tratadorExcecoes() {
        return new TratadorExcecoes();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "feign.FeignException")
    static class FeignConfiguration {

        @Bean
        @ConditionalOnMissingBean
        TratadorExcecoesFeign tratadorExcecoesFeign() {
            return new TratadorExcecoesFeign();
        }

        @Bean
        @ConditionalOnMissingBean
        TokenInternoFeignInterceptor tokenInternoFeignInterceptor(AfitcenterProperties props) {
            return new TokenInternoFeignInterceptor(props.tokenInterno());
        }
    }
}
