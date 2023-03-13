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

import java.util.ArrayList;
import java.util.LinkedHashMap;


/**
 * Dumps thread stack.
 */
class StackSampler extends AbstractSampler {

    private static final String TAG = "StackSampler";

    //sStackMap 的最大容量，默认 100个
    private static final int DEFAULT_MAX_ENTRY_COUNT = 100;
    //用于存储时间戳和 堆栈的关系
    private static final LinkedHashMap<Long, String> sStackMap = new LinkedHashMap<>();

    private int mMaxEntryCount = DEFAULT_MAX_ENTRY_COUNT;
    //主线程
    private Thread mCurrentThread;

    public StackSampler(Thread thread, //主线程也就是 ui线程
                        long sampleIntervalMillis//堆栈采样间隔 默认1000 ms 也就是1s
    ) {
        this(thread, DEFAULT_MAX_ENTRY_COUNT, sampleIntervalMillis);
    }

    public StackSampler(Thread thread, int maxEntryCount, long sampleIntervalMillis) {
        super(sampleIntervalMillis);
        mCurrentThread = thread;
        mMaxEntryCount = maxEntryCount;
    }

    public ArrayList<String> getThreadStackEntries(long startTime, long endTime) {
        ArrayList<String> result = new ArrayList<>();
        synchronized (sStackMap) {
            for (Long entryTime : sStackMap.keySet()) {
                //获取所有 开始时间和结束结束时间之内的 堆栈
                if (startTime < entryTime && entryTime < endTime) {
                    result.add(BlockInfo.TIME_FORMATTER.format(entryTime)
                            + BlockInfo.SEPARATOR
                            + BlockInfo.SEPARATOR
                            + sStackMap.get(entryTime));
                }
            }
        }
        return result;
    }

    @Override
    protected void doSample() {
        StringBuilder stringBuilder = new StringBuilder();


        //获取主线程堆栈， mCurrentThread 是主线程，构造方法中传进来的
        for (StackTraceElement stackTraceElement : mCurrentThread.getStackTrace()) {
            stringBuilder
                    .append(stackTraceElement.toString())
                    .append(BlockInfo.SEPARATOR);
        }

        synchronized (sStackMap) {
            //容量满了就随便删一个
            if (sStackMap.size() == mMaxEntryCount && mMaxEntryCount > 0) {
                sStackMap.remove(sStackMap.keySet().iterator().next());
            }

            //记录当前时间和堆栈的关系
            sStackMap.put(System.currentTimeMillis(), stringBuilder.toString());

            Log.e(TAG,"currentTime= "+System.currentTimeMillis() +"\n stackTrace="+stringBuilder.toString());
        }
    }
}