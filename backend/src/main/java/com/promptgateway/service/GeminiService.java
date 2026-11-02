package com.promptgateway.service;

import com.promptgateway.dto.gemini.GeminiRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * "실제 LLM(Gemini)을 호출하는 방법을 아는" 유일한 클래스.
 * 컨트롤러는 이 서비스가 어떻게 Gemini를 부르는지 몰라도 되고, 그냥 generateAnswer(prompt)만 호출하면 된다.
 * -> 이게 컨트롤러/서비스를 나누는 이유: "요청을 받는 역할"과 "실제 작업을 하는 역할"을 분리해서,
 *    나중에 4단계에서 필터 로직이 추가돼도 컨트롤러 코드는 거의 안 바뀌게 된다.
 */
@Service
@ConditionalOnProperty(name = "gateway.demo-answer", havingValue = "false", matchIfMissing = true)
public class GeminiService implements AnswerGenerator {

    private final RestTemplate restTemplate;

    // application.yml의 값을 그대로 필드에 주입받는다. (0-1단계에서 찍먹했던 @Value가 여기서 실전으로 쓰임)
    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    @Value("${gemini.base-url}")
    private String baseUrl;

    public GeminiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String generateAnswer(String prompt) {
        return call(GeminiRequest.of(prompt));
    }

    /** 대화 전체를 Gemini의 여러 턴 형식으로 보낸다(FR-11). */
    @Override
    public String generateAnswer(List<ChatTurn> conversation) {
        return call(GeminiRequest.of(conversation));
    }

    // 실제 호출(키는 헤더로, 응답 JSON을 GeminiResponse로)은 방문자 키와 같이 쓰는 GeminiCall에 있다(AC-12.3).
    private String call(GeminiRequest requestBody) {
        return GeminiCall.call(restTemplate, baseUrl, model, apiKey, requestBody);
    }
}
