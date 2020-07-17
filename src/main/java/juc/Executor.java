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
 * 执行提交的Runnable任务的对象。
 * 该接口提供了一种将任务提交与每个任务的运行方式（包括线程使用，调度的详细信息）的机制解耦的方法。
 * 通常使用执行程序来代替显式创建线程。
 * 例如，你可以使用下面的方式，而不是对于任务集调成员调用 new Thread(new(RunnableTask())).start()：
 *
 * <pre>
 * Executor executor = <em>anExecutor</em>;
 * executor.execute(new RunnableTask1());
 * executor.execute(new RunnableTask2());
 * ...
 * </pre>
 *
 *v但是，Executor接口并不严格要求执行是异步的。
 * 在最简单的情况下，执行者可以立即在调用者的线程中运行提交的任务：
 *
 *  <pre> {@code
 * class DirectExecutor implements Executor {
 *   public void execute(Runnable r) {
 *     r.run();
 *   }
 * }}</pre>
 *
 * 更典型地，任务在调用者线程之外的某个线程中执行。
 * 下面的Executor为每个任务生成一个新线程。
 *
 *  <pre> {@code
 * class ThreadPerTaskExecutor implements Executor {
 *   public void execute(Runnable r) {
 *     new Thread(r).start();
 *   }
 * }}</pre>
 *
 * 许多Executor实现对调度任务的方式和时间施加了某种限制。
 * 下面的Executor将任务序列化提交到第二个Executor，说明了一个复合Executor。
 *
 *  <pre> {@code
 * class SerialExecutor implements Executor {
 *   final Queue<Runnable> tasks = new ArrayDeque<Runnable>();
 *   final Executor executor;
 *   Runnable active;
 *
 *   SerialExecutor(Executor executor) {
 *     this.executor = executor;
 *   }
 *
 *   public synchronized void execute(final Runnable r) {
 *     tasks.offer(new Runnable() {
 *       public void run() {
 *         try {
 *           r.run();
 *         } finally {
 *           scheduleNext();
 *         }
 *       }
 *     });
 *     if (active == null) {
 *       scheduleNext();
 *     }
 *   }
 *
 *   protected synchronized void scheduleNext() {
 *     if ((active = tasks.poll()) != null) {
 *       executor.execute(active);
 *     }
 *   }
 * }}</pre>
 *
 * 此程序包中提供的Executor实现类实现了ExecutorService接口，它是一个更广泛的接口。
 * ThreadPoolExecutor类提供了可扩展的线程池实现。
 *  Executors类为这些Executor提供了方便的工厂方法。
 *
 * 内存一致性影响：在将Runnable对象提交给Executor之前的线程中的操作happens-before 该Runnable对象的开始执行，
 * 也许该Runnbale是在另一个线程中执行。
 *
 * @since 1.5
 * @author Doug Lea
 * todo 0
 */
public interface Executor {

    /**
     * 在将来的某个时间执行给定的命令。
     * 根据Executor的酌情决定，该命令可以在新线程，池线程或调用线程中执行。
     *
     * @param command the runnable task
     * @throws RejectedExecutionException if this task cannot be
     * accepted for execution
     * @throws NullPointerException if command is null
     */
    void execute(Runnable command);
}
