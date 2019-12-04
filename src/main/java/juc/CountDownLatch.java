package juc;
import juc.locks.AbstractQueuedSynchronizer;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

/**
 * 一种同步帮助，它允许一个或多个线程等待，直到在其他线程中执行的一组操作完成为止。
 * <p>CountDownLatch用给定的计数初始化。await方法将阻塞，直到由于{@link #countDown}方法的调用，当前计数达到零为止，
 * 此后所有等待的线程将被释放，并且此后任何对await的调用将立即返回。
 * 这是一种一次性现象-无法重置计数。如果需要用于重置计数的版本，请考虑使用{@link CyclicBarrier}。
 *
 * <p>CountDownLatch是一种多功能的同步工具，可以用于多种目的。
 * 初始化为1的CountDownLatch用作简单的开/关闩锁或门：所有调用await{@link #await}的线程在门处等待，直到被调用countDown（）的线程打开为止。
 * 初始化为N的CountDownLatch可以用于使一个线程等待，直到N个线程完成某个动作或某个动作已经完成N次。
 *
 * <p>CountDownLatch的一个有用属性是，它不需要调用countDown的线程在继续操作之前等待计数达到零，
 *  它只是防止任何调用{@link #await}的线程继续操作，直到所有线程都可以通过。
 *
 * <p><b>用法示例：</b>这是一对类，其中一组工作线程使用两个倒计时锁存器：
 * <ul>
 * <li>第一个是启动信号，可防止任何工人继续前进，直到驾驶员为他们做好准备为止。
 * <li>第二个是完成信号，允许驾驶员等到所有工人都完成为止。
 * </ul>
 *
 *  <pre> {@code
 * class Driver { // ...
 *   void main() throws InterruptedException {
 *     CountDownLatch startSignal = new CountDownLatch(1);
 *     CountDownLatch doneSignal = new CountDownLatch(N);
 *
 *     for (int i = 0; i < N; ++i) // create and start threads
 *       new Thread(new Worker(startSignal, doneSignal)).start();
 *
 *     doSomethingElse();            // don't let run yet
 *     startSignal.countDown();      // let all threads proceed
 *     doSomethingElse();
 *     doneSignal.await();           // wait for all to finish
 *   }
 * }
 *
 * class Worker implements Runnable {
 *   private final CountDownLatch startSignal;
 *   private final CountDownLatch doneSignal;
 *   Worker(CountDownLatch startSignal, CountDownLatch doneSignal) {
 *     this.startSignal = startSignal;
 *     this.doneSignal = doneSignal;
 *   }
 *   public void run() {
 *     try {
 *       startSignal.await();
 *       doWork();
 *       doneSignal.countDown();
 *     } catch (InterruptedException ex) {} // return;
 *   }
 *
 *   void doWork() { ... }
 * }}</pre>
 *
 * 另一个典型用法是将问题分为N个部分，用Runnable描述每个部分，该Runnable执行该部分并在闩锁上递减计数，然后将所有Runnable入队到Executor。
 * 当所有子部分都完成时，协调线程将能够通过等待。 （当线程必须以此方式反复递减计数时，请使用{@link CyclicBarrier}。）
 *
 *  <pre> {@code
 * class Driver2 { // ...
 *   void main() throws InterruptedException {
 *     CountDownLatch doneSignal = new CountDownLatch(N);
 *     Executor e = ...
 *
 *     for (int i = 0; i < N; ++i) // create and start threads
 *       e.execute(new WorkerRunnable(doneSignal, i));
 *
 *     doneSignal.await();           // wait for all to finish
 *   }
 * }
 *
 * class WorkerRunnable implements Runnable {
 *   private final CountDownLatch doneSignal;
 *   private final int i;
 *   WorkerRunnable(CountDownLatch doneSignal, int i) {
 *     this.doneSignal = doneSignal;
 *     this.i = i;
 *   }
 *   public void run() {
 *     try {
 *       doWork(i);
 *       doneSignal.countDown();
 *     } catch (InterruptedException ex) {} // return;
 *   }
 *
 *   void doWork() { ... }
 * }}</pre>
 *
 * <p>Memory consistency effects: Until the count reaches
 * zero, actions in a thread prior to calling
 * {@code countDown()}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * actions following a successful return from a corresponding
 * {@code await()} in another thread.
 * 内存一致性影响：在计数达到零之前，在调用countDown（）之前线程中的操作 happens-before从另一个线程中的相应await（）成功返回之后的操作。
 * @since 1.5
 * @author Doug Lea
 */
public class CountDownLatch {
    /**
     * CountDownLatch的同步控制。
     * 使用AQS中的state表示计数。
     */
    private static final class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 4982264981922014374L;

        Sync(int count) {
            setState(count);
        }

        int getCount() {
            return getState();
        }

        /**
         * (下面的方法注释来自父类AQS）。
         * 尝试以共享模式获取。该方法应该查询对象的状态是否允许在共享模式下获取它，如果允许，则应该获取它。
         *
         * <p>此方法总是由执行获取的线程调用。如果此方法报告失败，则获取方法可能会对线程进行排队(如果它还没有排队)，
         * 直到通过其他线程的发出释放信号。
         *
         * @param acquires 获取参数。这个值总是被传递给一个获取方法，或者是在进入一个条件wait时被保存。该值是未解释的，可以表示你喜欢的任何内容。
         * @return 返回负值表示失败;
         *         返回0表示，这次共享模式下的获取成功，但是后续的共享模式获取都不会成功;
         *         返回正数表示，这次共享模式下获取成功，并且随后的共享模式获取也可能成功，那么在这种情况下，后续的等待线程必须检查可用性。
         *         (支持三种不同的返回值，使此方法可以用于仅在某些情况下才进行获取的上下文中。)
         *         成功之后，这个对象就获得了。
         * @throws IllegalMonitorStateException  如果获取将使该同步器处于非法状态。必须以一致的方式抛出此异常，以便同步工作正常。
         */
        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;
        }

        /**
         *  (下面的方法注释来自父类AQS）。
         * 尝试设置状态来反映共享模式下的释放。
         *
         * <p>这个方法总是被执行release的线程调用。
         * @param releases 获取参数。这个值总是被传递给一个获取方法，或者是在进入一个条件wait时被保存。该值是未解释的，可以表示你喜欢的任何内容。
         * @return 如果共享模式下的释放可以允许（共享的或独占的）等待的获取成功，就返回true，否则返回false。
         * @throws IllegalMonitorStateException  如果获取将使该同步器处于非法状态。必须以一致的方式抛出此异常，以便同步工作正常。
         */
        protected boolean tryReleaseShared(int releases) {
            // 递减计数； 过渡到零时发出信号(也就是第一次减少到0时，本方法才返回true，其他情况都返回false)
            for (;;) {
                int c = getState();
                //如果已经等于0了，就直接返回false。
                if (c == 0)
                    return false;
                int nextc = c-1;
                //尝试CAS将satate的值减1.
                if (compareAndSetState(c, nextc))
                    //如果CAS成功的话就返回 nextc==0,也就是nextc为0，返回true；否则，返回false。
                    return nextc == 0;
            }
        }
    }

    private final Sync sync;

    /**
     * 构造一个用给定计数初始化的{@code CountDownLatch}。
     *
     * @param count 线程可以通过{@link #await}之前必须调用{@link #countDown}的次数
     * @throws IllegalArgumentException 如果 {@code count}是负数
     */
    public CountDownLatch(int count) {
        if (count < 0) throw new IllegalArgumentException("count < 0");
        this.sync = new Sync(count);
    }

    /**
     * 使当前线程等待，直到锁存器递减至零为止，除非该线程被中断{@linkplain Thread#interrupt} 。
     *
     * <p>如果当前计数为零，则此方法立即返回。
     * <p>如果当前计数大于零，则出于线程调度目的，当前线程将被禁用，并且在发生以下两种情况之一之前，它处于休眠状态：
     * <ul>
     * <li>由于countDown（）方法的调用，计数达到零;或
     * <li>其他一些线程中断当前线程。
     * </ul>
     *
     * <p>如果当前线程：
     * <ul>
     * <li>在进入此方法时已设置其中断状态；或
     * <li>在等待期间被打断{@linkplain Thread#interrupt},
     * </ul>
     * 那么就抛出InterruptedException并清除当前线程的中断状态。
     *
     * @throws InterruptedException 如果在等待期间被中断
     */
    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }

    /**
     *
     * 导致当前线程等待，直到锁存器减少到零为止，除非该线程被中断或经过了指定的等待时间。
     *
     * <p>如果当前计数为零，则此方法将立即返回true值。
     * <p>如果当前计数大于零，则出于线程调度目的，当前线程将被禁用，并且在发生以下三种情况之一之前，它处于休眠状态：
     * <ul>
     * <li>由于countDown（）方法的调用，计数达到零;或
     * <li>其他一些线程中断当前线程;或
     * <li>经过指定的等待时间。
     * </ul>
     *
     * <p>如果计数达到零，则该方法返回值true。
     *
     * <p>如果当前线程：
     * <ul>
     * <li>在进入此方法时已设置其中断状态；或
     * <li>在等待期间被打断{@linkplain Thread#interrupt},
     * </ul>
     * 那么就抛出InterruptedException并清除当前线程的中断状态。
     * <p>如果经过了指定的等待时间，则返回值false。 如果时间小于或等于零，则该方法将根本不等待。
     *
     * @param timeout 最大等待时间
     * @param unit timeout参数的时间单位
     * @return {@code true} 如果count变为0， 并且返回{@code false}，如果在count变为0之前，等待时间已经过了。
     * @throws InterruptedException 如果在等待期间被中断
     */
    public boolean await(long timeout, TimeUnit unit)
        throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    /**
     * 减少锁存器的计数，如果计数达到零，则释放所有等待线程。
     *
     * <p>如果当前计数大于零，则将其递减。 如果新计数为零，则将重新启用所有等待线程以进行线程调度。
     * <p>如果当前计数等于零，那么什么也不会发生。
     */
    public void countDown() {
        sync.releaseShared(1);
    }

    /**
     * 放回当前计数
     *
     * <p>此方法通常用于调试和测试目的。
     *
     * @return 当前计数
     */
    public long getCount() {
        return sync.getCount();
    }

    /**
     * Returns a string identifying this latch, as well as its state.
     * The state, in brackets, includes the String {@code "Count ="}
     * followed by the current count.
     *
     * @return a string identifying this latch, as well as its state
     */
    public String toString() {
        return super.toString() + "[Count = " + sync.getCount() + "]";
    }
}
