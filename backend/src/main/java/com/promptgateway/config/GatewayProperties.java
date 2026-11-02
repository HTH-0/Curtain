package com.promptgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.yml의 gateway 아래 설정. 입력 최대 길이(기본 4,000자, 명세 v2 S1)와 측정 모드(기본 꺼짐).
 * maskNotice는 가림 요청 앞에 가명 안내문을 붙일지 정한다(AC-4.6, 기본 꺼짐).
 * 측정 모드는 켜도 같은 컴퓨터에서 온 요청에만 적용된다(ChatController).
 * maxConversationLength는 OpenAI 호환 입구의 대화 전체 최대 길이이다(AC-11.8, 기본 20,000자).
 * 동결 대상인 application.yml을 고치지 않으려고 기본값을 코드에 둔다.
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        @DefaultValue("4000") int maxPromptLength,
        @DefaultValue("false") boolean measurementMode,
        @DefaultValue("false") boolean maskNotice,
        @DefaultValue("20000") int maxConversationLength) {

    public GatewayProperties {
        if (maxPromptLength <= 0) {
            throw new IllegalArgumentException("gateway.max-prompt-length는 0보다 커야 합니다: " + maxPromptLength);
        }
        if (maxConversationLength <= 0) {
            throw new IllegalArgumentException("gateway.max-conversation-length는 0보다 커야 합니다: " + maxConversationLength);
        }
    }
}
