package br.com.afitcenter.core.teste;

/**
 * Par de chaves ES256 usado SOMENTE nos testes automatizados. Não tem valor fora deles:
 * os ambientes reais recebem chaves próprias por variável de ambiente.
 */
public final class ChavesTeste {

    public static final String PRIVADA =
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgB1x6dLA+8w84LgtdmK3YmmgiCzinew5ahEyeCP0c2bGhRANCAASF7cP98vAmHFqvph5JBqJJB1w2+djaKOPRw8uHiVxQkBNL6QzAYUs/ojurY8ja5fXf3o9AepAugWk0zDdfbfl+";

    public static final String PUBLICA =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEhe3D/fLwJhxar6YeSQaiSQdcNvnY2ijj0cPLh4lcUJATS+kMwGFLP6I7q2PI2uX1396PQHqQLoFpNMw3X235fg==";

    public static final String ISSUER = "afitcenter-teste";

    public static final String TOKEN_INTERNO = "token-interno-teste";

    private ChavesTeste() {
    }
}
