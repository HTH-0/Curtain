package com.promptgateway.gateway;

import com.promptgateway.detect.FindingCount;
import com.promptgateway.detect.Item;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 직원에게 보여 주는 항목 이름(포털 화면의 이름과 같다). 안내 문구와 차단 문구에 쓰며, 값이나 위치는 담지 않는다.
 * detect 패키지는 동결 대상이라 이름 표를 여기에 둔다.
 */
public final class ItemNames {

    private ItemNames() {
    }

    public static String of(Item item) {
        return switch (item) {
            case RRN -> "주민등록번호";
            case FRN -> "외국인등록번호";
            case CARD -> "카드번호";
            case PHONE -> "휴대전화";
            case ACCOUNT -> "계좌번호";
            case EMAIL -> "이메일";
            case PRIVATE_KEY -> "개인 키";
            case CONN_STRING -> "접속 정보";
            case API_KEY -> "API 키";
            case JWT -> "토큰";
            case PASSWORD -> "비밀번호";
            case CLASSIFIED -> "보안 등급 표시";
            case CODENAME -> "프로젝트 코드명";
            case CLIENT -> "고객사 이름";
        };
    }

    /** "휴대전화 2건, 이메일 1건"처럼 항목별 개수를 한 줄로 만든다. */
    public static String summary(List<FindingCount> counts) {
        return counts.stream().map(c -> of(c.item()) + " " + c.count() + "건").collect(Collectors.joining(", "));
    }
}
