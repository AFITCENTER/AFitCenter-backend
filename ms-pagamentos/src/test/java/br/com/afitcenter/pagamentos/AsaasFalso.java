package br.com.afitcenter.pagamentos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Servidor HTTP local no lugar da API v3 do Asaas, com clientes e cobranças em memória. Cobre só as rotas que o
 * {@code ProvedorAsaas} usa e confere a chave no header {@code access_token}.
 */
class AsaasFalso implements AutoCloseable {

    private final HttpServer servidor;
    private final String chave;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, ObjectNode> clientes = new ConcurrentHashMap<>();
    private final Map<String, ObjectNode> pagamentos = new ConcurrentHashMap<>();
    private final AtomicInteger sequencia = new AtomicInteger();

    /** Rotas chamadas, como {@code "POST /payments"}. */
    final List<String> chamadas = new CopyOnWriteArrayList<>();
    /** Quantas das próximas consultas de QR Code Pix respondem 500 (falha depois de criar a cobrança). */
    final AtomicInteger falhasPix = new AtomicInteger();

    private AsaasFalso(String chave) throws IOException {
        this.chave = chave;
        this.servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.servidor.createContext("/v3/", this::tratar);
        this.servidor.start();
    }

    static AsaasFalso iniciar(String chave) {
        try {
            return new AsaasFalso(chave);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v3";
    }

    ObjectNode pagamento(String id) {
        return pagamentos.get(id);
    }

    /** O pagador pagou no Asaas (o webhook é enviado à parte pelo teste). */
    void pagar(String id) {
        pagamentos.get(id).put("status", "RECEIVED");
    }

    long contar(String chamada) {
        return chamadas.stream().filter(chamada::equals).count();
    }

    @Override
    public void close() {
        servidor.stop(0);
    }

    // ------------------------------------------------------------------ rotas

    private void tratar(HttpExchange troca) throws IOException {
        try {
            String metodo = troca.getRequestMethod();
            String caminho = troca.getRequestURI().getPath().substring("/v3".length());
            chamadas.add(metodo + " " + caminho.replaceAll("/pay_\\w+", "/{id}"));
            if (!chave.equals(troca.getRequestHeaders().getFirst("access_token"))) {
                responder(troca, 401, "{\"errors\":[{\"code\":\"invalid_access_token\",\"description\":\"Chave inválida.\"}]}");
                return;
            }
            String[] partes = caminho.substring(1).split("/");
            Map<String, String> query = query(troca.getRequestURI().getRawQuery());
            String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            switch (metodo + " " + partes[0] + (partes.length > 2 ? "/*/" + partes[2] : partes.length > 1 ? "/*" : "")) {
                case "GET customers" -> responder(troca, 200, lista(clientes, query.get("externalReference")));
                case "POST customers" -> responder(troca, 200, criar(clientes, "cus_", corpo, false));
                case "GET payments" -> responder(troca, 200, lista(pagamentos, query.get("externalReference")));
                case "POST payments" -> responder(troca, 200, criar(pagamentos, "pay_", corpo, true));
                case "GET payments/*" -> existente(troca, partes[1], p -> p.toString());
                case "DELETE payments/*" -> existente(troca, partes[1], p -> {
                    p.put("deleted", true);
                    return "{\"deleted\":true,\"id\":\"" + partes[1] + "\"}";
                });
                case "GET payments/*/pixQrCode" -> {
                    if (falhasPix.getAndUpdate(n -> Math.max(n - 1, 0)) > 0) responder(troca, 500, "{}");
                    else existente(troca, partes[1], p -> "{\"encodedImage\":\"iVBOR\",\"payload\":\"00020126ASAAS"
                            + partes[1] + "\"}");
                }
                case "GET payments/*/identificationField" -> existente(troca, partes[1],
                        p -> "{\"identificationField\":\"00190000090281913600966281313172593870000008990\"}");
                case "POST payments/*/refund" -> existente(troca, partes[1], p -> p.put("status", "REFUND_REQUESTED").toString());
                default -> responder(troca, 404, "{}");
            }
        } finally {
            troca.close();
        }
    }

    private String criar(Map<String, ObjectNode> onde, String prefixo, String corpo, boolean pagamento) throws IOException {
        ObjectNode novo = (ObjectNode) json.readTree(corpo);
        String id = prefixo + sequencia.incrementAndGet();
        novo.put("id", id).put("deleted", false);
        if (pagamento) novo.put("status", "PENDING").put("invoiceUrl", "https://sandbox.asaas.com/i/" + id);
        onde.put(id, novo);
        return novo.toString();
    }

    private String lista(Map<String, ObjectNode> onde, String referencia) {
        ArrayNode data = json.createArrayNode();
        onde.values().stream().filter(o -> o.path("externalReference").asText("").equals(referencia)).forEach(data::add);
        ObjectNode lista = json.createObjectNode().put("object", "list").put("hasMore", false)
                .put("totalCount", data.size());
        lista.set("data", data);
        return lista.toString();
    }

    private void existente(HttpExchange troca, String id, Function<ObjectNode, String> resposta)
            throws IOException {
        ObjectNode pagamento = pagamentos.get(id);
        if (pagamento == null) responder(troca, 404, "{}");
        else responder(troca, 200, resposta.apply(pagamento));
    }

    private static void responder(HttpExchange troca, int status, String corpo) throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().set("Content-Type", "application/json");
        troca.sendResponseHeaders(status, bytes.length);
        troca.getResponseBody().write(bytes);
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> valores = new HashMap<>();
        if (raw == null) return valores;
        Arrays.stream(raw.split("&")).map(p -> p.split("=", 2)).forEach(p -> valores.put(
                URLDecoder.decode(p[0], StandardCharsets.UTF_8),
                p.length > 1 ? URLDecoder.decode(p[1], StandardCharsets.UTF_8) : ""));
        return valores;
    }
}
