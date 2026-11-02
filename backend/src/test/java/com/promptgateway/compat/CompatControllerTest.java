package com.promptgateway.compat;

import com.jayway.jsonpath.JsonPath;
import com.promptgateway.log.GatewayLogger;
import com.promptgateway.service.ChatTurn;
import com.promptgateway.support.RecordingAnswerer;
import com.promptgateway.support.TestConversations;
import com.promptgateway.support.TestGateways;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * OpenAI 호환 입구의 HTTP 형식(FR-11, design.md 9.4). 외부 AI는 받은 대화를 기록하는 가짜이다. 값은 모두 가짜이다.
 */
class CompatControllerTest {

    static final String PATH = "/v1/chat/completions";
    static final String PHONE = "010-2345-6789";

    RecordingAnswerer answerer;
    List<String> logLines;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        answerer = RecordingAnswerer.echoLast();
        mvc = mvcFor(answerer, 20000);
    }

    private MockMvc mvcFor(RecordingAnswerer a, int maxConversationLength) {
        logLines = new ArrayList<>();
        CompatController controller = new CompatController(
                TestConversations.gateway(a, new GatewayLogger(logLines::add), maxConversationLength));
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new CompatExceptionHandler()).build();
    }

    private static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static String message(String role, String content) {
        return "{\"role\":" + q(role) + ",\"content\":" + q(content) + "}";
    }

    private static String request(boolean stream, String... messages) {
        return "{\"model\":\"gpt-4o\",\"stream\":" + stream + ",\"messages\":[" + String.join(",", messages) + "]}";
    }

    private MockHttpServletResponse send(String body) throws Exception {
        return mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(body))
                .andReturn().getResponse();
    }

    private static String text(MockHttpServletResponse r) throws Exception {
        return r.getContentAsString(StandardCharsets.UTF_8);
    }

    private static <T> T json(String body, String path) {
        return JsonPath.read(body, path);
    }

    @Test
    @DisplayName("AC-11.1 GET /v1/models는 OpenAI 목록 형식으로 모델 하나(deskguard)를 돌려준다")
    void models() throws Exception {
        MockHttpServletResponse r = mvc.perform(get("/v1/models")).andReturn().getResponse();
        String body = text(r);

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(json(body, "$.object").toString()).isEqualTo("list");
        assertThat(json(body, "$.data[0].id").toString()).isEqualTo("deskguard");
        assertThat(json(body, "$.data[0].object").toString()).isEqualTo("model");
        assertThat((List<?>) json(body, "$.data")).hasSize(1);
    }

    @Test
    @DisplayName("AC-11.2 허용: OpenAI 응답 형식으로 답을 돌려주고, 요청의 model 값은 무시한다")
    void allow() throws Exception {
        MockHttpServletResponse r = send(request(false, message("system", "한국어로 답해"), message("user", "회의록 요약해 줘")));
        String body = text(r);

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(json(body, "$.object").toString()).isEqualTo("chat.completion");
        assertThat(json(body, "$.model").toString()).isEqualTo("deskguard");
        assertThat(json(body, "$.choices[0].index").toString()).isEqualTo("0");
        assertThat(json(body, "$.choices[0].message.role").toString()).isEqualTo("assistant");
        assertThat(json(body, "$.choices[0].message.content").toString()).isEqualTo("답: 회의록 요약해 줘");
        assertThat(json(body, "$.choices[0].finish_reason").toString()).isEqualTo("stop");
        assertThat(answerer.calls().getFirst()).containsExactly(new ChatTurn("system", "한국어로 답해"), new ChatTurn("user", "회의록 요약해 줘"));
    }

    @Test
    @DisplayName("AC-11.4, 11.6 가림: 외부 AI에는 가명이 가고, 응답은 원래 값으로 되돌리고 안내 한 줄이 붙는다")
    void mask() throws Exception {
        MockHttpServletResponse r = send(request(false, message("user", "김민수 " + PHONE + " 에게 안내 메일 써 줘")));
        String body = text(r);

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(json(body, "$.choices[0].message.content").toString())
                .isEqualTo("[Deskguard] 휴대전화 1건을 가려서 보냈습니다.\n\n답: 김민수 " + PHONE + " 에게 안내 메일 써 줘");
        assertThat(answerer.allSentText()).contains("[전화1]").doesNotContain(PHONE);
    }

    @Test
    @DisplayName("AC-11.7 차단: 403과 OpenAI 오류 형식, 범주별 개수와 안내만 있고 값은 없다. 외부 AI를 부르지 않는다")
    void block() throws Exception {
        MockHttpServletResponse r = send(request(false, message("user", "이 설정 왜 안 돼? DB_PASSWORD=Qw7!zLp92x")));
        String body = text(r);

        assertThat(r.getStatus()).isEqualTo(403);
        assertThat(json(body, "$.error.type").toString()).isEqualTo("deskguard_blocked");
        assertThat(json(body, "$.error.code").toString()).isEqualTo("blocked");
        assertThat(json(body, "$.error.message").toString()).contains("비밀번호 1건", "다시 보내세요");
        assertThat(body).doesNotContain("Qw7!zLp92x", "DB_PASSWORD");
        assertThat(answerer.calls()).isEmpty();
    }

    @ParameterizedTest(name = "AC-11.8 400: {0}")
    @ValueSource(strings = {
            "{\"model\":\"x\"}",
            "{\"messages\":[]}",
            "{\"messages\":[{\"role\":\"tool\",\"content\":\"a\"}]}",
            "{\"messages\":[{\"role\":\"user\",\"content\":123}]}",
            "{\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"a\"}]}]}",
            "{\"messages\":[{\"role\":\"user\",\"content\":\"   \"}]}",
            "{\"messages\":[{\"role\":\"user\"}]}",
            "{{{ 깨진 JSON"})
    void invalidRequests(String body) throws Exception {
        MockHttpServletResponse r = send(body);
        String text = text(r);

        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(json(text, "$.error.type").toString()).isEqualTo("invalid_request_error");
        assertThat(answerer.calls()).isEmpty();
    }

    @Test
    @DisplayName("AC-11.8 Content-Type이 JSON이 아니면 500이 아니라 415와 OpenAI 오류 형식이다(클라이언트 잘못)")
    void unsupportedContentType() throws Exception {
        MockHttpServletResponse r = mvc.perform(post(PATH).contentType(MediaType.TEXT_PLAIN).content("hello")).andReturn().getResponse();

        assertThat(r.getStatus()).isEqualTo(415);
        assertThat(json(text(r), "$.error.type").toString()).isEqualTo("invalid_request_error");
        assertThat(answerer.calls()).isEmpty();
    }

    @Test
    @DisplayName("AC-11.8 대화 전체 길이가 기준을 넘으면 400이다")
    void tooLong() throws Exception {
        mvc = mvcFor(answerer, 50);

        MockHttpServletResponse r = send(request(false, message("user", "가".repeat(30)), message("assistant", "나".repeat(30))));

        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(json(text(r), "$.error.type").toString()).isEqualTo("invalid_request_error");
        assertThat(answerer.calls()).isEmpty();
    }

    @Test
    @DisplayName("AC-11.9 stream=true: 완성된 복원 답을 SSE chunk 한 건과 [DONE]으로 보낸다")
    void stream() throws Exception {
        MockHttpServletResponse r = send(request(true, message("user", "김민수 " + PHONE + " 정리해 줘")));
        String body = text(r);

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getContentType()).startsWith("text/event-stream");
        assertThat(body).endsWith("data: [DONE]\n\n");
        List<String> events = List.of(body.split("\n\n"));
        assertThat(events).hasSize(2);
        String chunk = events.get(0).substring("data: ".length());
        assertThat(json(chunk, "$.object").toString()).isEqualTo("chat.completion.chunk");
        assertThat(json(chunk, "$.model").toString()).isEqualTo("deskguard");
        assertThat(json(chunk, "$.choices[0].delta.role").toString()).isEqualTo("assistant");
        assertThat(json(chunk, "$.choices[0].delta.content").toString())
                .isEqualTo("[Deskguard] 휴대전화 1건을 가려서 보냈습니다.\n\n답: 김민수 " + PHONE + " 정리해 줘");
        assertThat(json(chunk, "$.choices[0].finish_reason").toString()).isEqualTo("stop");
    }

    @Test
    @DisplayName("AC-11.9 stream=true라도 차단은 스트림이 아니라 일반 JSON 오류(403)이다")
    void blockedStreamIsPlainError() throws Exception {
        MockHttpServletResponse r = send(request(true, message("user", "DB_PASSWORD=Qw7!zLp92x")));

        assertThat(r.getStatus()).isEqualTo(403);
        assertThat(r.getContentType()).startsWith("application/json");
        assertThat(json(text(r), "$.error.type").toString()).isEqualTo("deskguard_blocked");
    }

    @Test
    @DisplayName("AC-11.10 외부 AI 오류는 OpenAI 오류 형식으로 알리고(401, 429, 504, 500), 내부 정보는 싣지 않는다")
    void upstreamErrors() throws Exception {
        record Case(RuntimeException error, int status, String code) {
        }
        List<Case> cases = List.of(
                new Case(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "x", HttpHeaders.EMPTY, new byte[0], null),
                        401, "upstream_unauthorized"),
                new Case(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "x", HttpHeaders.EMPTY, new byte[0], null),
                        429, "upstream_rate_limited"),
                new Case(new ResourceAccessException("I/O error on POST https://g.example/v1?key=SECRET-KEY"), 504, "upstream_timeout"),
                new Case(new IllegalStateException("boom https://g.example/v1?key=SECRET-KEY"), 500, "internal_error"));

        for (Case c : cases) {
            mvc = mvcFor(new RecordingAnswerer(conv -> {
                throw c.error();
            }), 20000);

            MockHttpServletResponse r = send(request(false, message("user", "회의록 요약해 줘")));
            String body = text(r);

            assertThat(r.getStatus()).as(c.code()).isEqualTo(c.status());
            assertThat(json(body, "$.error.type").toString()).as(c.code()).isEqualTo("upstream_error");
            assertThat(json(body, "$.error.code").toString()).as(c.code()).isEqualTo(c.code());
            assertThat(body).as(c.code()).doesNotContain("SECRET-KEY", "g.example");
        }
    }

    @Test
    @DisplayName("AC-11.12 Authorization 헤더 값은 검사하지 않고 응답과 로그에 남기지 않는다")
    void authorizationHeaderIsIgnored() throws Exception {
        MockHttpServletResponse r = mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8")
                        .header("Authorization", "Bearer FAKE-CLIENT-KEY-12345")
                        .content(request(false, message("user", "회의록 요약해 줘"))))
                .andReturn().getResponse();

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(text(r)).doesNotContain("FAKE-CLIENT-KEY-12345");
        assertThat(String.join("\n", logLines)).doesNotContain("FAKE-CLIENT-KEY-12345");
    }

    @Test
    @DisplayName("AC-11.3 /api/chat과 같은 탐지: 한 문장의 가림 결과가 같다")
    void sameMaskingAsChatEndpoint() throws Exception {
        String sentence = "김민수 " + PHONE + ", 한결물산 담당자 표로 정리";
        send(request(false, message("user", sentence)));

        List<String> viaChat = new ArrayList<>();
        TestGateways.gateway(p -> {
            viaChat.add(p);
            return "x";
        }, new GatewayLogger(l -> { }), false).handle(sentence, false, false);

        assertThat(answerer.calls().getFirst()).extracting(ChatTurn::text).containsExactly(viaChat.getFirst());
    }
}
