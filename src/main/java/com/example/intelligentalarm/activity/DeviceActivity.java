package com.example.intelligentalarm.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.intelligentalarm.R;
import com.example.intelligentalarm.service.LeService;

public class DeviceActivity extends Activity {

    private Intent serviceIntent;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_item);

        //开启蓝牙连接的服务
        serviceIntent = new Intent(DeviceActivity.this, LeService.class);
        bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    LeService.LocalBinder mService;
    ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = (LeService.LocalBinder) service;
            if (mService != null) {
                initBluetooth();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d("onServiceDisconnected", name.toString());
        }

        @Override
        public  void onBindingDied(ComponentName name) {
            Log.d("onBindingDied", name.toString());

        }


        @Override
        public  void onNullBinding(ComponentName name) {
            Log.d("onNullBinding", name.toString());
        }
    };


    public void handleClickEvent(View view) {
        if (view.getId() == R.id.scan) {
            mService.startLeScan();
        }
        if (view.getId() == R.id.clear) {
            this.clean();
        }

    }

    private void clean() {
        //TODO
    }

    @SuppressLint("ShowToast")
    private void initBluetooth() {

        boolean bluetoothStatte = mService.initBluetooth();
        if (!bluetoothStatte) {
            Toast.makeText(this.getApplicationContext(), "您的设备不支持蓝牙！", Toast.LENGTH_LONG);
        } else {
            boolean leScannerState = mService.initLeScanner();
            if (leScannerState) {
                Toast.makeText(this.getApplicationContext(), "蓝牙已就绪！", Toast.LENGTH_LONG);
            }
        }
    }
}
