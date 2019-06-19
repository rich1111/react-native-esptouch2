
package com.rickl.rn.esptouch;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.espressif.iot.esptouch.EsptouchTask;
import com.espressif.iot.esptouch.IEsptouchResult;
import com.espressif.iot.esptouch.IEsptouchTask;
import com.espressif.iot.esptouch.util.ByteUtil;
import com.espressif.iot.esptouch.util.EspNetUtil;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;

import com.espressif.iot.esptouch.IEsptouchListener;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * code 200:    ESPTouch success
 * code 0      ESPTouch failed
 * code -1:     Esptouch is not ready yet
 * code -2:     Device don not support 5G Wifi, please make sure the currently connected Wifi is 2.4G
 * code -3:     no Wifi connection 
 * code -4      Android 9 need GPS permission 
 * code -5      Create Esptouch task failed, the EspTouch port could be used by other thread
 */


public class RNEsptouchModule extends ReactContextBaseJavaModule implements LifecycleEventListener, ActivityCompat.OnRequestPermissionsResultCallback {
    private static final int REQUEST_PERMISSION = 0x01;
    private final ReactApplicationContext reactContext;
    private Activity thisActivity = getCurrentActivity();
    private Promise mConfigPromise;
    private String mSSID = "";
    private String mBSSID = "";
    private boolean is5GWifi = false;
    private boolean isWifiConnected = false;
    private boolean needGPSPermission = false;
    private boolean mDestroyed = false;
    private boolean mReceiverRegistered = false; // 记录是否有注册广播接收
    private EsptouchAsyncTask4 mTask; // 配置任务
    private BroadcastReceiver mReceiver = new BroadcastReceiver() { // 监听网络状态及GPS开关变化广播
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            assert wifiManager != null;

            switch (action) {
                case WifiManager.NETWORK_STATE_CHANGED_ACTION:
                    WifiInfo wifiInfo;
                    if (intent.hasExtra(WifiManager.EXTRA_WIFI_INFO)) {
                        wifiInfo = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
                    } else {
                        wifiInfo = wifiManager.getConnectionInfo();
                    }
                    onWifiChanged(wifiInfo);
                    break;
                case LocationManager.PROVIDERS_CHANGED_ACTION:
                    onWifiChanged(wifiManager.getConnectionInfo());
                    break;
            }
        }
    };

    private IEsptouchListener myListener = new IEsptouchListener() {
        @Override
        public void onEsptouchResultAdded(final IEsptouchResult result) {
            onEsptoucResultAddedPerform(result);
        }
    };

    public RNEsptouchModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        reactContext.addLifecycleEventListener(this);
    }

    @Override
    public void onHostResume() {
        // Activity `onResume`
    }

    @Override
    public void onHostPause() {
        // Activity `onPause`
    }

    @Override
    public void onHostDestroy() {
        // Activity `onDestroy`
        mDestroyed = true;
        if (mReceiverRegistered) {
            reactContext.unregisterReceiver(mReceiver);
        }
    }

    @Override
    public String getName() {
        return "RNEsptouch";
    }


    @ReactMethod
    public void initESPTouch() {
        // issue#29上说Android 9需要授予位置权限后把GPS打开才能获取Wi-Fi信息
        // Skip this since it may cause exception on some Android 9
        // We use React Native android location service package instead
        if (false/*isSDKAtLeastP()*/) {
            // 如果未授权位置权限
            if (ContextCompat.checkSelfPermission(thisActivity, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                // 判断是否需要授权说明
                if (ActivityCompat.shouldShowRequestPermissionRationale(thisActivity,
                        Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    Toast.makeText(thisActivity, "ESPTouch配置需要此权限", Toast.LENGTH_LONG);
                }
                // 发起授权请求
                String[] permissions = {Manifest.permission.ACCESS_COARSE_LOCATION};
                ActivityCompat.requestPermissions(thisActivity, permissions, REQUEST_PERMISSION);
            } else {
                registerBroadcastReceiver();
            }

        } else {
            registerBroadcastReceiver();
        }
    }

    /* 请求授权后回调(上面的requestPermissions方法调用后，经用户操作反馈后会触发此函数) */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (!mDestroyed) {
                        registerBroadcastReceiver();
                    }
                }
                break;
        }
    }

    /* 开始配置ESP8266 */
    @ReactMethod
    public void startSmartConfig(String pwd, int broadcastType, Promise promise) {
        mConfigPromise = promise;
        if (!mReceiverRegistered) {
            respondToRN(-1, "Esptouch is not ready yet");
            return;
        }
        if (is5GWifi) {
            respondToRN(-2, "Device don not support 5G Wifi, please make sure the currently connected Wifi is 2.4G");
            return;
        }
        if (!isWifiConnected && !needGPSPermission) {
            respondToRN(-3, "no Wifi connection");
            return;
        }
        if (!isWifiConnected && needGPSPermission) {
            respondToRN(-4,"Android 9 need GPS permission");
            return;
        }
        byte[] ssid = ByteUtil.getBytesByString(mSSID);
        byte[] password = ByteUtil.getBytesByString(pwd);
        byte[] bssid = EspNetUtil.parseBssid2bytes(mBSSID);
        byte[] deviceCount = ByteUtil.getBytesByString("1");
        byte[] broadcast = {(byte) broadcastType}; // 1 广播， 0 组播

        if (mTask != null) {
            mTask.cancelEsptouch();
        }
        mTask = new EsptouchAsyncTask4(thisActivity);
        mTask.execute(ssid, bssid, password, deviceCount, broadcast);
    }

    @ReactMethod
    public void getNetInfo(Promise promise) {
        WritableMap map = Arguments.createMap();
        map.putString("ssid", mSSID);
        map.putString("bssid", mBSSID);
        promise.resolve(map);
    }

    @ReactMethod
    public void finish() {
        mConfigPromise = null;
        if (mTask != null) {
            mTask.cancelEsptouch();
        }
        if (mReceiverRegistered) {
            reactContext.unregisterReceiver(mReceiver);
            Log.i("RNEsptouchModule","config finished and unregisterReceiver");
        }
        mReceiverRegistered = false;
    }

    /* 判断SDK版本 */
    private boolean isSDKAtLeastP() {
        return Build.VERSION.SDK_INT >= 28;
    }

    /* 注册广播接收 */
    private void registerBroadcastReceiver() {
        if (mReceiverRegistered) return;
        mReceiverRegistered = true;
        Log.i("RNEsptouchModule","register receiver");
        IntentFilter filter = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        if (isSDKAtLeastP()) {
            filter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
        }
        reactContext.registerReceiver(mReceiver, filter);
    }

    /* 处理wifi变化 */
    private void onWifiChanged(WifiInfo info) {
        boolean disconnected = info == null
                || info.getNetworkId() == -1
                || "<unknown ssid>".equals(info.getSSID());
        if (disconnected) { // 未连接WIFI
            Log.i("RNEsptouchModule","No Wifi connection");
            mSSID = "";
            mBSSID = "";
            isWifiConnected = false;
            if (isSDKAtLeastP()) {
                //checkLocation();
            }

            if (mTask != null) { // 配置时wifi中断
                mTask.cancelEsptouch();
                mTask = null;
                respondToRN(-3, "no Wifi connection");
            }
        } else { // 已连接WIFI
            isWifiConnected = true;
            mSSID = info.getSSID();
            if (mSSID.startsWith("\"") && mSSID.endsWith("\"")) {
                mSSID = mSSID.substring(1, mSSID.length() - 1);
            }
            mBSSID = info.getBSSID();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                int frequency = info.getFrequency();
                if (frequency > 4900 && frequency < 5900) {
                    // Connected 5G wifi. Device does not support 5G
                    Log.i("RNEsptouchModule","Connected 5G wifi. Device does not support 5G");
                    is5GWifi = true;
                } else {
                    is5GWifi = false;
                }
            }
        }
    }

    /* 检查是否开启了位置权限 */
    private void checkLocation() {
        Log.i("RNEsptouchModule","check location permission");
        boolean enable;
        LocationManager locationManager = (LocationManager) thisActivity.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            enable = false;
        } else {
            boolean locationGPS = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean locationNetwork = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            enable = locationGPS || locationNetwork;
        }

        if (!enable) {
            needGPSPermission = true;
            Log.i("RNEsptouchModule","Android 9 need gps permission");
        } else {
            needGPSPermission = false;
        }
    }

    /* 当同时为多个设备配网时，每成功配网一个设备，就会调用一次此方法 */
    private void onEsptoucResultAddedPerform(final IEsptouchResult result) {
        Log.i("RNEsptouchModule", "bssid=" + result.getBssid() + " ip=" + result.getInetAddress().toString() + " is connected to the wifi");
    }

    private void respondToRN(int code, String msg) {
        if (mConfigPromise != null) {
            WritableMap map = Arguments.createMap();
            map.putInt("code", code);
            map.putString("msg", msg);
            mConfigPromise.resolve(map);
        }
    }

    private void respondToRN(int code, String msg, String bssid, String ip) {
        if (mConfigPromise != null) {
            WritableMap map = Arguments.createMap();
            map.putInt("code", code);
            map.putString("msg", msg);
            map.putString("bssid", bssid);
            map.putString("ip", ip);
            mConfigPromise.resolve(map);
        }
    }

    /* 配置任务类 */
    private class EsptouchAsyncTask4 extends AsyncTask<byte[], Void, List<IEsptouchResult>> {
        private WeakReference<Activity> mActivity;

        // without the lock, if the user tap confirm and cancel quickly enough,
        // the bug will arise. the reason is follows:
        // 0. task is starting created, but not finished
        // 1. the task is cancel for the task hasn't been created, it do nothing
        // 2. task is created
        // 3. Oops, the task should be cancelled, but it is running
        private final Object mLock = new Object();
        private IEsptouchTask mEsptouchTask;

        EsptouchAsyncTask4(Activity activity) {
            mActivity = new WeakReference<>(activity);
        }

        void cancelEsptouch() {
            cancel(true);
            if (mEsptouchTask != null) {
                mEsptouchTask.interrupt();
            }
        }

        @Override
        protected void onPreExecute() {
            //
        }

        @Override
        protected List<IEsptouchResult> doInBackground(byte[]... params) {
            int taskResultCount;
            synchronized (mLock) {
                byte[] apSsid = params[0];
                byte[] apBssid = params[1];
                byte[] apPassword = params[2];
                byte[] deviceCountData = params[3];
                byte[] broadcastData = params[4];
                taskResultCount = deviceCountData.length == 0 ? -1 : Integer.parseInt(new String(deviceCountData));
                mEsptouchTask = new EsptouchTask(apSsid, apBssid, apPassword, reactContext);
                mEsptouchTask.setPackageBroadcast(broadcastData[0] == 1);
                mEsptouchTask.setEsptouchListener(myListener);
            }
            return mEsptouchTask.executeForResults(taskResultCount);
        }

        @Override
        protected void onPostExecute(List<IEsptouchResult> result) {
            if (result == null) {
                Log.i("RNEsptouchModule","Create Esptouch task failed, the EspTouch port could be used by other thread");
                respondToRN(-5,"Create Esptouch task failed, the EspTouch port could be used by other thread");
                return;
            }

            IEsptouchResult firstResult = result.get(0);
            // check whether the task is cancelled and no results received
            if (!firstResult.isCancelled()) {
                // the task received some results including cancelled while
                // executing before receiving enough results
                if (firstResult.isSuc()) {
                    // 配置成功
                    Log.i("RNEsptouchModule","EspTouch success");
                    String ip = firstResult.getInetAddress().toString();
                    if (ip.startsWith("/")) ip = ip.substring(1);
                    respondToRN(200, "EspTouch succcess", firstResult.getBssid(), ip);
                } else {
                    // 配置失败
                    Log.i("RNEsptouchModule","EspTouch fail");
                    respondToRN(0, "EspTouch failed");
                }
            }

            mTask = null;
        }
    }
}