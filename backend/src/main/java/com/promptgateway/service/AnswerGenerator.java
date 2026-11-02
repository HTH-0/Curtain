package com.promptgateway.service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * "통과한 프롬프트에 답변을 만들어 주는" 응답 모델. 지금 구현은 GeminiService 하나이다.
 * ChatGateway가 구체적인 Gemini 코드를 몰라도 되게 하고, 시험에서는 가짜 응답 모델로 바꿔 끼운다.
 */
public interface AnswerGenerator {

    String generateAnswer(String prompt);

    /**
     * 대화 전체를 받아 다음 답을 만든다(FR-11). 기본 구현은 대화를 "역할: 내용" 줄로 이어 붙여 위의 메서드를 부른다(가짜 응답 모델용).
     * 실제 응답 모델(GeminiService)은 대화 형식으로 보내도록 다시 정의한다.
     */
    default String generateAnswer(List<ChatTurn> conversation) {
        return generateAnswer(conversation.stream().map(t -> t.role() + ": " + t.text()).collect(Collectors.joining("\n")));
    }
}
