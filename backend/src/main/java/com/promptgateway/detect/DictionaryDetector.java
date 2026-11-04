package com.promptgateway.detect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * D3 회사 기밀 탐지. 회사 사전(코드명, 고객사, 등급 표시)과 대소문자 구분 없이 일치하는 곳을 찾는다.
 * 사전은 설정 파일에서 읽는다(docs/dummy-company.md). 사전에 없는 기밀(문맥 판정)은 범위 밖이다.
 */
public class DictionaryDetector implements Detector {

    /** spread는 글자마다 공백으로 띄운 모양을 찾는 식이다. 공백을 뺀 글자가 3개 미만이면 null이다(AC-13.5). */
    private record Entry(Item item, String word, Pattern spread) {
    }

    private static final int MIN_SPREAD_LETTERS = 3;

    private final List<Entry> entries = new ArrayList<>();

    public DictionaryDetector(List<String> codenames, List<String> clients, List<String> classified) {
        codenames.forEach(w -> add(Item.CODENAME, w));
        clients.forEach(w -> add(Item.CLIENT, w));
        classified.forEach(w -> add(Item.CLASSIFIED, w));
    }

    private void add(Item item, String word) {
        if (word == null || word.isBlank()) {
            throw new IllegalArgumentException("회사 사전에 빈 단어가 있습니다: " + item);
        }
        String lower = word.trim().toLowerCase(Locale.ROOT);
        entries.add(new Entry(item, lower, spreadPattern(lower)));
    }

    /**
     * 공백을 뺀 글자 사이마다 공백이나 줄바꿈이 하나 이상 있는 모양(예: 글 자 마 다)을 찾는 식. 모든 간격이 떨어져 있어야 하고
     * 첫 글자 앞은 글자나 숫자가 아니어야 한다. 일부만 띄운 경우와 짧은 단어는 오탐을 막으려고 찾지 않는다(AC-13.5).
     */
    private static Pattern spreadPattern(String word) {
        List<String> letters = word.codePoints().filter(cp -> !Character.isWhitespace(cp))
                .mapToObj(cp -> Pattern.quote(new String(Character.toChars(cp)))).toList();
        if (letters.size() < MIN_SPREAD_LETTERS) {
            return null;
        }
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + String.join("\\s+", letters),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    @Override
    public List<Finding> detect(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<Finding> found = new ArrayList<>();
        for (Entry e : entries) {
            int from = 0;
            int at;
            while ((at = lower.indexOf(e.word(), from)) >= 0) {
                int end = at + e.word().length();
                found.add(new Finding(e.item(), at, end, text.substring(at, end)));
                from = end;
            }
            if (e.spread() != null) {
                Matcher m = e.spread().matcher(text);
                while (m.find()) {
                    found.add(new Finding(e.item(), m.start(), m.end(), m.group()));
                }
            }
        }
        return found;
    }
}
