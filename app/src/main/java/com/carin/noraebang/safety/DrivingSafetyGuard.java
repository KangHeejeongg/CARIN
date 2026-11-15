package com.carin.noraebang.safety;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

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
    private static final String UX_LISTENER_CLASS =
            "android.car.drivingstate.CarUxRestrictionsManager$OnUxRestrictionsChangedListener";

    /** 주행 상태가 바뀔 때 메인 스레드에서 호출된다 */
    public interface Listener {
        /**
         * @param result 바뀐 뒤의 상태
         */
        void onStateChanged(Result result);
    }

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
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 감시 중에만 유지하는 Car 연결과 매니저 (리플렉션 객체) */
    private Object monitorCar;
    private Object monitorManager;
    private Listener monitorListener;

    /**
     * @param context 아무 Context (내부에서 Application Context만 보관)
     */
    public DrivingSafetyGuard(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 현재 UX 제한 상태를 1회 조회한다 (동기, 수십 ms 소요).
     *
     * @return 조회 결과. 확인할 수 없으면 UNKNOWN
     */
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

            return evaluate(restrictions);
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

    /**
     * CarUxRestrictions 객체를 상태로 바꾼다.
     *
     * @param restrictions android.car.drivingstate.CarUxRestrictions 인스턴스
     * @return 판정 결과
     */
    private static Result evaluate(Object restrictions) throws Exception {
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
    }

    /**
     * 주행 상태 변경 감시를 시작한다. 이미 감시 중이면 리스너만 바꾼다.
     * 차량 플랫폼이 아니거나 등록에 실패하면 false를 반환하며, 이 경우 호출 측은
     * 기존처럼 기능 실행 직전의 {@link #check()}와 화면 복귀 시 재확인에 의존한다.
     *
     * @param listener 상태가 바뀔 때 메인 스레드에서 호출할 리스너
     * @return 등록 성공 여부
     */
    public boolean startMonitoring(Listener listener) {
        monitorListener = listener;
        if (monitorManager != null) return true;
        try {
            Class<?> carClass = Class.forName(CAR_CLASS);
            Object car = carClass.getMethod("createCar", Context.class).invoke(null, context);
            if (car == null) return false;
            Object manager = carClass.getMethod("getCarManager", String.class)
                    .invoke(car, UX_RESTRICTION_SERVICE);
            if (manager == null) {
                disconnectQuietly(car);
                return false;
            }

            Class<?> listenerClass = Class.forName(UX_LISTENER_CLASS);
            Object proxy = Proxy.newProxyInstance(listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass}, uxListenerHandler());
            manager.getClass().getMethod("registerListener", listenerClass).invoke(manager, proxy);

            monitorCar = car;
            monitorManager = manager;
            Log.d(TAG, "UX 제한 변경 감시 시작");
            return true;
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "android.car 미존재: 변경 감시 불가");
            return false;
        } catch (Exception e) {
            Log.w(TAG, "UX 제한 리스너 등록 실패", e);
            stopMonitoring();
            return false;
        }
    }

    /** 감시를 멈추고 Car 연결을 끊는다. 감시 중이 아니면 아무것도 하지 않는다 */
    public void stopMonitoring() {
        monitorListener = null;
        if (monitorManager != null) {
            try {
                monitorManager.getClass().getMethod("unregisterListener").invoke(monitorManager);
            } catch (Exception e) {
                Log.w(TAG, "UX 제한 리스너 해제 실패", e);
            }
            monitorManager = null;
        }
        if (monitorCar != null) {
            disconnectQuietly(monitorCar);
            monitorCar = null;
            Log.d(TAG, "UX 제한 변경 감시 종료");
        }
    }

    /**
     * debug 빌드 검증용: 실제 차량 신호 없이 상태 변경을 감시 리스너에 전달한다.
     * user build 에뮬레이터는 UX 제한을 바꿀 수 없어 닫기 경로를 이 방법으로만 재현할 수 있다.
     * 호출 측이 debug 빌드인지 확인해야 한다.
     *
     * @param driving true면 주행 중(RESTRICTED), false면 주차(PARKED)
     */
    public void simulateForDebug(boolean driving) {
        Result result = driving
                ? new Result(State.RESTRICTED, "[DEBUG] 주행 중으로 가정합니다. 주차 후 이용해 주세요.")
                : new Result(State.PARKED, "[DEBUG] 주차 상태로 가정합니다.");
        Log.w(TAG, "debug 주행 상태 주입: " + result.state);
        mainHandler.post(() -> {
            Listener target = monitorListener;
            if (target != null) target.onStateChanged(result);
        });
    }

    /** OnUxRestrictionsChangedListener 프록시의 호출 처리기 */
    private InvocationHandler uxListenerHandler() {
        return (proxy, method, args) -> {
            switch (method.getName()) {
                case "onUxRestrictionsChanged":
                    Result result;
                    try {
                        result = evaluate(args[0]);
                    } catch (Exception e) {
                        Log.w(TAG, "변경된 UX 제한 해석 실패", e);
                        result = new Result(State.UNKNOWN, "주행 상태를 확인할 수 없어 기능을 제한합니다.");
                    }
                    final Result changed = result;
                    // 차량 서비스 콜백은 바인더 스레드에서 올 수 있으므로 메인 스레드로 옮긴다
                    mainHandler.post(() -> {
                        Listener target = monitorListener;
                        if (target != null) target.onStateChanged(changed);
                    });
                    return null;
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "CarinUxListener";
                default:
                    return null;
            }
        };
    }

    private static void disconnectQuietly(Object car) {
        try {
            car.getClass().getMethod("disconnect").invoke(car);
        } catch (Exception ignored) {
        }
    }
}
