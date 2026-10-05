package dev.pt.risk;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExplanationServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    @Test void invalidProviderResponseFallsBackWithoutLeakingErrors() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            byte[] body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"not-json\"}]}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close();
        }); server.start();
        try {
            var service = new ExplanationService(new AiSettings("test-key","fake-model","http://127.0.0.1:"+server.getAddress().getPort()),mapper);
            var result = service.explain(new RiskEngine(RiskEngineTest.POLICY).analyze("A",List.of(),RiskEngineTest.AS_OF));
            assertThat(result.source()).isEqualTo("FALLBACK"); assertThat(result.summary()).doesNotContain("test-key","not-json");
        } finally { server.stop(0); }
    }
    @Test void validStructuredProviderResponseIsUsed() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("x-goog-api-key")).isEqualTo("test-key");
            byte[] body = mapper.writeValueAsBytes(java.util.Map.of("candidates",List.of(java.util.Map.of("content",java.util.Map.of("parts",List.of(java.util.Map.of("text",
                    "{\"summary\":\"No configured rules triggered.\",\"nextSteps\":[\"Check history completeness.\"],\"limitation\":\"Synthetic only.\"}")))))));
            exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close();
        }); server.start();
        try {
            var service = new ExplanationService(new AiSettings("test-key","fake-model","http://127.0.0.1:"+server.getAddress().getPort()),mapper);
            assertThat(service.explain(new RiskEngine(RiskEngineTest.POLICY).analyze("A",List.of(),RiskEngineTest.AS_OF)).source()).isEqualTo("GEMINI");
        } finally { server.stop(0); }
    }
}
