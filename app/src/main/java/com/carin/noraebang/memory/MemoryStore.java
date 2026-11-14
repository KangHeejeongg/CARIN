package com.carin.noraebang.memory;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 추억 기록을 SharedPreferences + JSON으로 저장하는 로컬 저장소.
 * Room 등 새 라이브러리를 도입하지 않고 앱 재실행 후에도 데이터를 유지한다.
 */
public final class MemoryStore {

    private static final String TAG = "CARIN_MEMORY";
    private static final String PREFS = "carin_memory_route";
    private static final String KEY_MEMORIES = "memories";

    public static final String DEMO_PLACE = "강릉";
    public static final String DEMO_TITLE = "우리의 강릉 여행 (데모)";
    public static final String DEMO_DATE = "2025-08-15";

    private final SharedPreferences prefs;

    public MemoryStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 저장된 모든 추억을 여행 날짜 최신순으로 반환 */
    public List<Memory> getAll() {
        List<Memory> result = new ArrayList<>();
        String raw = prefs.getString(KEY_MEMORIES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                result.add(Memory.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Log.e(TAG, "추억 데이터 파싱 실패", e);
        }
        sortRecentFirst(result);
        return result;
    }

    /** 새 추억 저장. 저장 성공 시 true */
    public boolean add(Memory memory) {
        List<Memory> all = getAll();
        all.add(memory);
        return saveAll(all);
    }

    /** ID로 삭제. 실제로 삭제된 경우 true */
    public boolean delete(String id) {
        List<Memory> all = getAll();
        boolean removed = false;
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).id.equals(id)) {
                all.remove(i);
                removed = true;
            }
        }
        return removed && saveAll(all);
    }

    /** 같은 ID의 기록을 교체 저장. 대상이 없으면 false */
    public boolean update(Memory updated) {
        List<Memory> all = getAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(updated.id)) {
                all.set(i, updated);
                return saveAll(all);
            }
        }
        return false;
    }

    /** ID로 1건 조회. 없으면 null */
    public Memory findById(String id) {
        if (id == null) return null;
        for (Memory memory : getAll()) {
            if (memory.id.equals(id)) return memory;
        }
        return null;
    }

    /** 사용자 입력으로 새 추억 생성 */
    public Memory create(String place, String title, String travelDate, String songId) {
        return new Memory(
                UUID.randomUUID().toString(),
                place.trim(),
                title.trim(),
                travelDate,
                songId,
                System.currentTimeMillis(),
                false
        );
    }

    /** 데모 기록이 이미 존재하는지 확인 (중복 생성 방지) */
    public boolean hasDemo() {
        for (Memory memory : getAll()) {
            if (memory.demo) return true;
        }
        return false;
    }

    /**
     * 강릉 데모 기록 1건을 생성한다.
     * 재생 가능한 곡이 없거나 이미 데모가 있으면 null을 반환한다.
     */
    public Memory createDemo() {
        if (hasDemo()) return null;
        List<SongCatalog.Song> songs = SongCatalog.all();
        if (songs.isEmpty()) return null;
        Memory demo = new Memory(
                "demo-gangneung",
                DEMO_PLACE,
                DEMO_TITLE,
                DEMO_DATE,
                songs.get(0).id,
                System.currentTimeMillis(),
                true
        );
        return add(demo) ? demo : null;
    }

    /** 목적지 이름/주소와 일치하는 추억을 최신순으로 반환 */
    public List<Memory> findMatching(String destinationName, String destinationAddress) {
        List<Memory> matched = new ArrayList<>();
        for (Memory memory : getAll()) {
            if (PlaceMatcher.matches(memory.place, destinationName, destinationAddress)) {
                matched.add(memory);
            }
        }
        return matched;
    }

    private boolean saveAll(List<Memory> memories) {
        JSONArray array = new JSONArray();
        try {
            for (Memory memory : memories) {
                array.put(memory.toJson());
            }
        } catch (JSONException e) {
            Log.e(TAG, "추억 데이터 직렬화 실패", e);
            return false;
        }
        return prefs.edit().putString(KEY_MEMORIES, array.toString()).commit();
    }

    private static void sortRecentFirst(List<Memory> list) {
        Collections.sort(list, new Comparator<Memory>() {
            @Override
            public int compare(Memory a, Memory b) {
                int byDate = b.travelDate.compareTo(a.travelDate);
                if (byDate != 0) return byDate;
                return Long.compare(b.createdAt, a.createdAt);
            }
        });
    }
}
