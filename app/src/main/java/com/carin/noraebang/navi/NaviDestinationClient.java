package com.carin.noraebang.navi;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import ai.pleos.playground.navi.constants.NaviErrorCode;
import ai.pleos.playground.navi.data.DestinationInfo;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;

import kotlin.Unit;

/**
 * NaviHelper SDK(3.0.5)에서 목적지 정보를 조회하는 래퍼.
 * SDK 콜백은 백그라운드 스레드로 도착하므로 모든 결과를 메인 스레드로 전달한다.
 *
 * 필요 권한: pleos.car.permission.NAVI_ROUTE (protection level: normal)
 */
public final class NaviDestinationClient {

    private static final String TAG = "CARIN_NAVI";

    /** 메인 스레드에서 호출되는 결과 콜백 */
    public interface Callback {
        void onConnected();

        void onConnectionFailed(Throwable error);

        /** 목적지 조회 결과. 목적지가 없으면 name/address가 빈 문자열이다. */
        void onDestination(DestinationInfo info);

        /** 내비 앱에서 목적지가 바뀌었음을 알림 (재조회 필요) */
        void onDestinationChanged();

        void onNaviError(NaviErrorCode code);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private NaviHelper helper;
    private Callback callback;
    private boolean connected = false;
    private boolean listenerRegistered = false;
    private boolean released = false;

    private final NaviHelperEventListener listener = new NaviHelperEventListener() {
        @Override
        public void onDestinationInfo(DestinationInfo destinationInfo) {
            Log.d(TAG, "onDestinationInfo: " + destinationInfo);
            post(() -> {
                if (callback != null) callback.onDestination(destinationInfo);
            });
        }

        @Override
        public void onDestinationChanged() {
            Log.d(TAG, "onDestinationChanged");
            post(() -> {
                if (callback != null) callback.onDestinationChanged();
            });
        }

        @Override
        public void onError(NaviErrorCode errorCode) {
            Log.w(TAG, "onError: " + errorCode);
            post(() -> {
                if (callback != null) callback.onNaviError(errorCode);
            });
        }
    };

    /** Navi Service에 연결한다. 연결 결과는 콜백으로 전달된다. */
    public void connect(Context context, Callback callback) {
        this.callback = callback;
        released = false;
        helper = new NaviHelper(context.getApplicationContext());
        helper.initialize(
                () -> {
                    post(() -> {
                        if (released || helper == null) return;
                        connected = true;
                        helper.addListener(listener);
                        listenerRegistered = true;
                        Log.d(TAG, "Navi Service 연결 성공");
                        if (this.callback != null) this.callback.onConnected();
                    });
                    return Unit.INSTANCE;
                },
                throwable -> {
                    Log.e(TAG, "Navi Service 연결 실패", throwable);
                    post(() -> {
                        if (released) return;
                        connected = false;
                        if (this.callback != null) this.callback.onConnectionFailed(throwable);
                    });
                    return Unit.INSTANCE;
                }
        );
    }

    public boolean isConnected() {
        return connected && helper != null;
    }

    /**
     * 현재 목적지 조회를 요청한다. 결과는 onDestination으로 비동기 전달된다.
     *
     * @return 요청을 보냈으면 true, 아직 연결되지 않았으면 false
     */
    public boolean requestDestination() {
        if (!isConnected()) return false;
        try {
            helper.getDestinationInfo();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "getDestinationInfo 호출 실패", e);
            return false;
        }
    }

    /** SDK 연결 해제. Activity 종료 시 반드시 호출 */
    public void release() {
        released = true;
        callback = null;
        if (helper != null) {
            try {
                if (listenerRegistered) helper.removeListener(listener);
                helper.release();
            } catch (Exception e) {
                Log.e(TAG, "NaviHelper.release 실패", e);
            }
        }
        helper = null;
        connected = false;
        listenerRegistered = false;
    }

    private void post(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            mainHandler.post(action);
        }
    }
}
