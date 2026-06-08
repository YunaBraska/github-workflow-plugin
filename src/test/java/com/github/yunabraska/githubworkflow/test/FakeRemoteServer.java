package com.github.yunabraska.githubworkflow.test;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FakeRemoteServer implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, String> contents = new HashMap<>();
    private final Map<String, List<String>> branches = new HashMap<>();
    private final Map<String, List<String>> tags = new HashMap<>();
    private final Map<String, Map<String, String>> repositories = new HashMap<>();
    private final List<String> requests = new ArrayList<>();

    public FakeRemoteServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            final URI uri = exchange.getRequestURI();
            final String request = uri.getPath() + (uri.getQuery() == null ? "" : "?" + uri.getQuery());
            requests.add(request);
            final Response response = responseFor(uri);
            final byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    public String webUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    public String apiUrl(final String prefix) {
        return webUrl() + prefix;
    }

    public List<String> requests() {
        return List.copyOf(requests);
    }

    public void addContent(final String owner, final String repo, final String path, final String ref, final String content) {
        contents.put(key(owner, repo, path, ref), content);
    }

    public void setBranches(final String owner, final String repo, final List<String> values) {
        branches.put(owner + "/" + repo, values);
    }

    public void setTags(final String owner, final String repo, final List<String> values) {
        tags.put(owner + "/" + repo, values);
    }

    public void setRepositories(final String owner, final Map<String, String> values) {
        repositories.put(owner, values);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private Response responseFor(final URI uri) {
        final String[] parts = uri.getPath().split("/");
        final int reposIndex = indexOf(parts, "repos");
        final int usersIndex = indexOf(parts, "users");
        final int orgsIndex = indexOf(parts, "orgs");
        if (usersIndex >= 0 && parts.length > usersIndex + 2 && "repos".equals(parts[usersIndex + 2])) {
            return new Response(200, repositories(parts[usersIndex + 1], repositories.getOrDefault(parts[usersIndex + 1], Map.of())));
        }
        if (orgsIndex >= 0 && parts.length > orgsIndex + 2 && "repos".equals(parts[orgsIndex + 2])) {
            return new Response(200, repositories(parts[orgsIndex + 1], repositories.getOrDefault(parts[orgsIndex + 1], Map.of())));
        }
        if (reposIndex < 0 || parts.length <= reposIndex + 3) {
            return new Response(404, "{}");
        }
        final String owner = parts[reposIndex + 1];
        final String repo = parts[reposIndex + 2];
        final String endpoint = parts[reposIndex + 3];
        if ("contents".equals(endpoint)) {
            final String path = tail(parts, reposIndex + 4);
            final String ref = ref(uri);
            final String content = contents.get(key(owner, repo, path, ref));
            if (content == null) {
                return new Response(404, "{}");
            }
            final String encoded = Base64.getMimeEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
            return new Response(200, "{\"content\":\"" + encoded + "\",\"encoding\":\"base64\",\"download_url\":\"" + webUrl() + "/raw/" + path + "\"}");
        }
        if ("branches".equals(endpoint)) {
            return new Response(200, names(branches.getOrDefault(owner + "/" + repo, List.of())));
        }
        if ("tags".equals(endpoint)) {
            return new Response(200, names(tags.getOrDefault(owner + "/" + repo, List.of())));
        }
        return new Response(404, "{}");
    }

    private static int indexOf(final String[] parts, final String value) {
        for (int index = 0; index < parts.length; index++) {
            if (value.equals(parts[index])) {
                return index;
            }
        }
        return -1;
    }

    private static String tail(final String[] parts, final int start) {
        final List<String> result = new ArrayList<>();
        for (int index = start; index < parts.length; index++) {
            result.add(parts[index]);
        }
        return String.join("/", result);
    }

    private static String ref(final URI uri) {
        final String query = uri.getQuery();
        if (query == null) {
            return "";
        }
        for (final String part : query.split("&")) {
            if (part.startsWith("ref=")) {
                return part.substring("ref=".length());
            }
        }
        return "";
    }

    private static String key(final String owner, final String repo, final String path, final String ref) {
        return owner + "/" + repo + "/" + path + "@" + ref;
    }

    private static String names(final List<String> names) {
        return names.stream()
                .map(name -> "{\"name\":\"" + name + "\"}")
                .reduce((left, right) -> left + "," + right)
                .map(value -> "[" + value + "]")
                .orElse("[]");
    }

    private static String repositories(final String owner, final Map<String, String> repositories) {
        return repositories.entrySet().stream()
                .map(entry -> "{\"name\":\"" + entry.getKey() + "\",\"full_name\":\"" + owner + "/" + entry.getKey() + "\",\"description\":\"" + entry.getValue() + "\"}")
                .reduce((left, right) -> left + "," + right)
                .map(value -> "[" + value + "]")
                .orElse("[]");
    }

    private record Response(int status, String body) {
    }
}
