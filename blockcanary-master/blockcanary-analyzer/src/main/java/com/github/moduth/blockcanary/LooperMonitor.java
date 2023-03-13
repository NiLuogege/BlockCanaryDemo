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

import android.os.Debug;
import android.os.SystemClock;
import android.util.Printer;

class LooperMonitor implements Printer {

    private static final int DEFAULT_BLOCK_THRESHOLD_MILLIS = 3000;

    //卡顿的阀值， 当超过这个会被认为为卡段，BlockCanaryContext中默认为 1s
    private long mBlockThresholdMillis = DEFAULT_BLOCK_THRESHOLD_MILLIS;
    private long mStartTimestamp = 0;
    private long mStartThreadTimestamp = 0;
    private boolean mPrintingStarted = false;

    //卡顿回调
    private BlockListener mBlockListener = null;
    //在调试模式下是否停止 检测，默认为 true
    private final boolean mStopWhenDebugging;

    public interface BlockListener {
        void onBlockEvent(long realStartTime,
                          long realTimeEnd,
                          long threadTimeStart,
                          long threadTimeEnd);
    }

    public LooperMonitor(BlockListener blockListener, //卡顿回调
                         long blockThresholdMillis,//卡顿的阀值， 当超过这个会被认为为卡段，默认为 1s
                         boolean stopWhenDebugging//在调试模式下是否停止 检测，默认为 true
    ) {
        if (blockListener == null) {
            throw new IllegalArgumentException("blockListener should not be null.");
        }
        mBlockListener = blockListener;
        mBlockThresholdMillis = blockThresholdMillis;
        mStopWhenDebugging = stopWhenDebugging;
    }

    /**
     * Looper.loop() 方法中在 msg.target.dispatchMessage(msg) 前后， 也就是在 主线程执行具体代码 的前后
     * 都会调用 println 方法 ，之前输出的标识为 >>>>>  ，之后输出的标识为 <<<<<
     *
     */
    @Override
    public void println(String x) {
        //debug 的时候不 监控
        if (mStopWhenDebugging && Debug.isDebuggerConnected()) {
            return;
        }

        //输出开始的信息
        if (!mPrintingStarted) {
            mStartTimestamp = System.currentTimeMillis();
            mStartThreadTimestamp = SystemClock.currentThreadTimeMillis();
            mPrintingStarted = true;
            //在子线程中获取调用栈和CPU信息
            startDump();
        } else {
            //输出结束的信息
            final long endTime = System.currentTimeMillis();
            mPrintingStarted = false;
            //判断是否超过设置的阈值
            if (isBlock(endTime)) {
                //回调
                notifyBlockEvent(endTime);
            }
            //停止获取调用栈和CPU信息
            stopDump();
        }
    }

    //判断是否超过设置的阈值
    private boolean isBlock(long endTime) {
        return endTime - mStartTimestamp > mBlockThresholdMillis;
    }

    private void notifyBlockEvent(final long endTime) {
        final long startTime = mStartTimestamp;
        final long startThreadTime = mStartThreadTimestamp;
        final long endThreadTime = SystemClock.currentThreadTimeMillis();
        HandlerThreadFactory.getWriteLogThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                //异步线程回调 onBlockEvent
                mBlockListener.onBlockEvent(startTime, endTime, startThreadTime, endThreadTime);
            }
        });
    }

    private void startDump() {
        if (null != BlockCanaryInternals.getInstance().stackSampler) {
            //开始堆栈采样
            BlockCanaryInternals.getInstance().stackSampler.start();
        }

        if (null != BlockCanaryInternals.getInstance().cpuSampler) {
            //开始cpu采样
            BlockCanaryInternals.getInstance().cpuSampler.start();
        }
    }

    private void stopDump() {
        if (null != BlockCanaryInternals.getInstance().stackSampler) {
            //结束堆栈采样
            BlockCanaryInternals.getInstance().stackSampler.stop();
        }

        if (null != BlockCanaryInternals.getInstance().cpuSampler) {
            //结束cpu采样
            BlockCanaryInternals.getInstance().cpuSampler.stop();
        }
    }
}