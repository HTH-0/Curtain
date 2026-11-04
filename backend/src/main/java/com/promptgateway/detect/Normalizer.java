package com.promptgateway.detect;

import java.util.Arrays;

/**
 * 탐지용 정규화 사본을 만든다(docs/spec.md AC-13.1, AC-13.2). 사본은 탐지에만 쓰고 외부로 보내는 문장은 원문에서 만든다.
 * 사본의 글자마다 원문에서 어느 구간에서 왔는지 기억해서, 사본에서 찾은 위치를 원문 위치로 되돌린다.
 *
 * 변환 순서: ① 유니코드 NFKC(코드 포인트마다) ② 보이지 않는 문자 삭제 ③ 한글 숫자 3글자 이상을 아라비아 숫자로
 * ④ 한 글자씩 공백으로 떨어진 숫자와 하이픈 7개 이상의 공백 삭제.
 */
public final class Normalizer {

    private static final String KOREAN_DIGITS = "공일이삼사오육칠팔구";
    private static final int MIN_KOREAN_DIGIT_RUN = 3;
    private static final int MIN_SPACED_TOKENS = 7;

    private Normalizer() {
    }

    /**
     * @param text      정규화한 사본
     * @param origStart 사본의 i번째 글자가 시작된 원문 위치(포함)
     * @param origEnd   사본의 i번째 글자가 끝난 원문 위치(제외)
     */
    public record Result(String text, int[] origStart, int[] origEnd) {

        /** 사본의 start 위치(포함)에 해당하는 원문 위치. */
        public int toOriginalStart(int start) {
            return origStart[start];
        }

        /** 사본의 end 위치(제외)에 해당하는 원문 위치(제외). */
        public int toOriginalEnd(int end) {
            return origEnd[end - 1];
        }
    }

    public static Result normalize(String original) {
        Buffer b = new Buffer(original.length());
        int i = 0;
        while (i < original.length()) {
            int cp = original.codePointAt(i);
            int next = i + Character.charCount(cp);
            if (!isInvisible(cp)) {
                String s = java.text.Normalizer.normalize(new String(Character.toChars(cp)), java.text.Normalizer.Form.NFKC);
                for (int k = 0; k < s.length(); k++) {
                    b.add(s.charAt(k), i, next);
                }
            }
            i = next;
        }
        koreanDigits(b);
        return joinSpacedDigits(b);
    }

    private static boolean isInvisible(int cp) {
        return cp == 0x00AD || cp == 0xFEFF
                || (cp >= 0x200B && cp <= 0x200F)
                || (cp >= 0x202A && cp <= 0x202E)
                || (cp >= 0x2060 && cp <= 0x2064)
                || (cp >= 0x2066 && cp <= 0x2069);
    }

    /** ③ 한글 숫자가 3글자 이상 이어진 구간만 아라비아 숫자로 바꾼다(글자 수는 그대로). */
    private static void koreanDigits(Buffer b) {
        int i = 0;
        while (i < b.size) {
            int j = i;
            while (j < b.size && isKoreanDigit(b.chars[j])) {
                j++;
            }
            if (j - i >= MIN_KOREAN_DIGIT_RUN) {
                for (int k = i; k < j; k++) {
                    b.chars[k] = (char) ('0' + digitValue(b.chars[k]));
                }
            }
            i = Math.max(j, i + 1);
        }
    }

    private static boolean isKoreanDigit(char c) {
        return c == '영' || KOREAN_DIGITS.indexOf(c) >= 0;
    }

    private static int digitValue(char c) {
        return c == '영' ? 0 : KOREAN_DIGITS.indexOf(c); // 목록 순서가 곧 숫자 값이다(공=0, 일=1, ...)
    }

    /** ④ 한 글자 토큰(숫자나 하이픈)이 공백 하나로 7개 이상 이어진 구간의 공백을 지운다. */
    private static Result joinSpacedDigits(Buffer b) {
        boolean[] drop = new boolean[b.size];
        int i = 0;
        while (i < b.size) {
            if (isSingleToken(b, i) && (i == 0 || Character.isWhitespace(b.chars[i - 1]))) {
                int count = 1;
                int j = i;
                while (j + 2 < b.size && b.chars[j + 1] == ' ' && isSingleToken(b, j + 2)) {
                    count++;
                    j += 2;
                }
                if (count >= MIN_SPACED_TOKENS) {
                    for (int k = i + 1; k <= j; k += 2) {
                        drop[k] = true;
                    }
                }
                i = j + 1;
            } else {
                i++;
            }
        }
        int kept = 0;
        for (boolean d : drop) {
            if (!d) {
                kept++;
            }
        }
        char[] chars = new char[kept];
        int[] start = new int[kept];
        int[] end = new int[kept];
        int w = 0;
        for (int k = 0; k < b.size; k++) {
            if (!drop[k]) {
                chars[w] = b.chars[k];
                start[w] = b.start[k];
                end[w] = b.end[k];
                w++;
            }
        }
        return new Result(new String(chars), start, end);
    }

    /** 위치 i의 글자가 숫자나 하이픈이고, 바로 뒤가 문장 끝이거나 공백이다(한 글자짜리 토큰). */
    private static boolean isSingleToken(Buffer b, int i) {
        char c = b.chars[i];
        boolean tokenChar = (c >= '0' && c <= '9') || c == '-';
        return tokenChar && (i + 1 == b.size || Character.isWhitespace(b.chars[i + 1]));
    }

    /** 글자와 원문 구간을 함께 쌓는 작은 버퍼. */
    private static final class Buffer {
        char[] chars;
        int[] start;
        int[] end;
        int size;

        Buffer(int capacity) {
            int c = Math.max(capacity, 16);
            chars = new char[c];
            start = new int[c];
            end = new int[c];
        }

        void add(char c, int s, int e) {
            if (size == chars.length) {
                int n = size * 2;
                chars = Arrays.copyOf(chars, n);
                start = Arrays.copyOf(start, n);
                end = Arrays.copyOf(end, n);
            }
            chars[size] = c;
            start[size] = s;
            end[size] = e;
            size++;
        }
    }
}
