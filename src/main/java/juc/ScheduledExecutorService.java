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
 * ExecutorService可以安排命令在给定的延迟后运行或定期执行。
 *
 * schedule 方法创建具有各种延迟的任务，并返回可用于取消或检查执行状态的任务对象。
 *  scheduleAtFixedRate和scheduleWithFixedDelay方法创建并执行定期运行的任务，直到该任务被取消为止。
 *
 * 使用Executor.execute（Runnable）和ExecutorService submit 方法提交的命令被以计划延迟为零的方式调度。
 * 调度 方法中还允许零延迟和负延迟（但不允许使用周期），并将其视为立即执行的请求。
 *
 * 所有调度方法都接受相对延迟和周期作为参数，而不是绝对时间或日期作为参数。
 * 将代表日期的绝对时间转换为所需的形式很容易。
 * 例如，要在某个将来的日期进行调度，可以使用：schedule(task, date.getTime() - System.currentTimeMillis(), TimeUnit.MILLISECONDS)。
 * 但是请注意，由于网络时间同步协议，时钟漂移或其他因素，相对延迟的到期时间不必与启用任务的当前日期一致。
 *
 * Executors类为此程序包中提供的ScheduledExecutorService实现提供了方便的工厂方法。
 *
 * <h3>使用示例</h3>
 * <p>
 * Here is a class with a method that sets up a ScheduledExecutorService
 * to beep every ten seconds for an hour:
 *
 * <pre> {@code
 * import static juc.TimeUnit.*;
 * class BeeperControl {
 *   private final ScheduledExecutorService scheduler =
 *     Executors.newScheduledThreadPool(1);
 *
 *   public void beepForAnHour() {
 *     final Runnable beeper = new Runnable() {
 *       public void run() { System.out.println("beep"); }
 *     };
 *     final ScheduledFuture<?> beeperHandle =
 *       scheduler.scheduleAtFixedRate(beeper, 10, 10, SECONDS);
 *     scheduler.schedule(new Runnable() {
 *       public void run() { beeperHandle.cancel(true); }
 *     }, 60 * 60, SECONDS);
 *   }
 * }}</pre>
 *
 * @author Doug Lea
 * @since 1.5
 */
public interface ScheduledExecutorService extends ExecutorService {

    /**
     * Creates and executes a one-shot action that becomes enabled
     * after the given delay.
     *
     * @param command the task to execute
     * @param delay   the time from now to delay execution
     * @param unit    the time unit of the delay parameter
     * @return a ScheduledFuture representing pending completion of
     * the task and whose {@code get()} method will return
     * {@code null} upon completion
     * @throws RejectedExecutionException if the task cannot be
     *                                    scheduled for execution
     * @throws NullPointerException       if command is null
     */
    ScheduledFuture<?> schedule(Runnable command,
                                long delay, TimeUnit unit);

    /**
     * Creates and executes a ScheduledFuture that becomes enabled after the
     * given delay.
     *
     * @param callable the function to execute
     * @param delay    the time from now to delay execution
     * @param unit     the time unit of the delay parameter
     * @param <V>      the type of the callable's result
     * @return a ScheduledFuture that can be used to extract result or cancel
     * @throws RejectedExecutionException if the task cannot be
     *                                    scheduled for execution
     * @throws NullPointerException       if callable is null
     */
    <V> ScheduledFuture<V> schedule(Callable<V> callable,
                                    long delay, TimeUnit unit);

    /**
     * Creates and executes a periodic action that becomes enabled first
     * after the given initial delay, and subsequently with the given
     * period; that is executions will commence after
     * {@code initialDelay} then {@code initialDelay+period}, then
     * {@code initialDelay + 2 * period}, and so on.
     * If any execution of the task
     * encounters an exception, subsequent executions are suppressed.
     * Otherwise, the task will only terminate via cancellation or
     * termination of the executor.  If any execution of this task
     * takes longer than its period, then subsequent executions
     * may start late, but will not concurrently execute.
     *
     * @param command      the task to execute
     * @param initialDelay the time to delay first execution
     * @param period       the period between successive executions
     * @param unit         the time unit of the initialDelay and period parameters
     * @return a ScheduledFuture representing pending completion of
     * the task, and whose {@code get()} method will throw an
     * exception upon cancellation
     * @throws RejectedExecutionException if the task cannot be
     *                                    scheduled for execution
     * @throws NullPointerException       if command is null
     * @throws IllegalArgumentException   if period less than or equal to zero
     */
    ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
                                           long initialDelay,
                                           long period,
                                           TimeUnit unit);

    /**
     * Creates and executes a periodic action that becomes enabled first
     * after the given initial delay, and subsequently with the
     * given delay between the termination of one execution and the
     * commencement of the next.  If any execution of the task
     * encounters an exception, subsequent executions are suppressed.
     * Otherwise, the task will only terminate via cancellation or
     * termination of the executor.
     *
     * @param command      the task to execute
     * @param initialDelay the time to delay first execution
     * @param delay        the delay between the termination of one
     *                     execution and the commencement of the next
     * @param unit         the time unit of the initialDelay and delay parameters
     * @return a ScheduledFuture representing pending completion of
     * the task, and whose {@code get()} method will throw an
     * exception upon cancellation
     * @throws RejectedExecutionException if the task cannot be
     *                                    scheduled for execution
     * @throws NullPointerException       if command is null
     * @throws IllegalArgumentException   if delay less than or equal to zero
     */
    ScheduledFuture<?> scheduleWithFixedDelay(Runnable command,
                                              long initialDelay,
                                              long delay,
                                              TimeUnit unit);

}
