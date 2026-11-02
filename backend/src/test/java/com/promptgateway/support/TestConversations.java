package com.promptgateway.support;

import com.promptgateway.detect.ActionDecider;
import com.promptgateway.gateway.ConversationGateway;
import com.promptgateway.log.GatewayLogger;
import com.promptgateway.mask.Masker;
import com.promptgateway.service.AnswerGenerator;
import com.promptgateway.service.ChatTurn;

/** 대화 입구(FR-11) 시험용 조립. 탐지 설정은 TestGateways와 같고 대량 기준은 20이다. */
public final class TestConversations {

    private TestConversations() {
    }

    public static ConversationGateway gateway(AnswerGenerator answerer, GatewayLogger logger) {
        return gateway(answerer, logger, 20000);
    }

    public static ConversationGateway gateway(AnswerGenerator answerer, GatewayLogger logger, int maxConversationLength) {
        return new ConversationGateway(TestGateways.engine(), new ActionDecider(20), new Masker(), answerer, logger,
                maxConversationLength);
    }

    public static ChatTurn system(String text) {
        return new ChatTurn("system", text);
    }

    public static ChatTurn user(String text) {
        return new ChatTurn("user", text);
    }

    public static ChatTurn assistant(String text) {
        return new ChatTurn("assistant", text);
    }
}
