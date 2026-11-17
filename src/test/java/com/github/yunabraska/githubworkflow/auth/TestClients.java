package com.github.yunabraska.githubworkflow.auth;

import java.net.URI;

public final class TestClients {
    private TestClients() { }

    public static GitHubDeviceAuth.Client local(final int port) {
        return new GitHubDeviceAuth.Client(URI.create("http://127.0.0.1:" + port), "test-client");
    }
}
