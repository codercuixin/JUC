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
 * A delayed result-bearing action that can be cancelled.
 * Usually a scheduled future is the result of scheduling
 * a task with a {@link ScheduledExecutorService}.
 *
 * 一个延迟的，承载结果的操作，并且该操作是可取消的。
 * 通常，一个ScheduledFuture是使用ScheduledExecutorService计划任务的结果。
 *
 * @param <V> The result type returned by this Future
 * @author Doug Lea
 * @since 1.5
 */
public interface ScheduledFuture<V> extends Delayed, Future<V> {
}
