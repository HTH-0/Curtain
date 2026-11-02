package com.promptgateway.compat;

import com.promptgateway.detect.Action;
import com.promptgateway.gateway.ConversationGateway;
import com.promptgateway.gateway.ConversationResult;
import com.promptgateway.gateway.ItemNames;
import com.promptgateway.service.AnswerGenerator;
import com.promptgateway.service.ChatTurn;
import com.promptgateway.service.UserKeyAnswerers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * OpenAI 호환 입구(FR-11, design.md 9장). 요청을 ConversationGateway에 맡기고 OpenAI 형식으로 바꾼다.
 *
 *   허용, 가림   -> 200 chat.completion (stream=true이면 완성된 답을 SSE 한 건과 [DONE]으로)
 *   차단        -> 403 OpenAI 오류 형식(범주별 개수와 안내만, 값은 없다)
 *   입력 오류    -> 400 (CompatExceptionHandler)
 *
 * 요청의 model은 쓰지 않는다. Authorization의 Bearer 값이 Gemini 키 형식이면 그 요청에만 그 키를 쓴다(FR-12). 입력 검증은 모두 ConversationGateway가 해서 실패도 로그 한 줄이 남는다.
 */
@RestController
public class CompatController {

    static final String MODEL = "curtain";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final long STARTED = Instant.now().getEpochSecond();

    private final ConversationGateway gateway;
    private final UserKeyAnswerers userKeys;

    public CompatController(ConversationGateway gateway) {
        this(gateway, null);
    }

    @Autowired
    public CompatController(ConversationGateway gateway, UserKeyAnswerers userKeys) {
        this.gateway = gateway;
        this.userKeys = userKeys;
    }

    @GetMapping("/v1/models")
    public ModelList models() {
        return new ModelList("list", List.of(new ModelList.Model(MODEL, "model", STARTED, "curtain")));
    }

    @PostMapping("/v1/chat/completions")
    public ResponseEntity<?> completions(@RequestBody ChatCompletionRequest request,
                                         @RequestHeader(value = "Authorization", required = false) String authorization) {
        // API 키 칸의 값이 Gemini 키 형식이면 그 요청에만 그 키를 쓴다(AC-11.12, FR-12). 아니면 서버 기본 응답이다.
        AnswerGenerator visitor = userKeys == null ? null : userKeys.forKeyOrNull(bearer(authorization));
        ConversationResult result = gateway.handle(toTurns(request), visitor);

        if (result.action() == Action.BLOCK) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(OpenAiError.of(blockMessage(result), "curtain_blocked", "blocked"));
        }

        String id = "chatcmpl-" + UUID.randomUUID();
        long created = Instant.now().getEpochSecond();
        if (request.isStream()) {
            ChatCompletionResponse.Chunk chunk = new ChatCompletionResponse.Chunk(id, "chat.completion.chunk", created, MODEL,
                    List.of(new ChatCompletionResponse.ChunkChoice(0, new ChatCompletionResponse.Message("assistant", result.answer()), "stop")));
            String events = "data: " + JSON.writeValueAsString(chunk) + "\n\ndata: [DONE]\n\n";
            return ResponseEntity.ok().contentType(new MediaType("text", "event-stream", StandardCharsets.UTF_8)).body(events);
        }
        return ResponseEntity.ok(new ChatCompletionResponse(id, "chat.completion", created, MODEL,
                List.of(new ChatCompletionResponse.Choice(0, new ChatCompletionResponse.Message("assistant", result.answer()), "stop"))));
    }

    private static String bearer(String authorization) {
        return authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7) ? authorization.substring(7) : null;
    }

    /** content가 문자열이 아니거나 없으면 text를 null로 넘긴다. 거부는 ConversationGateway가 한다(로그 한 줄이 남는다). */
    private static List<ChatTurn> toTurns(ChatCompletionRequest request) {
        if (request.messages() == null) {
            return List.of();
        }
        return request.messages().stream()
                .map(m -> m == null ? new ChatTurn(null, null) : new ChatTurn(m.role(), m.content() instanceof String s ? s : null))
                .toList();
    }

    private static String blockMessage(ConversationResult result) {
        return "보내지 않았습니다(" + ItemNames.summary(result.counts()) + "). 자격증명이나 보안 등급 표시가 있는 내용은 외부 AI로 보내지 않습니다. "
                + "해당 값을 지우고 다시 보내세요.";
    }
}
