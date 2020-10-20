package com.example.intelligentalarm.service;

import android.annotation.TargetApi;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;


import com.example.intelligentalarm.model.BatteryInfoParser;
import com.example.intelligentalarm.model.CommandPool;
import com.example.intelligentalarm.model.Profile;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class LeService extends Service {

    private String TAG = "LeService";
    private String mTargetDeviceName = "Mi Smart";

    //自定义binder，用于service绑定activity之后为activity提供操作service的接口
    private LocalBinder mBinder = new LocalBinder();
    private Handler mHandler;
    private Intent intent;
    private int SCAN_PERIOD = 100000;//设置扫描时限
    private boolean mScanning = false;
    private int mColorIndex = 0;
    private CommandPool mCommandPool;

    //bluetooth
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private ScanCallback mScanCallback;
    private LeGattCallback mLeGattCallback;
    private BluetoothGatt mGatt;
    private BluetoothDevice mTarget;

    //Characteristic
    BluetoothGattCharacteristic alertChar;
    BluetoothGattCharacteristic stepChar;
    BluetoothGattCharacteristic batteryChar;
    BluetoothGattCharacteristic controlPointChar;
    BluetoothGattCharacteristic vibrationChar;

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "service onBind()");
        return mBinder;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "service onCreate()");

        mScanCallback = new LeScanCallback();
        mLeGattCallback = new LeGattCallback();

        mHandler = new Handler();

    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "service onDestroy()");
    }

    /**
     * 继承Binder类，实现localbinder,为activity提供操作接口
     */
    public class LocalBinder extends Binder {
        public boolean initBluetooth() {
            Log.d(TAG, "initBluetooth");

            //init bluetoothadapter.api 21 above
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = mBluetoothManager.getAdapter();
            if (mBluetoothAdapter == null) {
                return false;
            } else if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
                boolean bluetoothState = mBluetoothAdapter.enable();
                return bluetoothState;
            }
            return true;
        }

        public boolean initLeScanner() {
            Log.d(TAG, "initLeScanner");

            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
            if (mBluetoothLeScanner != null) {
                return true;
            }
            return false;
        }

        public void startLeScan() {
            Log.d(TAG, "startLeScan");

            mBluetoothLeScanner.startScan(mScanCallback);
            mScanning = true;
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mScanning == true) {
                        Log.d(TAG, "Stop Scan Time Out");
                        mScanning = false;
                        mBluetoothLeScanner.stopScan(mScanCallback);
                        notifyUI("state", "3");
                    }
                }
            }, SCAN_PERIOD);
        }

        public void startAlert() {
            Log.d(TAG, "startLeScan extent: ");

            if (mGatt != null) {
                byte[] value = {(byte) 0x02};
                mCommandPool.addCommand(CommandPool.Type.write, value, alertChar);
            }
        }

        public void startPair() {
            Log.d(TAG, "startLeScan extent: ");

            if (mGatt != null) {
                byte[] value = {(byte) 0x02};
//                mCommandPool.addCommand(CommandPool.Type.write, value, Profile.UUID_CHAR_PAIR);
            }
        }

        public void vibrateWithoutLed() {
            Log.d(TAG, "vibrateWithoutLed : ");

            mCommandPool.addCommand(CommandPool.Type.write, Profile.VIBRATION_WITHOUT_LED, vibrationChar);
        }

        public void vibrateWithLed() {
            Log.d(TAG, "vibrateWithLed : ");

            mCommandPool.addCommand(CommandPool.Type.write, Profile.VIBRATION_WITH_LED, vibrationChar);
        }

        public int bondTarget() {
            if (mTarget == null) {
                return -1;
            } else {
                boolean result = mBluetoothAdapter.getBondedDevices().contains(mTarget);
                if (result) {
                    return 1;// 已经绑定
                }
                Method createBondMethod = null;
                try {
                    createBondMethod = mTarget.getClass().getMethod("createBond");
                    Boolean returnValue = (Boolean) createBondMethod.invoke(mTarget);
                    result = returnValue.booleanValue();
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
                return (result ? 0 : -1);
            }
        }

        public int connectToGatt() {

            mTarget.connectGatt(LeService.this, false, mLeGattCallback);
            return 0;
        }

    }

    /**
     * LE设备扫描结果返回
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private class LeScanCallback extends ScanCallback {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {

            if (result != null) {
                //此处，我们尝试连接MI 设备
                String name = result.getDevice().getName();
                String address = result.getDevice().getAddress();
                Log.d(TAG, "onScanResult DeviceName : " + name + " DeviceAddress : " + address);

                if (name != null && name.startsWith(mTargetDeviceName)) {
                    //扫描到我们想要的设备后，立即停止扫描
                    mScanning = false;
                    mTarget = result.getDevice();
                    notifyUI("state", mTarget.getAddress());
                    mBluetoothLeScanner.stopScan(mScanCallback);

//                    boolean bondState = mBluetoothAdapter.getBondedDevices().contains(mTarget);
//                    if (bondState) {
//                        notifyUI("state", 6 + "");
//                    }
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.d(TAG, "onScanResult DeviceSize : " + results.size());

        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.d(TAG, "onScanFailed : " + errorCode);
        }
    }

    private static boolean stop = false;

    /**
     * gatt连接结果的返回
     */
    private class LeGattCallback extends BluetoothGattCallback {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "onConnectionStateChange status:" + status + "  newState:" + newState);
            if (newState == 2) {
                gatt.discoverServices();
                mGatt = gatt;
                mCommandPool = new CommandPool(LeService.this, mGatt);
                Thread thread = new Thread(mCommandPool);
                thread.start();

                notifyUI("state", "1");
            } else if (newState == 0) {
                mGatt = null;

                notifyUI("state", "0");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered status : " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> services = gatt.getServices();

                if (services != null) {
                    Log.d(TAG, "onServicesDiscovered num: " + services.size());
                }


                for (BluetoothGattService bluetoothGattService : services) {
                    List<BluetoothGattCharacteristic> charc = bluetoothGattService.getCharacteristics();

                    for (BluetoothGattCharacteristic charac : charc) {
                        UUID characteristicId = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
                        if (bluetoothGattService.getUuid().equals(UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"))) {

                            if (charac.getUuid().equals(characteristicId)) {

                                gatt.setCharacteristicNotification(charac, true);
                                BluetoothGattDescriptor descriptor = charac.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                gatt.writeDescriptor(descriptor);
                            }

//                            if(charac.getUuid().equals(UUID.fromString("00002a39-0000-1000-8000-00805f9b34fb"))){
//                                byte[] START_HEART_RATE_SCAN = {21, 2, 1};
//                                charac.setValue(START_HEART_RATE_SCAN);
//                                gatt.writeCharacteristic(charac);
//                            }
                        }


                        if (charac.getUuid().equals(Profile.UUID_CHAR_PAIR)) {
                            Log.d(TAG, "charPair found!");
                            //设备 配对特征值
                            alertChar = charac;
                        }
                        if (charac.getUuid().equals(Profile.IMMIDATE_ALERT_CHAR_UUID)) {
                            Log.d(TAG, "alertChar found!");
                            //设备 震动特征值
                            alertChar = charac;

                        }
                        if (charac.getUuid().equals(Profile.STEP_CHAR_UUID)) {
                            Log.d(TAG, "stepchar found!");
                            //设备 步数
                            stepChar = charac;
                            mCommandPool.addCommand(CommandPool.Type.setNotification, null, charac);

                            notifyUI("state", "4");
                        }
                        if (charac.getUuid().equals(Profile.BATTERY_CHAR_UUID)) {
                            Log.d(TAG, "battery found!");
                            batteryChar = charac;
                            gatt.readCharacteristic(charac);
                            mCommandPool.addCommand(CommandPool.Type.read, null, charac);
//                            mCommandPool.addCommand(CommandPool.Type.setNotification, null, charac);
                        }
                        if (charac.getUuid().equals(Profile.CONTROL_POINT_CHAR_UUID)) {
                            Log.d(TAG, "control point found!");
                            //LED颜色
                            controlPointChar = charac;
                        }
                        if (charac.getUuid().equals(Profile.VIBRATION_CHAR_UUID)) {
                            Log.d(TAG, "vibration found!");
                            //震动
                            vibrationChar = charac;
                        }

                    }
                }
            }
        }

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicChanged UUID : " + characteristic.getUuid());
            if (characteristic.getValue().length == 2) {

                int rate = characteristic.getValue()[1] & 0xFF;
                Log.d("data[0]", ((int) characteristic.getValue()[0] & 0xFF) + "");
                Log.d("rate", rate + "");
                Log.d("time", dateFormat.format(new Date()));
            }

        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            stop = true;
            Log.d(TAG, "onCharacteristicWrite UUID: " + characteristic.getUuid() + "state : " + status);
            mCommandPool.onCommandCallbackComplete();
            stop = false;
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            stop = true;
            Log.d(TAG, "onCharacteristicRead UUID : " + characteristic.getUuid());
            mCommandPool.onCommandCallbackComplete();

            if (characteristic.getUuid().equals(Profile.BATTERY_CHAR_UUID)) {
                BatteryInfoParser parser = new BatteryInfoParser(characteristic.getValue());
                notifyUI("battery", parser.getLevel() + "|" + parser.getStatusToString());

            }
            stop = false;
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(TAG, "onDescriptorWrite");
            mCommandPool.onCommandCallbackComplete();
        }

        public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            Log.d(TAG, "onPhyUpdate UUID : ");
            throw new RuntimeException("Stub!");
        }

        public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            throw new RuntimeException("Stub!");
        }


        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            throw new RuntimeException("Stub!");
        }


        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            throw new RuntimeException("Stub!");
        }

        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            throw new RuntimeException("Stub!");
        }

        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            throw new RuntimeException("Stub!");
        }
    }

    private void notifyUI(String type, String data) {
        intent = new Intent();
        intent.setAction(type);
        intent.putExtra(type, data);
        sendBroadcast(intent);
    }


}
