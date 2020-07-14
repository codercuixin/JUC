/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package juc;

/**
 * A {@link ScheduledFuture} that is {@link Runnable}. Successful
 * execution of the {@code run} method causes completion of the
 * {@code Future} and allows access to its results.
 *
 * 可运行的ScheduledFuture。
 * 成功执行run方法会导致Future完成并允许访问其结果。
 *
 * @param <V> The result type returned by this Future's {@code get} method
 * @author Doug Lea
 * @see FutureTask
 * @see Executor
 * @since 1.6
 */
public interface RunnableScheduledFuture<V> extends RunnableFuture<V>, ScheduledFuture<V> {

    /**
     * Returns {@code true} if this task is periodic. A periodic task may
     * re-run according to some schedule. A non-periodic task can be
     * run only once.
     * 如果此任务是周期性的，则返回true。定期任务可以根据一些时间表重新运行。非定期任务只能运行一次。
     *
     * @return {@code true} if this task is periodic
     */
    boolean isPeriodic();
}
