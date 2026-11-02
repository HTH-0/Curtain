package com.promptgateway.gateway;

import com.promptgateway.detect.Action;
import com.promptgateway.detect.FindingCount;

import java.util.List;

/**
 * 대화 하나를 처리한 결과(FR-11). 경고는 확인할 방법이 없어 가림으로 처리하므로 action은 ALLOW, MASK, BLOCK 중 하나이다.
 * answer는 복원한 답이고 가림이면 맨 앞에 안내 한 줄이 붙어 있다. 차단이면 null이다.
 */
public record ConversationResult(Action action, List<FindingCount> counts, String answer, int restored) {
}
