package com.promptgateway.compat;

/** OpenAI 형식의 오류 본문. message는 고정 문구이고 내부 주소, 키, 외부 응답 본문은 싣지 않는다(AC-11.7, 11.8, 11.10). */
public record OpenAiError(Error error) {

    public record Error(String message, String type, String code) {
    }

    public static OpenAiError of(String message, String type, String code) {
        return new OpenAiError(new Error(message, type, code));
    }
}
