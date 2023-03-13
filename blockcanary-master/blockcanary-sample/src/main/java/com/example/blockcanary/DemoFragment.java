/*
 * Copyright (C) 2016 MarkZhai (http://zhaiyifan.cn).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.blockcanary;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.github.moduth.blockcanary.ui.DisplayActivity;

import java.io.FileInputStream;
import java.io.IOException;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

/**
 * 用于模拟主线程阻塞的
 */
public class DemoFragment extends Fragment implements View.OnClickListener {

    private static final String DEMO_FRAGMENT = "DemoFragment";
    private static final String TAG = "DemoFragment";

    public static DemoFragment newInstance() {
        return new DemoFragment();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_main, null);
    }

    @Override
    public void onViewCreated(final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View tvTitle = (View) view.findViewById(R.id.tv_title);
        Button button1 = (Button) view.findViewById(R.id.button1);
        Button button2 = (Button) view.findViewById(R.id.button2);
        Button button3 = (Button) view.findViewById(R.id.button3);
        Button button4 = (Button) view.findViewById(R.id.button4);

        tvTitle.setOnClickListener(this);
        button1.setOnClickListener(this);
        button2.setOnClickListener(this);
        button3.setOnClickListener(this);
        button4.setOnClickListener(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    private boolean isEnabled = false;

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.tv_title:
                System.out.println("hulahula ");
                break;
            case R.id.button1:
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Log.e(DEMO_FRAGMENT, "onClick of R.id.button1: ", e);
                }
                break;
            case R.id.button2:
                for (int i = 0; i < 100; ++i) {
                    readFile();
                }
                break;
            case R.id.button3:
                double result = compute();
                System.out.println(result);
                break;
            case R.id.button4:

                long startTime = SystemClock.currentThreadTimeMillis();

                for (int i = 0; i < 10; i++) {
                    ComponentName component = new ComponentName(getContext(), DisplayActivity.class);
                    PackageManager packageManager = getContext().getPackageManager();
                    int newState = isEnabled ? COMPONENT_ENABLED_STATE_ENABLED : COMPONENT_ENABLED_STATE_DISABLED;
                    // Blocks on IPC.
                    // 设置 componentClass 是否可用 ，
                    packageManager.setComponentEnabledSetting(component, newState, DONT_KILL_APP);
                }

                long endTime = SystemClock.currentThreadTimeMillis();

                Log.e(TAG, "ipc method cose time = " + (endTime - startTime));


                long startTime1 = SystemClock.currentThreadTimeMillis();

                for (int i = 0; i < 10; i++) {
                    isEnabled = !isEnabled;
                }

                long endTime2 = SystemClock.currentThreadTimeMillis();

                Log.e(TAG, "common method cose time = " + (endTime2 - startTime1) + " isEnabled=" + isEnabled);

                break;
            default:
                break;
        }
    }

    private static double compute() {
        double result = 0;
        for (int i = 0; i < 1000000; ++i) {
            result += Math.acos(Math.cos(i));
            result -= Math.asin(Math.sin(i));
        }
        return result;
    }

    private static void readFile() {
        FileInputStream reader = null;
        try {
            reader = new FileInputStream("/proc/stat");
            while (reader.read() != -1) ;
        } catch (IOException e) {
            Log.e(DEMO_FRAGMENT, "readFile: /proc/stat", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(DEMO_FRAGMENT, " on close reader ", e);
                }
            }
        }
    }
}
