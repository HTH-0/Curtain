package com.promptgateway.controller;

import ch.qos.logback.classic.Level;
import com.jayway.jsonpath.JsonPath;
import com.promptgateway.compat.CompatController;
import com.promptgateway.compat.CompatExceptionHandler;
import com.promptgateway.exception.GlobalExceptionHandler;
import com.promptgateway.log.GatewayLogger;
import com.promptgateway.service.AnswerGenerator;
import com.promptgateway.service.UserKeyAnswerers;
import com.promptgateway.support.LogCapture;
import com.promptgateway.support.TestConversations;
import com.promptgateway.support.TestGateways;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 방문자가 넣은 Gemini 키로 쓰기(FR-12). 포털(/api/chat)은 X-Gemini-Key 헤더, OpenAI 호환 입구(/v1)는 Authorization: Bearer로 받는다.
 * 키가 없거나 형식이 아니면 서버 기본 응답(여기서는 기록하는 가짜)을 쓴다. Gemini는 MockRestServiceServer로 흉내 내고 키는 가짜이다.
 */
class UserKeyTest {

    /** Gemini 키 형식(AIza로 시작하는 39자)의 가짜 키. 비밀 값 찾기에 걸리지 않게 코드로 만든다. */
    static final String USER_KEY = "AIza" + "Fake".repeat(8) + "Key";
    /** AQ.로 시작하는 새 형식의 가짜 키. */
    static final String NEW_KEY = "AQ." + "Fake_Key-".repeat(5) + "x.y";
    static final String GEMINI_URL = "https://gemini.test/v1beta/models/test-model:generateContent";
    static final String OK_BODY = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"안녕하세요\"}]}}]}";

    MockRestServiceServer server;
    MockMvc mockMvc;
    final List<String> defaultCalls = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        UserKeyAnswerers userKeys = new UserKeyAnswerers(restTemplate, "test-model", "https://gemini.test/v1beta/models");
        AnswerGenerator byDefault = prompt -> {
            defaultCalls.add(prompt);
            return "기본 답";
        };
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ChatController(TestGateways.gateway(byDefault, new GatewayLogger(), false), userKeys),
                        new CompatController(TestConversations.gateway(byDefault, new GatewayLogger()), userKeys))
                .setControllerAdvice(new CompatExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private static MockHttpServletRequestBuilder portal(String prompt) {
        return post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"" + prompt + "\"}");
    }

    private static MockHttpServletRequestBuilder v1(String prompt) {
        return post("/v1/chat/completions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"model\":\"curtain\",\"messages\":[{\"role\":\"user\",\"content\":\"" + prompt + "\"}]}");
    }

    private static String body(MockHttpServletResponse r) throws Exception {
        return r.getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("AC-12.1 키 헤더가 없거나 Gemini 키 형식이 아니면 서버 기본 응답을 쓰고 Gemini를 부르지 않는다")
    void noKeyOrWrongShapeUsesDefault() throws Exception {
        assertThat(body(send(portal("안녕")))).contains("기본 답");
        assertThat(body(send(portal("안녕").header("X-Gemini-Key", "x")))).contains("기본 답");
        assertThat(body(send(v1("안녕").header("Authorization", "Bearer x")))).contains("기본 답");
        assertThat(body(send(v1("안녕")))).contains("기본 답");

        assertThat(defaultCalls).hasSize(4);
        server.verify();
    }

    @Test
    @DisplayName("AC-12.2, 12.3 포털: 키 형식이면 그 키를 주소가 아니라 x-goog-api-key 헤더로 Gemini에 보내고, 가린 문장만 나간다")
    void portalUsesVisitorKeyInHeader() throws Exception {
        server.expect(requestTo(GEMINI_URL))
                .andExpect(header("x-goog-api-key", USER_KEY))
                .andExpect(content().string(not(containsString("010-2345-6789"))))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        MockHttpServletResponse r = send(portal("김민수 010-2345-6789 에게 안내 메일 써 줘").header("X-Gemini-Key", USER_KEY));

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(body(r)).contains("안녕하세요");
        assertThat(defaultCalls).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("AC-12.2 /v1: Authorization: Bearer 값이 키 형식이면 그 키로 Gemini를 부른다")
    void v1UsesBearerKey() throws Exception {
        server.expect(requestTo(GEMINI_URL)).andExpect(header("x-goog-api-key", USER_KEY))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        MockHttpServletResponse r = send(v1("안녕").header("Authorization", "Bearer " + USER_KEY));

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(body(r), "$.choices[0].message.content")).isEqualTo("안녕하세요");
        assertThat(defaultCalls).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("AC-12.2 차단이면 키가 있어도 Gemini를 부르지 않는다")
    void blockedNeverCallsGemini() throws Exception {
        assertThat(send(portal("DB_PASSWORD=Qw7!zLp92x 이 설정 봐 줘").header("X-Gemini-Key", USER_KEY)).getStatus()).isEqualTo(403);
        assertThat(send(v1("DB_PASSWORD=Qw7!zLp92x 이 설정 봐 줘").header("Authorization", "Bearer " + USER_KEY)).getStatus()).isEqualTo(403);
        server.verify();
    }

    @Test
    @DisplayName("AC-12.4 Gemini가 방문자 키를 거부하면(400, 401, 403) 401로 키를 확인하라고 알리고, 키와 외부 응답 본문은 싣지 않는다")
    void rejectedKeyIsReported() throws Exception {
        for (HttpStatus status : List.of(HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)) {
            server.expect(requestTo(GEMINI_URL)).andRespond(withStatus(status)
                    .body("{\"error\":{\"message\":\"API key not valid " + USER_KEY + "\"}}").contentType(MediaType.APPLICATION_JSON));
            server.expect(requestTo(GEMINI_URL)).andRespond(withStatus(status)
                    .body("{\"error\":{\"message\":\"API key not valid " + USER_KEY + "\"}}").contentType(MediaType.APPLICATION_JSON));

            MockHttpServletResponse p = send(portal("안녕").header("X-Gemini-Key", USER_KEY));
            assertThat(p.getStatus()).isEqualTo(401);
            assertThat(body(p)).contains("USER_KEY_REJECTED", "Gemini 키를 확인하세요").doesNotContain(USER_KEY, "API key not valid");

            MockHttpServletResponse v = send(v1("안녕").header("Authorization", "Bearer " + USER_KEY));
            assertThat(v.getStatus()).isEqualTo(401);
            assertThat(JsonPath.<String>read(body(v), "$.error.code")).isEqualTo("invalid_api_key");
            assertThat(body(v)).doesNotContain(USER_KEY, "API key not valid");
            server.reset();
        }
    }

    @Test
    @DisplayName("AC-12.3 정상, 거부, 외부 오류, 연결 오류, 깨진 응답의 어느 경로에서도 로그에 방문자 키가 없다")
    void visitorKeyNeverInLogs() throws Exception {
        server.expect(requestTo(GEMINI_URL)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));
        server.expect(requestTo(GEMINI_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST).body("bad " + USER_KEY));
        server.expect(requestTo(GEMINI_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("boom " + USER_KEY));
        server.expect(requestTo(GEMINI_URL)).andRespond(withException(new IOException("connect refused " + USER_KEY)));
        server.expect(requestTo(GEMINI_URL)).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
        server.expect(requestTo(GEMINI_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("boom " + USER_KEY));

        try (LogCapture logs = LogCapture.root()) {
            for (int i = 0; i < 5; i++) {
                send(portal("안녕").header("X-Gemini-Key", USER_KEY));
            }
            send(v1("안녕").header("Authorization", "Bearer " + USER_KEY));

            assertThat(logs.messages()).isNotEmpty();
            assertThat(logs.fullTextAtLeast(Level.INFO)).doesNotContain(USER_KEY);
        }
        server.verify();
    }

    @Test
    @DisplayName("AC-12.5 /api/info는 서버 기본 응답이 시연인지 서버 키인지 알린다")
    void infoTellsDefaultMode() throws Exception {
        MockMvc demo = MockMvcBuilders.standaloneSetup(new InfoController(true)).build();
        MockMvc serverKey = MockMvcBuilders.standaloneSetup(new InfoController(false)).build();

        assertThat(JsonPath.<String>read(demo.perform(get("/api/info")).andReturn().getResponse().getContentAsString(), "$.defaultMode"))
                .isEqualTo("demo");
        assertThat(JsonPath.<String>read(serverKey.perform(get("/api/info")).andReturn().getResponse().getContentAsString(), "$.defaultMode"))
                .isEqualTo("server-key");
    }

    @Test
    @DisplayName("AC-12.1 키 형식 판단: Google 키(AIza 39자, AQ. 형식)만 키로 보고, 다른 서비스의 키나 짧은 값은 키로 보지 않는다")
    void keyShape() {
        assertThat(UserKeyAnswerers.isGeminiKey(USER_KEY)).isTrue();
        assertThat(UserKeyAnswerers.isGeminiKey(" " + USER_KEY + " ")).isTrue();
        assertThat(UserKeyAnswerers.isGeminiKey(NEW_KEY)).isTrue();
        assertThat(UserKeyAnswerers.isGeminiKey(null)).isFalse();
        assertThat(UserKeyAnswerers.isGeminiKey("x")).isFalse();
        assertThat(UserKeyAnswerers.isGeminiKey(USER_KEY + "A")).isFalse();
        assertThat(UserKeyAnswerers.isGeminiKey("AQ.short")).isFalse();
        assertThat(UserKeyAnswerers.isGeminiKey("AQ." + "a".repeat(30) + " b")).isFalse();
        assertThat(UserKeyAnswerers.isGeminiKey("sk-" + "a".repeat(40))).isFalse();
        assertThat(UserKeyAnswerers.isGeminiKey("sk-proj-" + "a".repeat(40))).isFalse();
    }

    @Test
    @DisplayName("AC-12.1, 12.2 AQ.로 시작하는 새 형식의 키도 그 요청에만 x-goog-api-key 헤더로 Gemini에 보낸다")
    void newKeyFormatIsUsed() throws Exception {
        server.expect(requestTo(GEMINI_URL)).andExpect(header("x-goog-api-key", NEW_KEY))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));
        server.expect(requestTo(GEMINI_URL)).andExpect(header("x-goog-api-key", NEW_KEY))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        assertThat(body(send(portal("안녕").header("X-Gemini-Key", NEW_KEY)))).contains("안녕하세요");
        assertThat(body(send(v1("안녕").header("Authorization", "Bearer " + NEW_KEY)))).contains("안녕하세요");
        assertThat(defaultCalls).isEmpty();
        server.verify();
    }
}
