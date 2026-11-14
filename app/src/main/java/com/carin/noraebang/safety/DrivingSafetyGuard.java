package com.carin.noraebang.safety;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * AAOS 표준 CarUxRestrictionsManager로 현재 주행 제한 상태를 조회한다.
 *
 * android.car 클래스는 일반 Android SDK(android.jar)에 없고 차량 플랫폼의
 * BOOTCLASSPATH에만 존재하므로, 컴파일 의존성을 추가하지 않고 리플렉션으로 접근한다.
 * 상태를 확인할 수 없으면 UNKNOWN을 반환하며, 호출 측은 UNKNOWN을 "제한"으로 취급해야 한다.
 */
public final class DrivingSafetyGuard {

    private static final String TAG = "CARIN_SAFETY";
    private static final String CAR_CLASS = "android.car.Car";
    private static final String UX_RESTRICTION_SERVICE = "uxrestriction";

    public enum State {
        /** 주차 상태. 제한 기능(텍스트 입력, 기록 편집) 허용 */
        PARKED,
        /** 주행 중 또는 운전자 주의 분산 최적화 필요. 제한 기능 차단 */
        RESTRICTED,
        /** 차량 서비스에 접근 불가. 안전을 위해 제한으로 취급 */
        UNKNOWN
    }

    /** 조회 결과와 사용자 안내 문구 */
    public static final class Result {
        public final State state;
        public final String detail;

        Result(State state, String detail) {
            this.state = state;
            this.detail = detail;
        }

        /** 제한 기능을 실행해도 되는 상태인지 */
        public boolean isParkedSafe() {
            return state == State.PARKED;
        }
    }

    private final Context context;

    public DrivingSafetyGuard(Context context) {
        this.context = context.getApplicationContext();
    }

    /** 현재 UX 제한 상태를 1회 조회한다 (동기, 수십 ms 소요) */
    public Result check() {
        Object car = null;
        Class<?> carClass;
        try {
            carClass = Class.forName(CAR_CLASS);
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "android.car 미존재 (차량 플랫폼 아님)");
            return new Result(State.UNKNOWN, "차량 플랫폼이 아니어서 주행 상태를 확인할 수 없습니다.");
        }

        try {
            Method createCar = carClass.getMethod("createCar", Context.class);
            car = createCar.invoke(null, context);
            if (car == null) {
                return new Result(State.UNKNOWN, "차량 서비스에 연결할 수 없습니다.");
            }

            Method getCarManager = carClass.getMethod("getCarManager", String.class);
            Object manager = getCarManager.invoke(car, UX_RESTRICTION_SERVICE);
            if (manager == null) {
                return new Result(State.UNKNOWN, "UX 제한 서비스를 사용할 수 없습니다.");
            }

            Method getCurrent = manager.getClass().getMethod("getCurrentCarUxRestrictions");
            Object restrictions = getCurrent.invoke(manager);
            if (restrictions == null) {
                return new Result(State.UNKNOWN, "UX 제한 정보를 받지 못했습니다.");
            }

            Method requiresDo = restrictions.getClass().getMethod("isRequiresDistractionOptimization");
            boolean distractionOptimizationRequired = (Boolean) requiresDo.invoke(restrictions);

            Method getActive = restrictions.getClass().getMethod("getActiveRestrictions");
            int active = (Integer) getActive.invoke(restrictions);

            Log.d(TAG, "UxRestrictions requiresDO=" + distractionOptimizationRequired
                    + " active=0x" + Integer.toHexString(active));

            if (distractionOptimizationRequired || active != 0) {
                return new Result(State.RESTRICTED,
                        "주행 중에는 이 기능을 사용할 수 없습니다. 안전한 곳에 주차 후 이용해 주세요.");
            }
            return new Result(State.PARKED, "주차 상태 · 모든 기능 사용 가능");
        } catch (Exception e) {
            Log.w(TAG, "CarUxRestrictions 조회 실패", e);
            return new Result(State.UNKNOWN, "주행 상태를 확인할 수 없어 기능을 제한합니다.");
        } finally {
            if (car != null) {
                try {
                    carClass.getMethod("disconnect").invoke(car);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
