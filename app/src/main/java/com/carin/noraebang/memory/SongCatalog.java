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
        public final String title;

        Song(String id, int rawResId, String title) {
            this.id = id;
            this.rawResId = rawResId;
            this.title = title;
        }
    }

    private static final List<Song> SONGS;

    static {
        List<Song> list = new ArrayList<>();
        list.add(new Song("carin_song_01", R.raw.carin_song_01, "첫 번째 노래"));
        list.add(new Song("carin_song_02", R.raw.carin_song_02, "두 번째 노래"));
        list.add(new Song("carin_song_03", R.raw.carin_song_03, "세 번째 노래"));
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
