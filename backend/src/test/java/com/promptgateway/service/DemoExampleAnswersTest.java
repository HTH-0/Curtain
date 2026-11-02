package com.promptgateway.service;

import com.jayway.jsonpath.JsonPath;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 시연 예시의 모범 답(AC-12.8). 실제 설정(회사 사전 포함)과 시연 모드로 포털에 예시 문장을 보내, 탐지와 가림은 실제로 하고
 * 답은 미리 써 둔 모범 답이 오며 그 안의 가명이 원래 값으로 복원되는지 본다. 예시 문장은 화면(index.html)과 같아야 한다.
 */
@SpringBootTest(properties = "gateway.demo-answer=true")
class DemoExampleAnswersTest {

    static final String CONTACTS = "아래 고객 연락처를 표로 정리해 줘\n김민수 010-2345-6789 minsu.k@mail.example\n박서연 010-9876-5432 seoyeon.p@mail.example";
    static final String CLIENT_MAIL = "한결물산 미팅에서 블루웨일 출시 일정 공유한 내용으로 감사 메일 초안 써 줘";
    static final String MINUTES = "주간 회의록을 세 줄로 요약하는 방법 알려 줘";
    static final String RRN_QUESTION = "주민등록번호는 몇 자리야?";
    /** 화면의 "경고: 대량 명단" 예시와 같은 방식으로 만든다(직원1~22와 번호). */
    static final String BULK = IntStream.range(0, 22).mapToObj(i -> "직원" + (i + 1) + " 010-" + (2100 + i) + "-" + (3400 + i))
            .collect(Collectors.joining("\n")) + "\n위 명단 부서별로 정리해 줘";

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private String answer(String prompt, boolean confirmed) throws Exception {
        String body = "{\"prompt\":" + json(prompt) + ",\"confirmed\":" + confirmed + "}";
        MockHttpServletResponse r = mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(body))
                .andReturn().getResponse();
        assertThat(r.getStatus()).isEqualTo(200);
        return JsonPath.read(r.getContentAsString(StandardCharsets.UTF_8), "$.answer");
    }

    private static String json(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    @Test
    @DisplayName("AC-12.8 가림 예시(고객 연락처): 모범 답의 가명이 원래 번호와 이메일로 복원되고, 미리 쓴 답임을 밝힌다")
    void contactsExample() throws Exception {
        String a = answer(CONTACTS, false);

        assertThat(a).startsWith("(미리 써 둔 시연용 예시 답변").contains("| 김민수 | 010-2345-6789 | minsu.k@mail.example |",
                "| 박서연 | 010-9876-5432 | seoyeon.p@mail.example |");
        assertThat(a).doesNotContain("[전화", "[이메일");
    }

    @Test
    @DisplayName("AC-12.8 가림 예시(고객사와 코드명): 모범 메일 초안의 가명이 회사 이름과 코드명으로 복원된다")
    void clientMailExample() throws Exception {
        String a = answer(CLIENT_MAIL, false);

        assertThat(a).startsWith("(미리 써 둔 시연용 예시 답변").contains("한결물산", "블루웨일").doesNotContain("[고객사", "[코드명");
    }

    @Test
    @DisplayName("AC-12.8 경고 예시(대량 명단)를 확인하고 보내면 모범 답이 오고 가명이 복원된다")
    void bulkExample() throws Exception {
        String a = answer(BULK, true);

        assertThat(a).startsWith("(미리 써 둔 시연용 예시 답변").contains("010-2100-3400", "010-2121-3421").doesNotContain("[전화");
    }

    @Test
    @DisplayName("AC-12.8 허용 예시 두 개는 모범 답이 온다")
    void allowExamples() throws Exception {
        assertThat(answer(MINUTES, false)).startsWith("(미리 써 둔 시연용 예시 답변").contains("세 줄");
        assertThat(answer(RRN_QUESTION, false)).startsWith("(미리 써 둔 시연용 예시 답변").contains("13자리");
    }

    @Test
    @DisplayName("AC-12.8 예시가 아닌 문장은 외부 AI가 받았을 문장을 돌려주고 실제 답은 내 키로 받으라고 안내한다")
    void otherPromptsEcho() throws Exception {
        String a = answer("박서연 010-9876-5432 연락처를 메모해 줘", false);

        assertThat(a).contains("시연용 답변", "박서연 010-9876-5432 연락처를 메모해 줘", "내 키로 써 보기").doesNotContain("미리 써 둔");
    }

    @Test
    @DisplayName("AC-12.8 화면(index.html)의 예시 문장이 이 시험의 예시와 같다(한쪽만 바뀌면 모범 답이 나오지 않는다)")
    void examplesMatchPage() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/static/index.html"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\\[\"[^\"]+\", \"((?:[^\"\\\\]|\\\\.)*)\"]").matcher(html);
        List<String> literal = new ArrayList<>();
        while (m.find()) {
            literal.add(m.group(1).replace("\\n", "\n"));
        }
        assertThat(literal).contains(CONTACTS, CLIENT_MAIL, MINUTES, RRN_QUESTION);
        assertThat(html).contains("Array.from({length: 22}, (_, i) => `직원${i + 1} 010-${2100 + i}-${3400 + i}`).join(\"\\n\") + \"\\n위 명단 부서별로 정리해 줘\"");
    }
}
