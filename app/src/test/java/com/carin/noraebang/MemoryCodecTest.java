package com.carin.noraebang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.carin.noraebang.memory.Memory;
import com.carin.noraebang.memory.MemoryCodec;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 추억 저장 원문 손상 시 데이터가 사라지지 않는지 검증한다.
 * MemoryStore는 쓰기 직전에 decode → (변경) → encode 하고, topLevelCorrupt면 쓰지 않는다.
 */
public class MemoryCodecTest {

    private static final String GOOD_1 = "{\"id\":\"a\",\"place\":\"강릉\",\"title\":\"강릉 여행\","
            + "\"travelDate\":\"2025-08-15\",\"songId\":\"carin_song_01\",\"createdAt\":1,\"demo\":false,"
            + "\"photoUri\":\"file:///data/photos/a.jpg\"}";
    private static final String GOOD_2 = "{\"id\":\"b\",\"place\":\"부산\",\"title\":\"부산 여행\","
            + "\"travelDate\":\"2024-05-01\",\"songId\":\"carin_song_02\",\"createdAt\":2,\"demo\":true}";
    /** 필수 필드(songId, createdAt)가 없는 손상 레코드 */
    private static final String BROKEN = "{\"id\":\"c\",\"place\":\"속초\",\"title\":\"깨진 기록\"}";

    @Test
    public void emptyOrMissingRaw_isEmptyAndWritable() {
        for (String raw : new String[] {null, "", "   "}) {
            MemoryCodec.DecodeResult result = MemoryCodec.decode(raw);
            assertTrue(result.memories.isEmpty());
            assertFalse(result.hasProblem());
            assertTrue(result.canWrite());
        }
    }

    @Test
    public void validData_roundTripKeepsAllFields() throws Exception {
        MemoryCodec.DecodeResult result = MemoryCodec.decode("[" + GOOD_1 + "," + GOOD_2 + "]");
        assertEquals(2, result.memories.size());
        assertFalse(result.hasProblem());

        String encoded = MemoryCodec.encode(result.memories, result.unreadable);
        MemoryCodec.DecodeResult again = MemoryCodec.decode(encoded);
        assertEquals(2, again.memories.size());
        Memory first = again.memories.get(0);
        assertEquals("a", first.id);
        assertEquals("강릉", first.place);
        assertEquals("carin_song_01", first.songId);
        assertEquals("file:///data/photos/a.jpg", first.photoUri);
        assertTrue(again.memories.get(1).demo);
    }

    @Test
    public void legacyRecordWithoutOptionalFields_isReadable() {
        String legacy = "[{\"id\":\"old\",\"place\":\"경주\",\"title\":\"예전 기록\","
                + "\"travelDate\":\"2023-01-01\",\"songId\":\"carin_song_03\",\"createdAt\":5}]";
        MemoryCodec.DecodeResult result = MemoryCodec.decode(legacy);
        assertEquals(1, result.memories.size());
        assertFalse(result.memories.get(0).demo);
        assertNull(result.memories.get(0).photoUri);
        assertFalse(result.hasProblem());
    }

    @Test
    public void partiallyBrokenRecord_isPreservedAfterAWrite() throws Exception {
        MemoryCodec.DecodeResult result =
                MemoryCodec.decode("[" + GOOD_1 + "," + BROKEN + "," + GOOD_2 + ",42]");
        assertEquals(2, result.memories.size());
        assertEquals(2, result.unreadable.size());
        assertTrue(result.canWrite());

        // 저장 작업 흉내: 읽은 기록 하나 삭제 후 다시 저장
        List<Memory> changed = new ArrayList<>(result.memories);
        changed.remove(0);
        String encoded = MemoryCodec.encode(changed, result.unreadable);

        JSONArray saved = new JSONArray(encoded);
        assertEquals(3, saved.length());
        boolean brokenKept = false;
        boolean numberKept = false;
        for (int i = 0; i < saved.length(); i++) {
            Object value = saved.get(i);
            if (value instanceof JSONObject && "c".equals(((JSONObject) value).optString("id"))) {
                brokenKept = "깨진 기록".equals(((JSONObject) value).optString("title"));
            }
            if (value instanceof Number && ((Number) value).intValue() == 42) numberKept = true;
        }
        assertTrue("읽지 못한 레코드가 원래 내용 그대로 남아야 한다", brokenKept);
        assertTrue("객체가 아닌 값도 보존해야 한다", numberKept);

        MemoryCodec.DecodeResult again = MemoryCodec.decode(encoded);
        assertEquals(1, again.memories.size());
        assertEquals(2, again.unreadable.size());
    }

    @Test
    public void corruptArray_isFlaggedAndNotWritable() {
        String raw = "[" + GOOD_1 + ", {\"id\": \"d\"";   // 잘린 JSON
        MemoryCodec.DecodeResult result = MemoryCodec.decode(raw);
        assertTrue(result.topLevelCorrupt);
        assertFalse(result.canWrite());
        assertTrue(result.memories.isEmpty());
        assertEquals("원문을 그대로 들고 있어야 백업할 수 있다", raw, result.originalRaw);
    }

    @Test
    public void nonArrayTopLevel_isTreatedAsCorrupt() {
        MemoryCodec.DecodeResult result = MemoryCodec.decode(GOOD_1);
        assertTrue(result.topLevelCorrupt);
        assertFalse(result.canWrite());
    }
}
