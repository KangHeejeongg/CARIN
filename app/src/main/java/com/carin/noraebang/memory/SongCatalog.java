package com.carin.noraebang.memory;

import com.carin.noraebang.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 앱에 번들된 재생 가능한 음원 목록.
 * 추억 기록은 raw 리소스 번호 대신 이 고정 ID를 저장한다.
 * 리소스 번호는 빌드마다 달라질 수 있지만 ID는 변하지 않기 때문이다.
 */
public final class SongCatalog {

    /** 재생 가능한 곡 1개의 정보 */
    public static final class Song {
        public final String id;
        public final int rawResId;
        /** 곡 제목 (팀 자체 제작 음원의 가제. 정식 제목이 정해지면 여기만 바꾼다) */
        public final String title;
        /** 목록에 함께 보여 줄 한 줄 소개 */
        public final String mood;
        /** 커버 이미지 (assets 기준 경로) */
        public final String coverAsset;

        Song(String id, int rawResId, String title, String mood) {
            this.id = id;
            this.rawResId = rawResId;
            this.title = title;
            this.mood = mood;
            this.coverAsset = "covers/" + id + ".jpg";
        }
    }

    private static final List<Song> SONGS;

    static {
        List<Song> list = new ArrayList<>();
        // 제목은 각 곡의 데모 가사(DemoLyrics)에 맞춘 가제
        list.add(new Song("carin_song_01", R.raw.carin_song_01, "바다 빛 드라이브",
                "창문 너머 바다를 따라 달리는 여름 노래"));
        list.add(new Song("carin_song_02", R.raw.carin_song_02, "초록 신호의 밤",
                "지도 없이 떠나는 밤 드라이브"));
        list.add(new Song("carin_song_03", R.raw.carin_song_03, "터널 끝의 하늘",
                "비 갠 길 위에서 다시 만나는 그날"));
        SONGS = Collections.unmodifiableList(list);
    }

    private SongCatalog() {
    }

    /** 플레이리스트 순서대로 전체 곡 반환 */
    public static List<Song> all() {
        return SONGS;
    }

    /** ID로 곡 조회. 없으면 null */
    public static Song findById(String id) {
        if (id == null) return null;
        for (Song song : SONGS) {
            if (song.id.equals(id)) return song;
        }
        return null;
    }

    /** 곡 ID에 해당하는 제목. 없으면 안내 문구 */
    public static String titleOf(String id) {
        Song song = findById(id);
        return song != null ? song.title : "(찾을 수 없는 곡)";
    }
}
