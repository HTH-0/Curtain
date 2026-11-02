package com.promptgateway.compat;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** OpenAI 형식의 응답 본문(FR-11). 스트리밍 chunk는 object가 chat.completion.chunk이고 message 대신 delta를 쓴다. */
public record ChatCompletionResponse(String id, String object, long created, String model, List<Choice> choices) {

    public record Choice(int index, Message message, @JsonProperty("finish_reason") String finishReason) {
    }

    public record Message(String role, String content) {
    }

    public record ChunkChoice(int index, Message delta, @JsonProperty("finish_reason") String finishReason) {
    }

    public record Chunk(String id, String object, long created, String model, List<ChunkChoice> choices) {
    }
}
