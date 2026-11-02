package com.promptgateway.service;

import com.promptgateway.dto.gemini.GeminiRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 대화를 응답 모델에 보내는 방식(FR-11, design.md 9.1). 외부 호출은 없다. */
class ConversationAnswerTest {

    @Test
    @DisplayName("Gemini 요청: user와 model 순서의 contents로 보내고, system 메시지는 systemInstruction으로 모은다")
    void geminiRequestFromConversation() {
        GeminiRequest r = GeminiRequest.of(List.of(
                new ChatTurn("system", "규칙 하나"), new ChatTurn("system", "규칙 둘"),
                new ChatTurn("user", "안녕"), new ChatTurn("assistant", "네"), new ChatTurn("user", "질문")));

        assertThat(r.contents()).extracting(c -> c.role()).containsExactly("user", "model", "user");
        assertThat(r.contents()).extracting(c -> c.parts().getFirst().text()).containsExactly("안녕", "네", "질문");
        assertThat(r.systemInstruction()).isNotNull();
        assertThat(r.systemInstruction().parts().getFirst().text()).isEqualTo("규칙 하나\n규칙 둘");
    }

    @Test
    @DisplayName("Gemini 요청: system 메시지가 없으면 systemInstruction은 없다")
    void geminiRequestWithoutSystem() {
        GeminiRequest r = GeminiRequest.of(List.of(new ChatTurn("user", "안녕")));

        assertThat(r.systemInstruction()).isNull();
        assertThat(r.contents()).hasSize(1);
        assertThat(r.contents().getFirst().role()).isEqualTo("user");
        assertThat(r.contents().getFirst().parts().getFirst().text()).isEqualTo("안녕");
    }

    @Test
    @DisplayName("Gemini 요청: 내용이 공백인 메시지는 거부당하므로 contents에서 뺀다")
    void geminiRequestSkipsBlankTurns() {
        GeminiRequest r = GeminiRequest.of(List.of(new ChatTurn("user", "안녕"), new ChatTurn("assistant", "  "), new ChatTurn("user", "질문")));

        assertThat(r.contents()).extracting(c -> c.parts().getFirst().text()).containsExactly("안녕", "질문");
    }

    @Test
    @DisplayName("기존 문자열 요청 만들기는 그대로이다(contents 하나, systemInstruction 없음)")
    void singlePromptRequestUnchanged() {
        GeminiRequest r = GeminiRequest.of("안녕");

        assertThat(r.contents()).hasSize(1);
        assertThat(r.contents().getFirst().role()).isNull();
        assertThat(r.systemInstruction()).isNull();
    }

    @Test
    @DisplayName("시연용 응답 모델은 대화에서 마지막 사용자 메시지에 기존과 같은 틀로 답한다")
    void demoAnswersLastUserMessage() {
        DemoAnswerGenerator demo = new DemoAnswerGenerator();

        String answer = demo.generateAnswer(List.of(new ChatTurn("user", "a"), new ChatTurn("assistant", "b"), new ChatTurn("user", "c")));

        assertThat(answer).isEqualTo(demo.generateAnswer("c"));
    }

    @Test
    @DisplayName("대화 메서드의 기본 구현(가짜 응답 모델용)은 대화를 역할과 함께 하나의 문자열로 이어 붙여 기존 메서드를 부른다")
    void defaultConversationMethodJoinsTurns() {
        AnswerGenerator echo = prompt -> "받음: " + prompt;

        String answer = echo.generateAnswer(List.of(new ChatTurn("user", "안녕"), new ChatTurn("assistant", "네")));

        assertThat(answer).isEqualTo("받음: user: 안녕\nassistant: 네");
    }
}
