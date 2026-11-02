package com.promptgateway.service;

import com.promptgateway.dto.gemini.GeminiRequest;
import com.promptgateway.dto.gemini.GeminiResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

/**
 * Gemini를 한 번 부르는 방법. 서버 키(GeminiService)와 방문자 키(UserKeyAnswerers)가 같이 쓴다.
 * 키는 요청 주소에 넣지 않고 x-goog-api-key 헤더로 보낸다. 주소는 예외 메시지에 그대로 실리므로 주소에 키가 있으면 로그로 샐 수 있다(AC-12.3).
 */
final class GeminiCall {

    private GeminiCall() {
    }

    static String call(RestTemplate restTemplate, String baseUrl, String model, String apiKey, GeminiRequest requestBody) {
        HttpHeaders headers = new HttpHeaders();
        // charset을 명시해 서버가 다른 인코딩으로 오해할 여지를 없앤다(이 컴퓨터의 기본 인코딩은 MS949이다)
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        headers.set("x-goog-api-key", apiKey);

        GeminiResponse response = restTemplate.postForObject(baseUrl + "/" + model + ":generateContent",
                new HttpEntity<>(requestBody, headers), GeminiResponse.class);
        if (response == null) {
            throw new IllegalStateException("Gemini API 응답이 비어 있습니다.");
        }
        return response.extractAnswerText();
    }
}
