package com.common.zxing.light;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
/**
 * Created by Admin on 2019/6/27.
 */

public class LightSensorManager {

    private static final boolean DEBUG = true;
    private static final String TAG = "LightSensor";

    private static LightSensorManager instance;
    private SensorManager mSensorManager;
    private LightSensorListener mLightSensorListener;
    private boolean mHasStarted = false;

    private LightSensorManager() {
    }

    public static LightSensorManager getInstance() {
        if (instance == null) {
            instance = new LightSensorManager();
        }
        return instance;
    }
    //启动，在获取光照强度前调用。
    public void start(Context context) {
        if (mHasStarted) {
            return;
        }
        mHasStarted = true;
        mSensorManager = (SensorManager) context.getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        Sensor lightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT); // 获取光线传感器
        if (lightSensor != null) { // 光线传感器存在时
            mLightSensorListener = new LightSensorListener();
            mSensorManager.registerListener(mLightSensorListener, lightSensor,
                    SensorManager.SENSOR_DELAY_NORMAL); // 注册事件监听
        }
    }
    //停止，在不再需要获取光照强度后调用。
    public void stop() {
        if (!mHasStarted || mSensorManager == null) {
            return;
        }
        mHasStarted = false;
        mSensorManager.unregisterListener(mLightSensorListener);
    }

    /**
     * 获取光线强度,单位为勒克斯(lux)。
     */
    public float getLux() {
        if (mLightSensorListener != null) {
            return mLightSensorListener.lux;
        }
        return -1.0f; // 默认返回-1，表示设备无光线传感器或者未调用start()方法
    }

    private class LightSensorListener implements SensorEventListener {

        private float lux; // 光线强度
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
                // 获取光线强度
                lux = event.values[0];
                if (DEBUG) {
                    Log.d(TAG, "lux : " + lux);
                }
            }
        }

    }

}
