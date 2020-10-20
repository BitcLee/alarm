package com.example.intelligentalarm.activity;

import android.annotation.SuppressLint;
import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TabHost;
import android.widget.Toast;

import com.example.intelligentalarm.R;

public class MainActivity extends TabActivity {
    public Intent deviceIntent, chartIntent, configIntent;
    TabHost tabHost;
    @SuppressLint({"UseCompatLoadingForDrawables", "ResourceType"})
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tabHost = getTabHost();


        findViewById(R.id.device_tag).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                onCheckedChanged(v);
            }
        });

        findViewById(R.id.chart_tag).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                onCheckedChanged(v);
            }
        });

        findViewById(R.id.config_tag).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                onCheckedChanged(v);
            }
        });

        deviceIntent = new Intent(MainActivity.this, DeviceActivity.class);
        chartIntent = new Intent(MainActivity.this, ChartActivity.class);
        configIntent = new Intent(MainActivity.this, ConfigActivity.class);


        /* 以上创建和添加标签页也可以用如下代码实现 */
        tabHost.addTab(tabHost.newTabSpec("tab1").setIndicator(getString(R.string.device), getResources().getDrawable(R.drawable.bluetooth)).setContent(deviceIntent));
        tabHost.addTab(tabHost.newTabSpec("tab2").setIndicator("标签页二").setContent(chartIntent));
        tabHost.addTab(tabHost.newTabSpec("tab3").setIndicator("标签页三").setContent(configIntent));

    }

    public void onCheckedChanged(View view) {
        switch (view.getId()) {
            case R.id.device_tag:
                this.tabHost.setCurrentTabByTag("tab1");
                break;

            case R.id.chart_tag:
                this.tabHost.setCurrentTabByTag("tab2");
                break;

            case R.id.config_tag:
                this.tabHost.setCurrentTabByTag("tab3");
                break;

        }
    }
}