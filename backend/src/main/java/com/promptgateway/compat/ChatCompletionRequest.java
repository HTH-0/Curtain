package com.promptgateway.compat;

import java.util.List;

/** OpenAI 형식의 요청 본문(FR-11). content는 문자열만 받으므로 일단 Object로 읽고 컨트롤러에서 검사한다. */
public record ChatCompletionRequest(String model, List<Message> messages, Boolean stream) {

    public record Message(String role, Object content) {
    }

    public boolean isStream() {
        return Boolean.TRUE.equals(stream);
    }
}
