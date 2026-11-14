package com.carin.noraebang.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 저장된 장소명과 내비 목적지(이름/주소)를 지역 단위로 비교한다.
 *
 * 단순 substring 비교 대신, 공백/쉼표로 토큰을 나눈 뒤 각 토큰에서
 * 행정구역 접미사(시, 역, 광역시 등)를 떼어낸 정규화 키를 완전 일치로 비교한다.
 * 예) "강릉" ↔ "강원특별자치도 강릉시 경포로" → 토큰 "강릉시" → "강릉" → 일치
 */
public final class PlaceMatcher {

    /** 긴 접미사가 먼저 오도록 정렬된 행정구역/장소 접미사 */
    private static final String[] SUFFIXES = {
            "특별자치도", "특별자치시", "특별시", "광역시",
            "해수욕장", "시청", "터미널",
            "해변", "역", "시", "군", "구", "항", "도"
    };

    private PlaceMatcher() {
    }

    /** 장소명 1개를 비교용 키로 정규화. 비교 불가(빈 값)이면 "" */
    public static String normalize(String place) {
        if (place == null) return "";
        String trimmed = place.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if (trimmed.isEmpty()) return "";
        return stripSuffix(trimmed);
    }

    /**
     * 저장된 장소가 목적지 이름 또는 주소와 같은 지역인지 판정한다.
     * 비교 대상이 모두 비어 있으면 false.
     */
    public static boolean matches(String savedPlace, String destinationName, String destinationAddress) {
        String key = normalize(savedPlace);
        if (key.isEmpty()) return false;

        for (String token : tokens(destinationName)) {
            if (key.equals(token)) return true;
        }
        for (String token : tokens(destinationAddress)) {
            if (key.equals(token)) return true;
        }
        return false;
    }

    /** 문자열을 공백/쉼표/괄호 기준으로 나누고 각 토큰을 정규화 */
    static List<String> tokens(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) return result;
        String[] parts = text.trim().toLowerCase(Locale.ROOT).split("[\\s,()\\[\\]/·]+");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            String stripped = stripSuffix(part);
            if (!stripped.isEmpty()) result.add(stripped);
            if (!stripped.equals(part)) result.add(part);
        }
        return result;
    }

    private static String stripSuffix(String token) {
        for (String suffix : SUFFIXES) {
            if (token.length() > suffix.length() + 1 && token.endsWith(suffix)) {
                return token.substring(0, token.length() - suffix.length());
            }
        }
        return token;
    }
}
