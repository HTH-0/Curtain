package com.promptgateway.dto.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.promptgateway.service.ChatTurn;

import java.util.ArrayList;
import java.util.List;

/**
 * 우리가 Gemini API로 보내는 요청 body 전체 모양.
 * 1단계 PowerShell에서 만들었던 이 구조를 그대로 자바 객체로 옮긴 것:
 *   { "contents": [ { "parts": [ { "text": "..." } ] } ] }
 * 대화는 role이 user, model인 contents 여러 개로 보내고, system 메시지는 systemInstruction에 담는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeminiRequest(List<Content> contents, Content systemInstruction) {

    // 프롬프트 문자열 하나로 바로 요청 객체를 만들 수 있게 해주는 보조 생성자
    public static GeminiRequest of(String prompt) {
        return new GeminiRequest(List.of(Content.of(prompt)), null);
    }

    /**
     * 대화 전체로 요청 객체를 만든다(FR-11). system 메시지는 systemInstruction에 줄바꿈으로 모으고,
     * 나머지는 순서대로 contents에 담는다(assistant는 Gemini의 role 이름인 model). 내용이 공백인 메시지는 Gemini가 거부하므로 뺀다.
     */
    public static GeminiRequest of(List<ChatTurn> conversation) {
        List<Content> contents = new ArrayList<>();
        List<String> system = new ArrayList<>();
        for (ChatTurn turn : conversation) {
            if ("system".equals(turn.role())) {
                system.add(turn.text());
            } else if (!turn.text().isBlank()) {
                contents.add(new Content(List.of(new Part(turn.text())), "assistant".equals(turn.role()) ? "model" : "user"));
            }
        }
        return new GeminiRequest(contents, system.isEmpty() ? null : Content.of(String.join("\n", system)));
    }
}
