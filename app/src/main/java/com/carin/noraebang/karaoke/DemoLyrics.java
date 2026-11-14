package com.carin.noraebang.karaoke;

import java.util.HashMap;
import java.util.Map;

/**
 * 카인 노래방 최소 체험용 정적 데모 가사.
 * 팀이 직접 작성한 문구이며 실제 음원의 가사가 아니다.
 * 음악 재생 시간과 동기화되지 않는다 (화면에 "정적 데모"로 표기).
 */
public final class DemoLyrics {

    public static final String NOTICE = "정적 데모 가사 · 팀 자체 제작 · 음악과 동기화되지 않음";

    private static final Map<String, String> LYRICS = new HashMap<>();

    static {
        LYRICS.put("carin_song_01",
                "창문 너머 스치는 바다 빛\n"
                        + "라디오는 우리 노래를 틀고\n"
                        + "핸들 위에 포갠 손끝에\n"
                        + "여름이 천천히 내려앉아\n\n"
                        + "이 길 끝에 네가 웃고 있다면\n"
                        + "조금 돌아가도 괜찮아\n"
                        + "차 안에서 시작된 노래가\n"
                        + "오래도록 우리 곁에 남을 테니까");

        LYRICS.put("carin_song_02",
                "신호등이 초록으로 바뀌면\n"
                        + "우린 또 어디로든 갈 수 있어\n"
                        + "지도 없이도 괜찮은 밤\n"
                        + "별이 길을 그려 주니까\n\n"
                        + "같은 노래 같은 길 위에서\n"
                        + "그날의 우리를 다시 만나\n"
                        + "돌아오는 길엔 조용히\n"
                        + "이 멜로디를 흥얼거리자");

        LYRICS.put("carin_song_03",
                "터널을 지나면 환해지는 하늘\n"
                        + "비 갠 아스팔트 위 반짝이는 빛\n"
                        + "너는 창밖을 보며 웃고\n"
                        + "나는 그 옆모습을 기억해\n\n"
                        + "언젠가 이 길을 다시 달릴 때\n"
                        + "이 노래가 먼저 말을 걸겠지\n"
                        + "그날 우리 여기 있었다고\n"
                        + "함께였다고");
    }

    private DemoLyrics() {
    }

    /** 곡 ID에 해당하는 데모 가사. 없으면 안내 문구 */
    public static String forSong(String songId) {
        String lyrics = songId == null ? null : LYRICS.get(songId);
        return lyrics != null ? lyrics : "이 곡의 데모 가사가 아직 준비되지 않았어요.";
    }

    /** 빈 줄을 뺀 가사 줄 목록 (노래방 화면에서 한 줄씩 강조 표시용) */
    public static String[] linesFor(String songId) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String line : forSong(songId).split("\n")) {
            if (!line.trim().isEmpty()) lines.add(line.trim());
        }
        return lines.toArray(new String[0]);
    }
}
