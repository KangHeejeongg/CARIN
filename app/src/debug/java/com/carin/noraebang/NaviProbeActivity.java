package com.carin.noraebang;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ai.pleos.playground.navi.constants.NaviErrorCode;
import ai.pleos.playground.navi.data.CurrentLocationInfo;
import ai.pleos.playground.navi.data.DestinationInfo;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;

import kotlin.Unit;
import java.lang.reflect.Method;

/**
 * CAR IN: NaviHelper SDK investigation screen (DEBUG BUILD ONLY).
 * This is a one-off diagnostic, not production architecturclaudee.
 * Do NOT expose or upload raw location logs.
 */
public final class NaviProbeActivity extends Activity {
    private static final String TAG = "CARIN_NAVI_TEST";
    private TextView output;
    private NaviHelper helper;
    private boolean connected = false;
    private boolean listenerRegistered = false;

    private final NaviHelperEventListener listener = new NaviHelperEventListener() {
        @Override
        public void onCurrentLocationInfo(CurrentLocationInfo info) {
            report("[현재 위치 수신] " + String.valueOf(info));
        }

        @Override
        public void onDestinationInfo(DestinationInfo info) {
            report("[목적지 수신] " + String.valueOf(info));
            if (info != null) {
                // SDK 3.0.5 documents remainTime and remainDistance.
                // The getter inspection is confined to this diagnostic utility.
                report("  remainTime = " + readGetter(info, "getRemainTime"));
                report("  remainDistance = " + readGetter(info, "getRemainDistance"));
            }
        }

        @Override
        public void onError(NaviErrorCode error) {
            report("[SDK ERROR] " + String.valueOf(error));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(24, 32, 24, 24);
        column.setBackgroundColor(0xFF141B26);

        TextView heading = new TextView(this);
        heading.setText("CAR IN · NaviHelper 실제 데이터 검사");
        heading.setTextColor(0xFF75E5BE);
        heading.setTextSize(20);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(heading);

        Button fetch = new Button(this);
        fetch.setText("현재 위치 + 목적지 다시 조회");
        column.addView(fetch);

        ScrollView scroll = new ScrollView(this);
        output = new TextView(this);
        output.setTextColor(0xFFFFFFFF);
        output.setTextSize(14);
        output.setTextIsSelectable(true);
        scroll.addView(output);
        column.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(column);

        fetch.setOnClickListener(v -> requestValues());
        report("진단 시작. NaviHelper SDK 연결 중...");
        helper = new NaviHelper(getApplicationContext());
        helper.initialize(
                () -> {
                    if (isFinishing() || isDestroyed()) return Unit.INSTANCE;
                    connected = true;
                    report("[연결 성공] Navi Service");
                    helper.addListener(listener);
                    listenerRegistered = true;
                    requestValues();
                    return Unit.INSTANCE;
                },
                throwable -> {
                    report("[연결 실패] " + String.valueOf(throwable));
                    return Unit.INSTANCE;
                }
        );
    }

    private void requestValues() {
        if (!connected || helper == null) {
            report("[조회 보류] 아직 Navi Service에 연결되지 않음");
            return;
        }
        report("[조회 요청] 위치 / 목적지");
        try {
            helper.getCurrentLocationInfo();
            helper.getDestinationInfo();
        } catch (Exception e) {
            report("[호출 실패] " + Log.getStackTraceString(e));
        }
    }

    private String readGetter(Object source, String methodName) {
        try {
            Method getter = source.getClass().getMethod(methodName);
            return String.valueOf(getter.invoke(source));
        } catch (Exception e) {
            return "게터 확인 필요 (" + e.getClass().getSimpleName() + ")";
        }
    }

    private void report(String message) {
        Log.d(TAG, message);
        runOnUiThread(() -> {
            if (output != null) output.append(message + "\n\n");
        });
    }

    @Override
    protected void onDestroy() {
        if (helper != null) {
            if (listenerRegistered) helper.removeListener(listener);
            helper.release();
        }
        super.onDestroy();
    }
}
