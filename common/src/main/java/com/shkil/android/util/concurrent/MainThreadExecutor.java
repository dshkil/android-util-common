/*
 * Copyright (C) 2017 Dmytro Shkil
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.shkil.android.util.concurrent;

import java.util.concurrent.Executor;

public class MainThreadExecutor implements Executor {

    private static final MainThreadExecutor INSTANCE = new MainThreadExecutor();

    public static MainThreadExecutor getInstance() {
        return INSTANCE;
    }

    public static MainThreadExecutor get() {
        return INSTANCE;
    }

    protected MainThreadExecutor() {
    }

    @Override
    public void execute(Runnable runnable) {
        if (MainThread.isCurrent()) {
            runnable.run();
        } else {
            post(runnable);
        }
    }

    /**
     * Causes the runnable to be added to the message queue
     */
    public void post(Runnable runnable) {
        MainThread.HANDLER.post(runnable);
    }

    /**
     * Remove any pending posts of runnable that are in the message queue
     */
    public void removeCallbacks(Runnable runnable) {
        MainThread.HANDLER.removeCallbacks(runnable);
    }

}
