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

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.preference.PreferenceManager;

import com.github.moduth.blockcanary.ui.DisplayActivity;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

public final class BlockCanary {

    private static final String TAG = "BlockCanary";

    private static BlockCanary sInstance;
    private BlockCanaryInternals mBlockCanaryCore;
    //检测是否启动
    private boolean mMonitorStarted = false;

    private BlockCanary() {
        //设置Context
        BlockCanaryInternals.setContext(BlockCanaryContext.get());
        //创建并获取 BlockCanaryInternals 也是个单例
        mBlockCanaryCore = BlockCanaryInternals.getInstance();
        //设置 卡顿拦截器 ，一般用作全局处理卡顿的回调
        mBlockCanaryCore.addBlockInterceptor(BlockCanaryContext.get());

        //如果不显示 推送，就不添加 DisplayService 了
        if (!BlockCanaryContext.get().displayNotification()) {
            return;
        }
        //添加 DisplayService 这个拦截器
        mBlockCanaryCore.addBlockInterceptor(new DisplayService());

    }

    /**
     * Install {@link BlockCanary}
     *
     * @param context            Application context
     * @param blockCanaryContext BlockCanary context
     * @return {@link BlockCanary}
     */
    public static BlockCanary install(Context context, BlockCanaryContext blockCanaryContext) {
        //初始化，只是在 BlockCanaryContext中保存了 Context 和 用于自定义的 BlockCanaryContext
        BlockCanaryContext.init(context, blockCanaryContext);
        //设置 推送是否可用
        setEnabled(context, DisplayActivity.class, BlockCanaryContext.get().displayNotification());
        //返回单例的 BlockCanary
        return get();
    }

    /**
     * Get {@link BlockCanary} singleton.
     *
     * @return {@link BlockCanary} instance
     */
    public static BlockCanary get() {
        if (sInstance == null) {
            synchronized (BlockCanary.class) {
                if (sInstance == null) {
                    sInstance = new BlockCanary();
                }
            }
        }
        return sInstance;
    }

    /**
     * Start monitoring.
     * 开始监测
     */
    public void start() {
        if (!mMonitorStarted) {
            mMonitorStarted = true;
            //设置自定义的 Printer
            Looper.getMainLooper().setMessageLogging(mBlockCanaryCore.monitor);
        }
    }

    /**
     * Stop monitoring.
     */
    public void stop() {
        if (mMonitorStarted) {
            mMonitorStarted = false;
            Looper.getMainLooper().setMessageLogging(null);
            mBlockCanaryCore.stackSampler.stop();
            mBlockCanaryCore.cpuSampler.stop();
        }
    }

    /**
     * Zip and upload log files, will user context's zip and log implementation.
     */
    public void upload() {
        Uploader.zipAndUpload();
    }

    /**
     * Record monitor start time to preference, you may use it when after push which tells start
     * BlockCanary.
     */
    public void recordStartTime() {
        PreferenceManager.getDefaultSharedPreferences(BlockCanaryContext.get().provideContext())
                .edit()
                .putLong("BlockCanary_StartTime", System.currentTimeMillis())
                .commit();
    }

    /**
     * Is monitor duration end, compute from recordStartTime end provideMonitorDuration.
     *
     * @return true if ended
     */
    public boolean isMonitorDurationEnd() {
        long startTime =
                PreferenceManager.getDefaultSharedPreferences(BlockCanaryContext.get().provideContext())
                        .getLong("BlockCanary_StartTime", 0);
        return startTime != 0 && System.currentTimeMillis() - startTime >
                BlockCanaryContext.get().provideMonitorDuration() * 3600 * 1000;
    }

    // these lines are originally copied from LeakCanary: Copyright (C) 2015 Square, Inc.
    // 创建一个单线程的线程池 ，名为 File-IO
    private static final Executor fileIoExecutor = newSingleThreadExecutor("File-IO");

    /**
     * 这个设置 还在子线程中设置？  这个方法这么耗时吗？
     *
     * 原因 源码注释行已经说了 Blocks on IPC. 也就是说会被IPC阻塞，所以这里在子线程中调用，草 真他妈细
     */
    private static void setEnabledBlocking(Context appContext,
                                           Class<?> componentClass,//显示具体堆栈的Activity
                                           boolean enabled//是否显示 Notification
    ) {
        ComponentName component = new ComponentName(appContext, componentClass);
        PackageManager packageManager = appContext.getPackageManager();
        int newState = enabled ? COMPONENT_ENABLED_STATE_ENABLED : COMPONENT_ENABLED_STATE_DISABLED;
        // Blocks on IPC.
        // 设置 componentClass 是否可用 ，
        packageManager.setComponentEnabledSetting(component, newState, DONT_KILL_APP);
    }
    // end of lines copied from LeakCanary

    private static void executeOnFileIoThread(Runnable runnable) {
        fileIoExecutor.execute(runnable);
    }

    private static Executor newSingleThreadExecutor(String threadName) {
        return Executors.newSingleThreadExecutor(new SingleThreadFactory(threadName));
    }

    private static void setEnabled(Context context,
                                   final Class<?> componentClass,//显示具体堆栈的Activity
                                   final boolean enabled //是否显示 Notification
    ) {
        final Context appContext = context.getApplicationContext();
        executeOnFileIoThread(new Runnable() {
            @Override
            public void run() {
                //在 File-IO 线程中执行 setEnabledBlocking
                setEnabledBlocking(appContext, componentClass, enabled);
            }
        });
    }
}
