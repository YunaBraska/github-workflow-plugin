package com.github.yunabraska.githubworkflow.helper;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class FileDownloaderTest {

    private HttpServer server;
    private String baseUrl;

    @Before
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        baseUrl = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
        server.start();
    }

    @After
    public void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void downloadSyncReadsSuccessfulResponseAndSendsHeaders() {
        final AtomicReference<String> userAgent = new AtomicReference<>();
        final AtomicReference<String> clientName = new AtomicReference<>();
        server.createContext("/ok", exchange -> {
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            clientName.set(exchange.getRequestHeaders().getFirst("Client-Name"));
            final byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        final String result = FileDownloader.downloadSync(baseUrl + "/ok", "JUnitAgent/1");

        assertThat(result).isEqualTo("hello" + System.lineSeparator());
        assertThat(userAgent).hasValue("JUnitAgent/1");
        assertThat(clientName).hasValue("GitHub Workflow Plugin");
    }

    @Test
    public void downloadSyncReturnsEmptyStringForHttpFailures() {
        server.createContext("/missing", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        assertThat(FileDownloader.downloadSync(baseUrl + "/missing", "JUnitAgent/1")).isEmpty();
    }

    @Test
    public void downloadSyncReturnsEmptyStringForInvalidUrls() {
        assertThat(FileDownloader.downloadSync("://not-a-url", "JUnitAgent/1")).isEmpty();
    }
}
