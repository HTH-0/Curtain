package com.promptgateway.compat;

import com.jayway.jsonpath.JsonPath;
import com.promptgateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 실제 설정과 모든 부품을 연결한 상태에서 대화 입구를 확인한다(키 없이, 시연용 응답 모델).
 * 오류 처리 순서(CompatExceptionHandler가 GlobalExceptionHandler보다 먼저 적용되는지)와 설정 기본값도 여기서 확인한다.
 */
@SpringBootTest(properties = {"gateway.demo-answer=true", "gemini.api-key=TEST-GEMINI-KEY"})
class CompatApplicationTest {

    @Autowired WebApplicationContext context;
    @Autowired GatewayProperties gatewayProperties;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private MockHttpServletResponse send(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(body))
                .andReturn().getResponse();
    }

    @Test
    @DisplayName("AC-11.8 설정 기본값: 대화 전체 길이는 20,000자이다(application.yml을 고치지 않고 코드 기본값)")
    void defaultConversationLimit() {
        assertThat(gatewayProperties.maxConversationLength()).isEqualTo(20000);
    }

    @Test
    @DisplayName("AC-11.2, 11.6 실제 연결에서 가림과 복원이 동작한다(시연용 응답 모델)")
    void maskAndRestoreEndToEnd() throws Exception {
        MockHttpServletResponse r = send("/v1/chat/completions",
                "{\"model\":\"curtain\",\"messages\":[{\"role\":\"user\",\"content\":\"김민수 010-2345-6789 에게 메일 써 줘\"}]}");
        String body = r.getContentAsString(StandardCharsets.UTF_8);

        assertThat(r.getStatus()).isEqualTo(200);
        String content = JsonPath.read(body, "$.choices[0].message.content");
        assertThat(content).startsWith("[Curtain] 휴대전화 1건을 가려서 보냈습니다.").contains("시연용 답변", "010-2345-6789");
    }

    @Test
    @DisplayName("AC-11.8 대화 입구의 입력 오류는 OpenAI 오류 형식이고, 기존 /api/chat의 오류 형식은 그대로이다")
    void errorHandlersDoNotMix() throws Exception {
        MockHttpServletResponse compat = send("/v1/chat/completions", "{\"messages\":[]}");
        assertThat(compat.getStatus()).isEqualTo(400);
        assertThat(JsonPath.<String>read(compat.getContentAsString(StandardCharsets.UTF_8), "$.error.type"))
                .isEqualTo("invalid_request_error");

        MockHttpServletResponse chat = send("/api/chat", "{\"prompt\":\"   \"}");
        assertThat(chat.getStatus()).isEqualTo(400);
        // 기존 형식: error가 문자열이다(OpenAI 형식에서는 error가 객체이다)
        assertThat(JsonPath.<String>read(chat.getContentAsString(StandardCharsets.UTF_8), "$.error")).isEqualTo("INVALID_PROMPT");
    }

    @Test
    @DisplayName("AC-11.2 기성 채팅 화면이 보내는 그 밖의 필드(temperature, max_tokens, stream_options, 메시지의 name 등)는 무시하고 처리한다")
    void ignoresUnknownRequestFields() throws Exception {
        MockHttpServletResponse r = send("/v1/chat/completions",
                "{\"model\":\"gpt-4o\",\"temperature\":0.7,\"max_tokens\":256,\"top_p\":1,\"user\":\"u-1\","
                        + "\"stream\":false,\"stream_options\":{\"include_usage\":true},"
                        + "\"messages\":[{\"role\":\"user\",\"name\":\"minsu\",\"content\":\"회의록 요약해 줘\"}]}");

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(r.getContentAsString(StandardCharsets.UTF_8), "$.choices[0].message.content"))
                .contains("회의록 요약해 줘");
    }

    @Test
    @DisplayName("AC-11.1 실제 연결에서 모델 목록이 나온다")
    void models() throws Exception {
        MockHttpServletResponse r = mvc.perform(get("/v1/models")).andReturn().getResponse();

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(r.getContentAsString(StandardCharsets.UTF_8), "$.data[0].id")).isEqualTo("curtain");
    }
}
