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

import com.shkil.android.util.CompletionListener;
import com.shkil.android.util.ExceptionListener;
import com.shkil.android.util.Result;
import com.shkil.android.util.ResultListener;
import com.shkil.android.util.ValueListener;
import com.shkil.android.util.ValueMapper;
import com.shkil.android.util.concurrent.AbstractResultFuture.OnResultRunnable;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.concurrent.GuardedBy;

public class ResultFutures {

    public static <V> ResultFuture<V> result(Result<V> result) {
        return result(result, MainThreadExecutor.getInstance());
    }

    public static <V> ResultFuture<V> result(Result<V> result, Executor defaultResultExecutor) {
        return new ImmediateResultFuture<V>(result, defaultResultExecutor);
    }

    public static <V> ResultFuture<V> success(V value) {
        return success(value, MainThreadExecutor.getInstance());
    }

    public static <V> ResultFuture<V> success(V value, Executor defaultResultExecutor) {
        return result(Result.<V>success(value), defaultResultExecutor);
    }

    public static <V> ResultFuture<V> failure(Exception ex) {
        return failure(ex, MainThreadExecutor.getInstance());
    }

    public static <V> ResultFuture<V> failure(Exception ex, Executor defaultResultExecutor) {
        return result(Result.<V>failure(ex), defaultResultExecutor);
    }

    public static <V> ResultListener<V> successAdapter(ValueListener<V> listener) {
        return new SuccessResultAdapter<>(listener);
    }

    public static <V> ResultListener<V> errorAdapter(ExceptionListener ex) {
        return new ErrorResultAdapter<>(ex);
    }

    public static <V> ResultFutureTask<V> futureTask(Callable<V> task) {
        return ResultFutureTask.create(task);
    }

    public static <V> ResultFutureTask<V> executeTask(Callable<V> task, Executor taskExecutor) {
        return ResultFutureTask.execute(task, taskExecutor);
    }

    public static <V> LatchResultFuture<V> latch() {
        return latch(MainThreadExecutor.getInstance());
    }

    public static <V> LatchResultFuture<V> latch(Executor defaultResultExecutor) {
        return new LatchResultFuture<>(defaultResultExecutor);
    }

    private static class ImmediateResultFuture<V> implements ResultFuture<V> {
        private final Result<V> result;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile Executor defaultResultExecutor;
        @GuardedBy("this")
        private volatile Runnable cancellationListener;
        @GuardedBy("this")
        private volatile Executor cancellationListenerExecutor;


        public static <V> ResultFuture<V> success(V value, Executor defaultResultExecutor) {
            return create(Result.success(value), defaultResultExecutor);
        }

        public static <V> ResultFuture<V> failure(Exception ex, Executor defaultResultExecutor) {
            return create(Result.<V>failure(ex), defaultResultExecutor);
        }

        public static <V> ResultFuture<V> create(Result<V> result, Executor defaultResultExecutor) {
            return new ImmediateResultFuture<V>(result, defaultResultExecutor);
        }

        protected ImmediateResultFuture(Result<V> result, Executor defaultResultExecutor) {
            this.result = result;
            this.defaultResultExecutor = defaultResultExecutor;
        }

        @Override
        public boolean isResultReady() {
            return !isCancelled();
        }

        @Override
        public Result<V> await() {
            return result;
        }

        @Override
        public V awaitValue() {
            return await().getValue();
        }

        @Override
        public V awaitValueOrThrow() throws Exception {
            return await().getValueOrThrow();
        }

        @Override
        public V awaitValueOrThrowEx() throws ExecutionException {
            return await().getValueOrThrowEx();
        }

        @Override
        public V awaitValueOrThrowRuntime() throws RuntimeException {
            return await().getValueOrThrowRuntime();
        }

        @Override
        public Result<V> await(long timeout, TimeUnit unit) {
            return result;
        }

        @Override
        public Result<V> peekResult() {
            return result;
        }

        @Override
        public V peekValue() {
            return result != null ? result.getValue() : null;
        }

        @Override
        public V peekValueOrThrow() throws Exception {
            return result != null ? result.getValueOrThrow() : null;
        }

        @Override
        public V peekValueOrThrowEx() throws ExecutionException {
            return result != null ? result.getValueOrThrowEx() : null;
        }

        @Override
        public V peekValueOrThrowRuntime() throws RuntimeException {
            return result != null ? result.getValueOrThrowRuntime() : null;
        }

        @Override
        public boolean cancel() {
            if (cancelled.getAndSet(true)) {
                return false;
            }
            synchronized (this) {
                if (cancellationListener != null) {
                    if (cancellationListenerExecutor != null) {
                        cancellationListenerExecutor.execute(cancellationListener);
                    } else {
                        cancellationListener.run();
                    }
                }
                this.cancellationListener = null;
                this.cancellationListenerExecutor = null;
            }
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public ResultFuture<V> onCancel(Runnable listener) {
            return onCancel(listener, defaultResultExecutor);
        }

        @Override
        public ResultFuture<V> onResult(ResultListener<V> listener) {
            return onResult(listener, defaultResultExecutor);
        }

        @Override
        public ResultFuture<V> onCompleted(CompletionListener listener) {
            return onCompleted(listener, defaultResultExecutor);
        }

        @Override
        public synchronized ResultFuture<V> onCancel(Runnable listener, Executor listenerExecutor) {
            if (cancellationListener != null) {
                throw new IllegalStateException("Only one cancellation listener is supported");
            }
            if (isCancelled()) {
                if (listenerExecutor != null) {
                    listenerExecutor.execute(listener);
                } else if (isResultReady()) {
                    listener.run();
                }
            } else {
                this.cancellationListener = listener;
                this.cancellationListenerExecutor = listenerExecutor;
            }
            return this;

        }

        @Override
        public ResultFuture<V> onResult(ResultListener<V> listener, Executor listenerExecutor) {
            if (listenerExecutor != null) {
                listenerExecutor.execute(new OnResultRunnable<>(listener, result, cancelled));
            } else if (isResultReady()) {
                listener.onResult(result);
            }
            return this;
        }

        @Override
        public ResultFuture<V> onCompleted(final CompletionListener listener, Executor listenerExecutor) {
            if (listenerExecutor != null) {
                listenerExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        listener.onCompleted(false);
                    }
                });
            } else if (isResultReady()) {
                listener.onCompleted(false);
            }
            return this;
        }

        @Override
        public ResultFuture<V> onSuccess(ValueListener<V> listener) {
            return onSuccess(listener, defaultResultExecutor);
        }

        @Override
        public ResultFuture<V> onSuccess(ValueListener<V> listener, Executor resultExecutor) {
            return onResult(ResultFutures.successAdapter(listener), resultExecutor);
        }

        @Override
        public ResultFuture<V> onError(ExceptionListener listener) {
            return onError(listener, defaultResultExecutor);
        }

        @Override
        public ResultFuture<V> onError(ExceptionListener listener, Executor resultExecutor) {
            return onResult(ResultFutures.<V>errorAdapter(listener), resultExecutor);
        }

        @Override
        public <R> ResultFuture<R> map(ValueMapper<? super V, ? extends R> mapper) {
            return ResultFutureAdapter.map(this, mapper);
        }

        @Override
        public <R> ResultFuture<R> map(Executor mapperExecutor, ValueMapper<? super V, ? extends R> mapper) {
            return ResultFutureAdapter.map(this, mapper, mapperExecutor);
        }

        @Override
        public Executor getDefaultResultExecutor() {
            return defaultResultExecutor;
        }
    }

    private static class SuccessResultAdapter<V> implements ResultListener<V> {
        private final ValueListener<V> listener;

        public SuccessResultAdapter(ValueListener<V> listener) {
            this.listener = listener;
        }

        @Override
        public void onResult(Result<V> result) {
            if (result.isSuccess()) {
                listener.onValue(result.getValue());
            }
        }
    }

    private static class ErrorResultAdapter<V> implements ResultListener<V> {
        private final ExceptionListener listener;

        public ErrorResultAdapter(ExceptionListener listener) {
            this.listener = listener;
        }

        @Override
        public void onResult(Result<V> result) {
            Exception ex = result.getException();
            if (ex != null) {
                listener.onException(ex);
            }
        }
    }

}
