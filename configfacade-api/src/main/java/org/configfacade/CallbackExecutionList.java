package org.configfacade;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.Executor;

import com.google.common.util.concurrent.FutureCallback;

public class CallbackExecutionList<V> implements FutureCallback<V> {

	private volatile RunnableExecutorPair<V> runnables;

	public CallbackExecutionList() {}

	public void add(FutureCallback<? super V> runnable, Executor executor) {

		checkNotNull(runnable, "Runnable was null.");
		checkNotNull(executor, "Executor was null.");

		runnables = new RunnableExecutorPair<V>(runnable, executor, runnables);
		
	}

	public void onSuccess(V v) {

		RunnableExecutorPair<V> list = runnables;		

		while (list != null) {
			callSuccess(v, list.runnable, list.executor);
			list = list.next;
		}
	}

	public void onFailure(Throwable throwable) {
		RunnableExecutorPair<V> list;
		synchronized (this) {
			list = runnables;
		}

		while (list != null) {
			callFailure(throwable, list.runnable, list.executor);
			list = list.next;
		}
	}

	protected void callSuccess(final V value, final FutureCallback<? super V> runnable, Executor executor) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				runnable.onSuccess(value);
			}
		});
	}

	protected void callFailure(final Throwable t, final FutureCallback<? super V> runnable, Executor executor) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				runnable.onFailure(t);
			}
		});
	}

	private static final class RunnableExecutorPair<V> {
		final FutureCallback<? super V> runnable;
		final Executor executor;
		RunnableExecutorPair<V> next;

		RunnableExecutorPair(FutureCallback<? super V> runnable, Executor executor, RunnableExecutorPair<V> next) {
			this.runnable = runnable;
			this.executor = executor;
			this.next = next;
		}
	}
}
