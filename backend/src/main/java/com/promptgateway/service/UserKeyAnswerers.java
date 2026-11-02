package com.promptgateway.service;

import com.promptgateway.dto.gemini.GeminiRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 방문자가 넣은 Gemini 키로 답하는 응답 모델을 요청마다 만든다(FR-12). 키는 만든 응답 모델 안에만 있고 저장하지 않는다.
 * 서버 키가 없는 시연 모드에서도 쓸 수 있도록 서버 키(GeminiService)와 따로 둔다.
 */
@Component
public class UserKeyAnswerers {

    /**
     * Google 키 형식: AIza로 시작하는 39자, 또는 AQ.로 시작하는 새 형식. 이 형식이 아니면(예: Chatbox에 넣던 "아무 값") 키로 보지 않고
     * 서버 기본 응답을 쓴다(AC-12.1). 긴 값을 모두 받지 않는 것은 다른 서비스의 키(sk- 등)를 Google로 보내지 않기 위해서이다.
     */
    private static final Pattern GEMINI_KEY = Pattern.compile("AIza[0-9A-Za-z_-]{35}|AQ\\.[A-Za-z0-9._-]{20,200}");

    private final RestTemplate restTemplate;
    private final String model;
    private final String baseUrl;

    public UserKeyAnswerers(RestTemplate restTemplate, @Value("${gemini.model}") String model, @Value("${gemini.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    /** Google 키 형식인가(앞뒤 공백은 무시한다). */
    public static boolean isGeminiKey(String value) {
        return value != null && GEMINI_KEY.matcher(value.strip()).matches();
    }

    /** 키 형식이면 그 키로 답하는 응답 모델, 아니면 null(게이트웨이가 서버 기본 응답을 쓴다). */
    public AnswerGenerator forKeyOrNull(String value) {
        return isGeminiKey(value) ? forKey(value.strip()) : null;
    }

    public AnswerGenerator forKey(String key) {
        return new AnswerGenerator() {
            @Override
            public String generateAnswer(String prompt) {
                return call(GeminiRequest.of(prompt));
            }

            @Override
            public String generateAnswer(List<ChatTurn> conversation) {
                return call(GeminiRequest.of(conversation));
            }

            /** Gemini가 키를 거부하면(400, 401, 403) 방문자에게 키를 확인하라고 알린다. 외부 응답 본문은 버린다(AC-12.4). */
            private String call(GeminiRequest body) {
                try {
                    return GeminiCall.call(restTemplate, baseUrl, model, key, body);
                } catch (HttpClientErrorException.BadRequest | HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                    throw new UserKeyRejectedException();
                }
            }
        };
    }
}
