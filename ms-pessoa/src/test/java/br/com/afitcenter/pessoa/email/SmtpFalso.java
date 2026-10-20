package br.com.afitcenter.pessoa.email;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servidor SMTP mínimo em 127.0.0.1 (sem TLS nem autenticação) que guarda os comandos e as mensagens recebidas.
 * Nenhum teste fala com um servidor de e-mail de verdade.
 */
final class SmtpFalso implements AutoCloseable {

    private final ServerSocket servidor;
    private final List<String> comandos = new CopyOnWriteArrayList<>();
    private final List<String> mensagens = new CopyOnWriteArrayList<>();

    SmtpFalso() throws IOException {
        servidor = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        Thread.ofVirtual().start(this::atender);
    }

    int porta() {
        return servidor.getLocalPort();
    }

    List<String> comandos() {
        return List.copyOf(comandos);
    }

    /** A única mensagem recebida, já interpretada (cabeçalhos decodificados, corpo no charset dele). */
    MimeMessage unica() throws Exception {
        if (mensagens.size() != 1)
            throw new AssertionError("Esperava 1 mensagem; chegaram " + mensagens.size() + ".");
        return new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(mensagens.getFirst().getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Override
    public void close() throws IOException {
        servidor.close();
    }

    private void atender() {
        while (!servidor.isClosed()) {
            try (Socket conexao = servidor.accept()) {
                conversar(conexao);
            } catch (IOException e) {
                // servidor fechado ou cliente desconectado
            }
        }
    }

    private void conversar(Socket conexao) throws IOException {
        var entrada = new BufferedReader(new InputStreamReader(conexao.getInputStream(), StandardCharsets.ISO_8859_1));
        OutputStream saida = conexao.getOutputStream();
        responder(saida, "220 smtp-falso");
        String linha;
        while ((linha = entrada.readLine()) != null) {
            comandos.add(linha);
            String comando = linha.toUpperCase(Locale.ROOT);
            if (comando.startsWith("DATA")) {
                responder(saida, "354 termine com uma linha com um ponto");
                StringBuilder dados = new StringBuilder();
                while ((linha = entrada.readLine()) != null && !linha.equals("."))
                    dados.append(linha.startsWith("..") ? linha.substring(1) : linha).append("\r\n");
                mensagens.add(dados.toString());
                responder(saida, "250 recebida");
            } else if (comando.startsWith("QUIT")) {
                responder(saida, "221 tchau");
                return;
            } else {
                responder(saida, "250 ok"); // EHLO (sem extensões), MAIL FROM, RCPT TO, RSET, NOOP
            }
        }
    }

    private static void responder(OutputStream saida, String resposta) throws IOException {
        saida.write((resposta + "\r\n").getBytes(StandardCharsets.US_ASCII));
        saida.flush();
    }
}
