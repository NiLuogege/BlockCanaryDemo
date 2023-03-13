/*
 * Copyright (C) 2016 MarkZhai (http://zhaiyifan.cn).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.moduth.blockcanary;

import android.util.Log;

import com.github.moduth.blockcanary.internal.BlockInfo;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dumps cpu usage.
 */
class CpuSampler extends AbstractSampler {

    private static final String TAG = "CpuSampler";
    private static final int BUFFER_SIZE = 1000;

    /**
     * TODO: Explain how we define cpu busy in README
     */
    private final int BUSY_TIME;
    //容量为十个
    private static final int MAX_ENTRY_COUNT = 10;
    //用于存储时间戳和 cup信息的关系
    private final LinkedHashMap<Long, String> mCpuInfoEntries = new LinkedHashMap<>();
    private int mPid = 0;
    private long mUserLast = 0;
    private long mSystemLast = 0;
    private long mIdleLast = 0;
    private long mIoWaitLast = 0;
    private long mTotalLast = 0;
    private long mAppCpuTimeLast = 0;

    public CpuSampler(long sampleInterval) {
        super(sampleInterval);
        //采样时间 * 1.2 就是 繁忙时间
        // mSampleInterval 默认为 1s
        BUSY_TIME = (int) (mSampleInterval * 1.2f);
    }

    @Override
    public void start() {
        super.start();
        reset();
    }

    /**
     * Get cpu rate information
     *
     * 获取cpu 使用率
     *  这是吧记录的cup的信息都给 输出了
     * @return string show cpu rate information
     */
    public String getCpuRateInfo() {
        StringBuilder sb = new StringBuilder();
        synchronized (mCpuInfoEntries) {
            for (Map.Entry<Long, String> entry : mCpuInfoEntries.entrySet()) {
                long time = entry.getKey();
                sb.append(BlockInfo.TIME_FORMATTER.format(time))
                        .append(' ')
                        .append(entry.getValue())
                        .append(BlockInfo.SEPARATOR);
            }
        }
        return sb.toString();
    }

    public boolean isCpuBusy(long start, long end) {
        if (end - start > mSampleInterval) {
            //向前向后各扩展一个采样时间
            //采样时间默认为 1s
            long s = start - mSampleInterval;
            long e = start + mSampleInterval;
            long last = 0;
            synchronized (mCpuInfoEntries) {
                for (Map.Entry<Long, String> entry : mCpuInfoEntries.entrySet()) {
                    long time = entry.getKey();
                    //
                    if (s < time && time < e) {
                        //两个采样点的时间 大于 BUSY_TIME 就说明是 cup繁忙？？？
                        if (last != 0 && time - last > BUSY_TIME) {
                            return true;
                        }
                        last = time;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 开始采样
     */
    @Override
    protected void doSample() {
        BufferedReader cpuReader = null;
        BufferedReader pidReader = null;

        try {
            cpuReader = new BufferedReader(new InputStreamReader(
                    new FileInputStream("/proc/stat")), BUFFER_SIZE);
            //整体cpu使用率
            String cpuRate = cpuReader.readLine();
            if (cpuRate == null) {
                cpuRate = "";
            }

            if (mPid == 0) {
                mPid = android.os.Process.myPid();
            }
            pidReader = new BufferedReader(new InputStreamReader(
                    new FileInputStream("/proc/" + mPid + "/stat")), BUFFER_SIZE);
            //当前app cup使用率
            String pidCpuRate = pidReader.readLine();
            if (pidCpuRate == null) {
                pidCpuRate = "";
            }

            //解析
            parse(cpuRate, pidCpuRate);
        } catch (Throwable throwable) {
            Log.e(TAG, "doSample: ", throwable);
        } finally {
            try {
                if (cpuReader != null) {
                    cpuReader.close();
                }
                if (pidReader != null) {
                    pidReader.close();
                }
            } catch (IOException exception) {
                Log.e(TAG, "doSample: ", exception);
            }
        }
    }

    private void reset() {
        mUserLast = 0;
        mSystemLast = 0;
        mIdleLast = 0;
        mIoWaitLast = 0;
        mTotalLast = 0;
        mAppCpuTimeLast = 0;
    }

    /**
     * 参考：https://www.cnblogs.com/aziji/articles/9634960.html
     */
    private void parse(String cpuRate, String pidCpuRate) {
        String[] cpuInfoArray = cpuRate.split(" ");
        if (cpuInfoArray.length < 9) {
            return;
        }

        //分别读出这几个时间 这些是证系统的cup使用率
        long user = Long.parseLong(cpuInfoArray[2]);
        long nice = Long.parseLong(cpuInfoArray[3]);
        long system = Long.parseLong(cpuInfoArray[4]);
        long idle = Long.parseLong(cpuInfoArray[5]);
        long ioWait = Long.parseLong(cpuInfoArray[6]);
        long total = user + nice + system + idle + ioWait
                + Long.parseLong(cpuInfoArray[7])
                + Long.parseLong(cpuInfoArray[8]);

        String[] pidCpuInfoList = pidCpuRate.split(" ");
        if (pidCpuInfoList.length < 17) {
            return;
        }

        //这个是app的cup使用时间
        long appCpuTime = Long.parseLong(pidCpuInfoList[13])
                + Long.parseLong(pidCpuInfoList[14])
                + Long.parseLong(pidCpuInfoList[15])
                + Long.parseLong(pidCpuInfoList[16]);

        if (mTotalLast != 0) {
            StringBuilder stringBuilder = new StringBuilder();
            long idleTime = idle - mIdleLast;
            long totalTime = total - mTotalLast;

            stringBuilder
                    .append("cpu:")
                    .append((totalTime - idleTime) * 100L / totalTime) //cup 总的使用率
                    .append("% ")
                    .append("app:")
                    .append((appCpuTime - mAppCpuTimeLast) * 100L / totalTime) //app cup 使用率
                    .append("% ")
                    .append("[")
                    .append("user:").append((user - mUserLast) * 100L / totalTime) // 用户态使用率
                    .append("% ")
                    .append("system:").append((system - mSystemLast) * 100L / totalTime) //内核态使用率
                    .append("% ")
                    .append("ioWait:").append((ioWait - mIoWaitLast) * 100L / totalTime)// io等待率
                    .append("% ]");

            synchronized (mCpuInfoEntries) {
                //时间戳和 cup信息存起来
                mCpuInfoEntries.put(System.currentTimeMillis(), stringBuilder.toString());
                //超过容量就删一个
                if (mCpuInfoEntries.size() > MAX_ENTRY_COUNT) {
                    for (Map.Entry<Long, String> entry : mCpuInfoEntries.entrySet()) {
                        Long key = entry.getKey();
                        mCpuInfoEntries.remove(key);
                        break;
                    }
                }
            }
        }
        mUserLast = user;
        mSystemLast = system;
        mIdleLast = idle;
        mIoWaitLast = ioWait;
        mTotalLast = total;

        mAppCpuTimeLast = appCpuTime;
    }
}