package com.promptgateway.gateway;

import com.promptgateway.detect.Action;
import com.promptgateway.log.GatewayLogger;
import com.promptgateway.support.TestGateways;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가림 요청에 붙이는 가명 안내문(AC-4.6). 외부 AI가 가명을 진짜 값으로 오해하거나 바꿔 쓰지 않게 알린다.
 * 설정으로 켜고 끈다. 꺼져 있으면 1차 Z 측정과 같은 문장이 나간다.
 */
class MaskNoticeTest {

    static final String PROMPT = "김민수 010-2345-6789, 한결물산 담당자 표로 정리";
    static final String MASKED = "김민수 [전화1], [고객사1] 담당자 표로 정리";

    List<String> sentToAi = new ArrayList<>();

    ChatGateway gateway(boolean maskNotice) {
        return TestGateways.gateway(text -> {
            sentToAi.add(text);
            return "정리 결과: " + text;
        }, new GatewayLogger(line -> { }), false, maskNotice);
    }

    @Test
    @DisplayName("AC-4.6 켜면 가림 요청 앞에 안내문이 붙고, 그 뒤에 가린 문장이 그대로 온다")
    void noticeBeforeMasked() {
        gateway(true).handle(PROMPT, false, false);

        assertThat(ChatGateway.MASK_NOTICE).isNotBlank();
        assertThat(sentToAi).hasSize(1);
        assertThat(sentToAi.getFirst()).isNotEqualTo(MASKED).startsWith(ChatGateway.MASK_NOTICE).endsWith(MASKED);
        assertThat(sentToAi.getFirst()).doesNotContain("010-2345-6789", "한결물산");
    }

    @Test
    @DisplayName("AC-4.6 꺼져 있으면(기본) 가린 문장만 간다")
    void offByDefault() {
        gateway(false).handle(PROMPT, false, false);

        assertThat(sentToAi).containsExactly(MASKED);
    }

    @Test
    @DisplayName("AC-4.6 허용 요청에는 안내문을 붙이지 않는다")
    void notOnAllow() {
        GatewayResult r = gateway(true).handle("회의록 요약해 줘", false, false);

        assertThat(r.action()).isEqualTo(Action.ALLOW);
        assertThat(sentToAi).containsExactly("회의록 요약해 줘");
    }

    @Test
    @DisplayName("AC-6.4 직원에게 돌려주는 보낸 문장에는 안내문이 없고, 답은 원래 값으로 복원된다")
    void sentPromptAndRestoreUnaffected() {
        GatewayResult r = gateway(true).handle(PROMPT, false, false);

        assertThat(r.maskedPrompt()).isEqualTo(MASKED);
        assertThat(r.answer()).contains("김민수 010-2345-6789, 한결물산 담당자");
        assertThat(r.restored()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-4.6 안내문은 가명 모양 글자를 담지 않는다(되풀이해도 복원 대상으로 오인되지 않는다)")
    void noticeHasNoPseudonymShape() {
        assertThat(ChatGateway.MASK_NOTICE).doesNotContainPattern("\\[[가-힣A-Za-z]+\\d+\\]");
    }
}
