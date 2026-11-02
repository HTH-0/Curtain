package com.promptgateway.gateway;

import com.promptgateway.detect.Action;
import com.promptgateway.detect.ActionDecider;
import com.promptgateway.detect.DetectionEngine;
import com.promptgateway.detect.Finding;
import com.promptgateway.detect.FindingCount;
import com.promptgateway.log.GatewayLogger;
import com.promptgateway.mask.ConversationMask;
import com.promptgateway.mask.Masker;
import com.promptgateway.mask.RestoreResult;
import com.promptgateway.service.AnswerGenerator;
import com.promptgateway.service.ChatTurn;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * OpenAI 호환 입구의 처리 흐름(명세 FR-11, design.md 9장). 판단은 각 부품이 하고, 이 클래스는 순서만 정한다.
 *
 *   1. 검증   대화가 비었거나 허용 밖 역할이거나 문자열이 아니거나 모두 공백이거나 너무 길면 InvalidPromptException(컨트롤러에서 400)
 *   2. 탐지   모든 메시지를 역할과 관계없이 검사한다. 이전 답에는 복원된 원래 값이 들어 있기 때문이다
 *   3. 결정   대화 전체의 탐지로 결정한다. 경고는 확인할 방법이 없어 가림으로 처리한다(원문은 보내지 않는다)
 *   4. 외부 AI 차단이면 부르지 않는다. 허용이면 대화 그대로, 가림이면 한 대응표로 가린 대화를 보낸다
 *   5. 복원   답의 가명을 원래 값으로 되돌리고, 가림이면 맨 앞에 안내 한 줄을 붙인다
 *   6. 로그   모든 경로에서 정확히 한 줄. 원문과 값은 남기지 않는다
 *
 * 대화는 저장하지 않는다. 클라이언트가 매 턴 대화 전체를 다시 보내므로 매번 처음부터 가리며, 가명 번호는 앞에서부터
 * 정해지므로 턴이 늘어도 앞부분은 같다(AC-11.5). 가명 대응표는 이 메서드 안에서만 쓰고 버린다(AC-4.4).
 */
public class ConversationGateway {

    private static final Set<String> ROLES = Set.of("system", "user", "assistant");

    private final DetectionEngine engine;
    private final ActionDecider decider;
    private final Masker masker;
    private final AnswerGenerator answerGenerator;
    private final GatewayLogger logger;
    private final int maxConversationLength;

    public ConversationGateway(DetectionEngine engine, ActionDecider decider, Masker masker, AnswerGenerator answerGenerator,
                               GatewayLogger logger, int maxConversationLength) {
        this.engine = engine;
        this.decider = decider;
        this.masker = masker;
        this.answerGenerator = answerGenerator;
        this.logger = logger;
        this.maxConversationLength = maxConversationLength;
    }

    public ConversationResult handle(List<ChatTurn> conversation) {
        String joined = join(conversation);
        try {
            validate(conversation);
        } catch (InvalidPromptException e) {
            logger.logError(joined, "VALIDATION", e);
            throw e;
        }

        long t0 = System.nanoTime();
        List<String> texts = conversation.stream().map(ChatTurn::text).toList();
        List<List<Finding>> perText = texts.stream().map(engine::detect).toList();
        List<Finding> all = perText.stream().flatMap(List::stream).toList();
        Action decided = decider.decide(all);
        long detectMs = ms(t0);
        List<FindingCount> counts = distinctCounts(all);

        boolean confirmedWarn = decided == Action.WARN;
        Action action = confirmedWarn ? Action.MASK : decided;

        if (action == Action.BLOCK) {
            logged(joined, new GatewayResult(Action.BLOCK, counts, null, false, null, null,
                    new GatewayResult.Timings(detectMs, null, null, null), 0, false));
            return new ConversationResult(Action.BLOCK, counts, null, 0);
        }
        if (action == Action.ALLOW) {
            long t1 = System.nanoTime();
            String answer = callAnswer(conversation, joined);
            long answerMs = ms(t1);
            logged(joined, new GatewayResult(Action.ALLOW, counts, answer, false, null, null,
                    new GatewayResult.Timings(detectMs, null, answerMs, null), 0, false));
            return new ConversationResult(Action.ALLOW, counts, answer, 0);
        }

        long t1 = System.nanoTime();
        ConversationMask mask = masker.maskAll(texts, perText);
        List<ChatTurn> sent = IntStream.range(0, conversation.size())
                .mapToObj(i -> new ChatTurn(conversation.get(i).role(), mask.maskedTexts().get(i))).toList();
        long maskMs = ms(t1);
        long t2 = System.nanoTime();
        String answer = callAnswer(sent, joined);
        long answerMs = ms(t2);
        long t3 = System.nanoTime();
        RestoreResult restored = masker.restore(answer, mask);
        long restoreMs = ms(t3);
        String finalAnswer = notice(counts) + "\n\n" + Objects.toString(restored.text(), "");
        logged(joined, new GatewayResult(Action.MASK, counts, finalAnswer, confirmedWarn, null, null,
                new GatewayResult.Timings(detectMs, maskMs, answerMs, restoreMs), restored.restored(), false));
        return new ConversationResult(Action.MASK, counts, finalAnswer, restored.restored());
    }

    /**
     * 항목별 개수. 클라이언트가 이전 대화(이전 답 포함)를 매번 되보내므로 같은 값이 여러 메시지에 다시 나온다.
     * 그대로 세면 턴이 늘수록 개수가 부풀기 때문에 같은 값은 한 번만 센다(AC-11.6). 한 문장짜리 대화에서 같은 값이 반복되지 않으면 /api/chat과 같다.
     */
    private static List<FindingCount> distinctCounts(List<Finding> all) {
        Map<String, Finding> firstOfEachValue = new LinkedHashMap<>();
        all.forEach(f -> firstOfEachValue.putIfAbsent(Masker.valueKey(f.item(), f.value()), f));
        return FindingCount.of(List.copyOf(firstOfEachValue.values()));
    }

    /** 가린 범주와 개수를 알리는 한 줄(AC-11.6). 값은 담지 않는다. */
    private static String notice(List<FindingCount> counts) {
        return "[Deskguard] " + ItemNames.summary(counts) + "을 가려서 보냈습니다.";
    }

    private void validate(List<ChatTurn> conversation) {
        if (conversation == null || conversation.isEmpty()) {
            throw new InvalidPromptException("messages가 비어 있습니다.");
        }
        long total = 0;
        boolean anyContent = false;
        for (ChatTurn turn : conversation) {
            if (turn == null || turn.role() == null || !ROLES.contains(turn.role())) {
                throw new InvalidPromptException("허용하지 않는 role입니다. system, user, assistant만 쓸 수 있습니다.");
            }
            if (turn.text() == null) {
                throw new InvalidPromptException("content는 문자열이어야 합니다.");
            }
            anyContent |= !turn.text().isBlank();
            total += turn.text().codePointCount(0, turn.text().length());
        }
        if (!anyContent) {
            throw new InvalidPromptException("모든 메시지가 비어 있습니다.");
        }
        if (total > maxConversationLength) {
            throw new InvalidPromptException("대화 전체는 " + maxConversationLength + "자를 넘을 수 없습니다.");
        }
    }

    /** 외부 AI에 대화를 보낸다. 실패하면 대화 전체의 길이와 해시로 오류 로그를 남기고 그대로 알린다. */
    private String callAnswer(List<ChatTurn> conversation, String joined) {
        try {
            return answerGenerator.generateAnswer(conversation);
        } catch (RuntimeException e) {
            logger.logError(joined, "ANSWER", e);
            throw e;
        }
    }

    private void logged(String joined, GatewayResult result) {
        logger.logResult(joined, result);
    }

    /** 로그의 길이와 해시는 메시지를 이어 붙인 전체 기준이다(AC-11.11). */
    private static String join(List<ChatTurn> conversation) {
        if (conversation == null) {
            return null;
        }
        return conversation.stream().map(t -> t == null ? "" : Objects.toString(t.text(), "")).collect(Collectors.joining("\n"));
    }

    private static long ms(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
