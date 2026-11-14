package com.carin.noraebang.memory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 추억 저장 원문(JSON 배열 문자열) ↔ Memory 목록 변환.
 *
 * 손상된 데이터를 만나도 원문을 잃지 않는 것이 목적이다.
 * - 배열 자체를 읽을 수 없으면 {@link DecodeResult#topLevelCorrupt}로 표시하고 원문을 그대로 들고 있는다.
 *   이 경우 호출 측은 저장(덮어쓰기)을 하면 안 된다.
 * - 배열 안의 레코드 일부만 읽을 수 없으면 읽을 수 있는 기록만 돌려주고,
 *   읽지 못한 레코드는 원래 JSON 값 그대로 {@link DecodeResult#unreadable}에 보관해 다시 저장할 때 함께 쓴다.
 *
 * Android API에 의존하지 않으므로 JVM 단위 테스트로 검증한다.
 */
public final class MemoryCodec {

    /** 원문을 읽은 결과 */
    public static final class DecodeResult {
        /** 정상적으로 읽은 기록 (원문 순서) */
        public final List<Memory> memories;
        /** 읽지 못한 레코드의 원래 JSON 값 (JSONObject, 문자열, 숫자 등). 저장 시 그대로 다시 쓴다 */
        public final List<Object> unreadable;
        /** 배열 자체를 읽을 수 없음 → 저장 금지 */
        public final boolean topLevelCorrupt;
        /** 읽기에 사용한 원문 (null 가능) */
        public final String originalRaw;

        DecodeResult(List<Memory> memories, List<Object> unreadable,
                     boolean topLevelCorrupt, String originalRaw) {
            this.memories = Collections.unmodifiableList(memories);
            this.unreadable = Collections.unmodifiableList(unreadable);
            this.topLevelCorrupt = topLevelCorrupt;
            this.originalRaw = originalRaw;
        }

        /** 손상(전체 또는 일부)이 있는지 */
        public boolean hasProblem() {
            return topLevelCorrupt || !unreadable.isEmpty();
        }

        /** 이 상태에서 저장해도 원문 손실이 없는지 (배열 전체 손상이면 false) */
        public boolean canWrite() {
            return !topLevelCorrupt;
        }
    }

    private MemoryCodec() {
    }

    /**
     * 저장 원문을 읽는다. 예외를 던지지 않는다.
     *
     * @param raw SharedPreferences에 저장된 문자열. null/빈 값이면 기록 없음
     */
    public static DecodeResult decode(String raw) {
        List<Memory> memories = new ArrayList<>();
        List<Object> unreadable = new ArrayList<>();

        if (raw == null || raw.trim().isEmpty()) {
            return new DecodeResult(memories, unreadable, false, raw);
        }

        JSONArray array;
        try {
            array = new JSONArray(raw);
        } catch (JSONException e) {
            return new DecodeResult(memories, unreadable, true, raw);
        }

        for (int i = 0; i < array.length(); i++) {
            Object element = array.opt(i);
            Memory memory = null;
            if (element instanceof JSONObject) {
                try {
                    memory = Memory.fromJson((JSONObject) element);
                } catch (JSONException | RuntimeException e) {
                    memory = null;
                }
            }
            if (memory != null) {
                memories.add(memory);
            } else {
                unreadable.add(element == null ? JSONObject.NULL : element);
            }
        }
        return new DecodeResult(memories, unreadable, false, raw);
    }

    /**
     * 기록 목록을 저장 원문으로 만든다. 읽지 못했던 레코드는 원래 값 그대로 뒤에 붙인다.
     *
     * @return JSON 배열 문자열. 직렬화에 실패하면 null (호출 측은 저장하지 않는다)
     */
    public static String encode(List<Memory> memories, List<Object> unreadable) {
        JSONArray array = new JSONArray();
        try {
            for (Memory memory : memories) {
                array.put(memory.toJson());
            }
        } catch (JSONException e) {
            return null;
        }
        if (unreadable != null) {
            for (Object value : unreadable) {
                array.put(value);
            }
        }
        return array.toString();
    }
}
