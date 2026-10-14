package com.example.server.utils;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderSeparationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ProviderUtilitiesConfiguration.class);

    @Test
    void bindsIndependentProviderCredentialsAndEndpoints() {
        contextRunner
                .withPropertyValues(
                        "LLM_API_KEY=llm-key",
                        "LLM_BASE_URL=https://api.deepseek.com",
                        "LLM_MODEL=deepseek-flash",
                        "ASR_API_KEY=asr-key",
                        "EMBEDDING_API_KEY=embedding-key",
                        "EMBEDDING_BASE_URL=https://embedding.example/v1"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getEnvironment().getProperty("ai.deepseek.api-key")).isEqualTo("llm-key");
                    assertThat(context.getEnvironment().getProperty("ai.deepseek.base-url"))
                            .isEqualTo("https://api.deepseek.com");
                    assertThat(context.getEnvironment().getProperty("ai.deepseek.model")).isEqualTo("deepseek-flash");

                    AliyunAsrUtils asr = context.getBean(AliyunAsrUtils.class);
                    assertThat(ReflectionTestUtils.getField(asr, "apiKey")).isEqualTo("asr-key");

                    EmbeddingUtils embedding = context.getBean(EmbeddingUtils.class);
                    assertThat(ReflectionTestUtils.getField(embedding, "apiKey")).isEqualTo("embedding-key");
                    assertThat(ReflectionTestUtils.getField(embedding, "baseUrl"))
                            .isEqualTo("https://embedding.example/v1");
                });
    }

    @Test
    void retainsSiliconFlowFallbacksForAsrAndEmbedding() {
        contextRunner
                .withPropertyValues(
                        "SILICONFLOW_API_KEY=fallback-key",
                        "SILICONFLOW_BASE_URL=https://fallback.example/v1"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(ReflectionTestUtils.getField(context.getBean(AliyunAsrUtils.class), "apiKey"))
                            .isEqualTo("fallback-key");
                    EmbeddingUtils embedding = context.getBean(EmbeddingUtils.class);
                    assertThat(ReflectionTestUtils.getField(embedding, "apiKey")).isEqualTo("fallback-key");
                    assertThat(ReflectionTestUtils.getField(embedding, "baseUrl"))
                            .isEqualTo("https://fallback.example/v1");
                });
    }

    @Test
    void disabledEmbeddingReturnsEmptyWithoutHttpRequest() throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestCount.incrementAndGet();
            byte[] response = "{\"data\":[{\"embedding\":[1.0]}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            contextRunner
                    .withPropertyValues(
                            "SILICONFLOW_API_KEY=fallback-key",
                            "EMBEDDING_BASE_URL=http://127.0.0.1:" + server.getAddress().getPort(),
                            "EMBEDDING_ENABLED=false"
                    )
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean(EmbeddingUtils.class).embed("must not be sent")).isEmpty();
                        assertThat(requestCount).hasValue(0);
                    });
        } finally {
            server.stop(0);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import({AliyunAsrUtils.class, EmbeddingUtils.class})
    static class ProviderUtilitiesConfiguration {
    }
}
