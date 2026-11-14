package com.carin.noraebang.memory;

import org.json.JSONException;
import org.json.JSONObject;

/** 장소와 노래를 연결한 여행 추억 1건 */
public final class Memory {

    public final String id;
    public final String place;
    public final String title;
    /** yyyy-MM-dd */
    public final String travelDate;
    public final String songId;
    public final long createdAt;
    public final boolean demo;
    /** 사용자가 연결한 사진 URI 문자열. 없으면 null (이전 버전 데이터와 호환) */
    public final String photoUri;

    public Memory(String id, String place, String title, String travelDate,
                  String songId, long createdAt, boolean demo) {
        this(id, place, title, travelDate, songId, createdAt, demo, null);
    }

    public Memory(String id, String place, String title, String travelDate,
                  String songId, long createdAt, boolean demo, String photoUri) {
        this.id = id;
        this.place = place;
        this.title = title;
        this.travelDate = travelDate;
        this.songId = songId;
        this.createdAt = createdAt;
        this.demo = demo;
        this.photoUri = (photoUri == null || photoUri.isEmpty()) ? null : photoUri;
    }

    /** 사진만 바꾼 복사본 (null이면 사진 해제) */
    public Memory withPhoto(String newPhotoUri) {
        return new Memory(id, place, title, travelDate, songId, createdAt, demo, newPhotoUri);
    }

    /** JSON 직렬화 */
    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("place", place);
        json.put("title", title);
        json.put("travelDate", travelDate);
        json.put("songId", songId);
        json.put("createdAt", createdAt);
        json.put("demo", demo);
        if (photoUri != null) json.put("photoUri", photoUri);
        return json;
    }

    /** JSON 역직렬화. photoUri가 없는 이전 데이터도 그대로 읽힌다 */
    static Memory fromJson(JSONObject json) throws JSONException {
        return new Memory(
                json.getString("id"),
                json.getString("place"),
                json.getString("title"),
                json.getString("travelDate"),
                json.getString("songId"),
                json.getLong("createdAt"),
                json.optBoolean("demo", false),
                json.optString("photoUri", null)
        );
    }
}
