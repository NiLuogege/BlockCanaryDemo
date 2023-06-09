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

import android.app.Application;
import android.content.Context;

import com.github.moduth.blockcanary.BlockCanary;

public class DemoApplication extends Application {

    private static Context sContext;

    @Override
    public void onCreate() {
        super.onCreate();
        sContext = this;
        //初始化 BlockCanary ,主要是去 设置 推送是否可用 ，然后会创建一个 BlockCanary
        BlockCanary blockCanary = BlockCanary.install(this, new AppContext());
        //开始检测， 这里就会设置一个自定义的 Printer
        blockCanary.start();
    }

    public static Context getAppContext() {
        return sContext;
    }
}
