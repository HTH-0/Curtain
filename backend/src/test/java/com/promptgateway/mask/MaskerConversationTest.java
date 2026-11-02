package com.promptgateway.mask;

import com.promptgateway.detect.DetectionEngine;
import com.promptgateway.detect.Finding;
import com.promptgateway.support.TestGateways;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 대화 전체 가림(AC-11.4, 11.5, design.md 9.3). 값은 모두 가짜이다. */
class MaskerConversationTest {

    final DetectionEngine engine = TestGateways.engine();
    final Masker masker = new Masker();

    private ConversationMask maskAll(String... texts) {
        List<List<Finding>> found = Arrays.stream(texts).map(engine::detect).toList();
        return masker.maskAll(List.of(texts), found);
    }

    @Test
    @DisplayName("AC-11.4 메시지가 달라도 같은 값은 같은 가명, 다른 값은 다른 가명이다")
    void sharesPseudonymsAcrossMessages() {
        ConversationMask r = maskAll(
                "김민수 010-2345-6789 정리해 줘",
                "정리: 김민수 010-2345-6789, minsu.k@mail.example",
                "박서연 010-9876-5432 도 추가");

        assertThat(r.maskedTexts()).containsExactly(
                "김민수 [전화1] 정리해 줘",
                "정리: 김민수 [전화1], [이메일1]",
                "박서연 [전화2] 도 추가");
        assertThat(r.pseudonymToValue())
                .containsEntry("[전화1]", "010-2345-6789")
                .containsEntry("[전화2]", "010-9876-5432")
                .containsEntry("[이메일1]", "minsu.k@mail.example");
        assertThat(String.join("\n", r.maskedTexts())).doesNotContain("010-2345-6789", "010-9876-5432", "minsu.k@mail.example");
    }

    @Test
    @DisplayName("AC-11.5 대화가 한 턴씩 늘어도 앞부분 메시지의 가명은 그대로이다")
    void earlierMessagesKeepTheirPseudonyms() {
        String a = "김민수 010-2345-6789 정리해 줘";
        String b = "정리: 김민수 010-2345-6789";
        String c = "박서연 010-9876-5432 도 추가해 줘";
        String d = "추가: 박서연 010-9876-5432";
        String e = "한결물산 담당 minsu.k@mail.example 도 넣어 줘";

        ConversationMask three = maskAll(a, b, c);
        ConversationMask five = maskAll(a, b, c, d, e);

        assertThat(five.maskedTexts().subList(0, 3)).isEqualTo(three.maskedTexts());
        assertThat(five.maskedTexts().get(3)).isEqualTo("추가: 박서연 [전화2]");
        assertThat(five.maskedTexts().get(4)).isEqualTo("[고객사1] 담당 [이메일1] 도 넣어 줘");
    }

    @Test
    @DisplayName("AC-4.5 어느 메시지에든 이미 있는 [전화1] 같은 글자의 번호는 건너뛴다")
    void skipsPseudonymLikeTextInAnyMessage() {
        ConversationMask r = maskAll("형식 예: [전화1] 처럼 쓴다", "010-2345-6789 로 연락");

        assertThat(r.maskedTexts()).containsExactly("형식 예: [전화1] 처럼 쓴다", "[전화2] 로 연락");
        assertThat(r.pseudonymToValue()).containsOnlyKeys("[전화2]");
    }

    @Test
    @DisplayName("탐지된 값이 없으면 모든 메시지가 그대로이고 대응표는 비어 있다")
    void unchangedWithoutFindings() {
        ConversationMask r = maskAll("회의록 요약해 줘", "네, 요약했습니다");

        assertThat(r.maskedTexts()).containsExactly("회의록 요약해 줘", "네, 요약했습니다");
        assertThat(r.pseudonymToValue()).isEmpty();
    }

    @Test
    @DisplayName("AC-4.3 답의 가명을 대화 전체의 대응표로 되돌리고, 대응표에 없는 가명 모양은 그대로 둔다")
    void restoresAnswerWithConversationMap() {
        ConversationMask r = maskAll("김민수 010-2345-6789", "박서연 010-9876-5432");

        RestoreResult restored = masker.restore("연락처는 [전화1] 와 [전화2] 입니다. [전화9]는 그대로", r);

        assertThat(restored.text()).isEqualTo("연락처는 010-2345-6789 와 010-9876-5432 입니다. [전화9]는 그대로");
        assertThat(restored.restored()).isEqualTo(2);
    }
}
