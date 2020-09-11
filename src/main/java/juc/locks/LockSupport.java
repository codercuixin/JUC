package juc.locks;

import sun.misc2.Unsafe;
import unsafeTest.GetUnsafeFromReflect;

/**
 * 创建锁和其他同步类的基本线程阻塞原语。
 *
 * <p>这个类与使用它的每个线程关联一个许可证(在{@link java.util.concurrent.Semaphore}信号量类的意义上)。
 * 如果获得许可证，对{@code park}的调用将立即返回，并在此过程中消耗掉它;否则它可能阻塞。
 * 如果许可证目前不可用，则调用{@code unpark}将使许可证可用。(与Semaphore信号量不同，许可证不会累积。最多只有一个。)
 *
 * <p>方法{@code park}和{@code unpark}提供了有效的阻塞和非阻塞线程的方法，
 * 这些方法不会遇到导致弃用方法{@code Thread.suspend}和{code Thread.resume}的问题，
 * 即一个线程调用{@code park}和另一个线程调用{unpark}, 两者之间的竞争由于许可证将保持活性。
 * 另外，如果调用者的线程被中断，{@code park}将返回，并且支持超时版本的park方法。
 * {@code park}法也可以在任何其他时间返回，不需要任何原因，所以通常必须在返回时在循环中重新检查条件。
 * 在这个意义上，{@code park}作为一个“繁忙等待”的优化，不浪费太多的时间旋转，但必须配合{@code unpark}来生效。
 *
 * <p>{@code park}的三种形式也都支持{@code blocker}对象参数。
 * 这个blocker对象在线程被阻塞时进行记录，以允许监视和诊断工具识别线程被阻塞的原因。
 * (这些工具可能会使用{@link #getBlocker(Thread)}方法访问blockers。)
 * 强烈建议使用这些形式而不是没有此参数的原始形式。
 * 在锁实现中作为{@code blocker}提供的一般参数是{@code this}。
 *
 * <p>这些方法被设计为用于创建更高级别的同步实用程序的工具，
 * 但它们本身对大多数并发控制应用程序并无用处。{@code park}方法是被设计在下面的形式中使用:
 *
 * <pre> {@code
 * while (!canProceed()) { ... LockSupport.park(this); }}</pre>
 * <p>
 * 在调用{@code park}方法之前，无法执行{@code canProceed}或任何其他操作时,从而导致locking或者blocking。
 * 因为每个线程仅关联一个许可证，所以{@code park}的任何中间用途都可能干扰其预期的效果。
 *
 * <p><b>样本用法</b> 这是先进先出的不可重入锁类的示意图：
 * <pre> {@code
 * class FIFOMutex {
 *   private final AtomicBoolean locked = new AtomicBoolean(false);
 *   private final Queue<Thread> waiters
 *     = new ConcurrentLinkedQueue<Thread>();
 *
 *   public void lock() {
 *     boolean wasInterrupted = false;
 *     Thread current = Thread.currentThread();
 *     waiters.add(current);
 *
 *     // Block while not first in queue or cannot acquire lock
 *     while (waiters.peek() != current ||
 *            !locked.compareAndSet(false, true)) {
 *       LockSupport.park(this);
 *       if (Thread.interrupted()) // ignore interrupts while waiting
 *         wasInterrupted = true;
 *     }
 *
 *     waiters.remove();
 *     if (wasInterrupted)          // reassert interrupt status on exit
 *       current.interrupt();
 *   }
 *
 *   public void unlock() {
 *     locked.set(false);
 *     LockSupport.unpark(waiters.peek());
 *   }
 * }}</pre>
 */
public class LockSupport {
    // Hotspot implementation via intrinsics API
    private static final Unsafe UNSAFE;
    private static final long parkBlockerOffset;
    private static final long SEED;
    private static final long PROBE;
    private static final long SECONDARY;

    static {
        try {
            UNSAFE = GetUnsafeFromReflect.getUnsafe();
            Class<?> tk = Thread.class;
            parkBlockerOffset = UNSAFE.objectFieldOffset
                    (tk.getDeclaredField("parkBlocker"));
            SEED = UNSAFE.objectFieldOffset
                    (tk.getDeclaredField("threadLocalRandomSeed"));
            PROBE = UNSAFE.objectFieldOffset
                    (tk.getDeclaredField("threadLocalRandomProbe"));
            SECONDARY = UNSAFE.objectFieldOffset
                    (tk.getDeclaredField("threadLocalRandomSecondarySeed"));
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    private LockSupport() {
    } // Cannot be instantiated.

    private static void setBlocker(Thread t, Object arg) {
        // Even though volatile, hotspot doesn't need a write barrier here.
        UNSAFE.putObject(t, parkBlockerOffset, arg);
    }

    /**
     * 如果许可还不可用，就使用对于给定线程可用。
     * 如果线程在{@code park}上被阻塞，那么它将不阻塞(unblock)。
     * 否则，它的下一个调用{@code park}保证不会阻塞。
     * 如果没有启动给定的线程，则不能保证此操作有任何效果。
     *
     * @param thread thread-要不阻塞（unpark）的线程，
     *               或者为null，在这种情况下，此操作无效
     */
    public static void unpark(Thread thread) {
        if (thread != null)
            //取消阻塞由于调用park方法阻塞的给定线程，或者，如果线程未阻塞，则导致随后的调用park不阻塞。
            UNSAFE.unpark(thread);
    }

    /**
     * 为线程调度的目的禁用当前线程，除非许可证可用。
     *
     * <p>如果许可证可用，则使用许可证并立即返回;
     * 否则，当前线程将出于线程调度的目的而被禁用，并处于休眠状态，直到发生以下三种情况之一:
     * <ul>
     * <li>其他一些线程以当前线程为目标调用{@code unpark};或
     * <li>其他一些线程中断{@linkplain Thread#interrupt()}当前线程;或
     * <li>虚假的调用(也就是说，没有任何理由)返回。
     * </ul>
     *
     * <p>此方法<em>不</em>报告是哪些原因导致该方法返回。调用者应该首先重新检查导致线程park的条件。
     * 例如，调用者检查线程返回时的中断状态。
     *
     * @param blocker——负责此线程park(休眠）的同步对象
     * @since 1.6
     */
    public static void park(Object blocker) {
        Thread t = Thread.currentThread();
        //设置当前线程t的blocker为参数blocker
        setBlocker(t, blocker);
        //阻塞当前线程，在发生与之平衡的unpark或已经发生与之平衡的unpark，或线程被中断，或设置的时间过了
        // (设置的时间过了包括以下几种：或如果不是绝对时间且时间不为零，且给定时间的纳秒数已经过了，或如果是绝对时间，从Epoch开始的截止时间已经过去）。
        UNSAFE.park(false, 0L);
        //设置当前线程t的blocker为null
        setBlocker(t, null);
    }

    /**
     * 除非许可证可用，否则在指定的等待时间内，禁止当前线程用于线程调度。
     *
     * <p>如果许可证可用，则使用许可证并立即返回;
     * 否则，当前线程将出于线程调度的目的而被禁用，并处于休眠状态，直到发生以下四种情况之一:
     * <ul>
     * <li>其他一些线程以当前线程为目标调用{@code unpark};或
     * <li>其他一些线程中断{@linkplain Thread#interrupt()}当前线程;或
     * <li>指定的等候时间已过;或
     * <li>虚假的调用(也就是说，没有任何理由)返回。
     * </ul>
     *
     * <p>此方法<em>不</em>报告是哪些原因导致该方法返回。调用者应该首先重新检查导致线程park的条件。
     *  例如，调用者检查线程返回时的中断状态。

     * @param blocker 负责此线程休眠park的同步对象
     * @param nanos 等待的最大纳秒数
     * @since 1.6
     */
    public static void parkNanos(Object blocker, long nanos) {
        if (nanos > 0) {
            Thread t = Thread.currentThread();
            setBlocker(t, blocker);
            UNSAFE.park(false, nanos);
            setBlocker(t, null);
        }
    }

    /**
     * 为了线程调度的目的，在指定的截止日期之前禁用当前线程，除非许可证可用。
     *
     * <p>如果许可证可用，则使用许可证并立即返回;
     * 否则，当前线程将出于线程调度的目的而被禁用，并处于休眠状态，直到发生以下四种情况之一:
     * <ul>
     * <li>其他一些线程以当前线程为目标调用{@code unpark};或
     * <li>其他一些线程中断{@linkplain Thread#interrupt()}当前线程;或
     * <li>指定的等候时间已过;或
     * <li>虚假的调用(也就是说，没有任何理由)返回。
     * </ul>
     *
     * <p>此方法<em>不</em>报告是哪些原因导致该方法返回。调用者应该首先重新检查导致线程park的条件。
     *  例如，调用者检查线程返回时的中断状态。
     *
     * @param blocker 负责此线程休眠park的同步对象
     * @param deadline 等待直到绝对时间到来，从Epoch开始的毫秒数，
     * @since 1.6
     */
    public static void parkUntil(Object blocker, long deadline) {
        Thread t = Thread.currentThread();
        setBlocker(t, blocker);
        UNSAFE.park(true, deadline);
        setBlocker(t, null);
    }

    /**
     * 返回提供给park方法的最近一次调用的blocker对象，该方法尚未被解除阻塞，
     * 如果未被阻塞，则返回null。
     * 返回的值只是一个瞬时快照——线程可能在不同的blocker对象上unblock或block。
     *
     * @param t the thread
     * @return the blocker
     * @throws NullPointerException if argument is null
     * @since 1.6
     */
    public static Object getBlocker(Thread t) {
        if (t == null)
            throw new NullPointerException();
        return UNSAFE.getObjectVolatile(t, parkBlockerOffset);
    }

    /**
     * 注释基本类似于{@link #park(Object)}
     * 除非有许可证可用，否则出于线程调度目的禁用当前线程。
     *
     * <p>如果许可证可用，则使用许可证并立即返回;
     * 否则，当前线程将出于线程调度的目的而被禁用，并处于休眠状态，直到发生以下三种情况之一:
     * <ul>
     * <li>其他一些线程以当前线程为目标调用{@code unpark};或
     * <li>其他一些线程中断{@linkplain Thread#interrupt()}当前线程;或
     * <li>虚假的调用(也就是说，没有任何理由)返回。
     * </ul>
     *
     * <p>此方法<em>不</em>报告是哪些原因导致该方法返回。调用者应该首先重新检查导致线程park的条件。
     * 例如，调用者检查线程返回时的中断状态
     */
    public static void park() {
        UNSAFE.park(false, 0L);
    }

    /**
     * 除非许可证可用，否则在指定的等待时间内，禁止当前线程用于线程调度。
     *
     * <p>如果许可证可用，则使用许可证并立即返回;
     * 否则，当前线程将出于线程调度的目的而被禁用，并处于休眠状态，直到发生以下四种情况之一:
     * <ul>
     * <li>其他一些线程以当前线程为目标调用{@code unpark};或
     * <li>其他一些线程中断{@linkplain Thread#interrupt()}当前线程;或
     * <li>指定的等候时间已过;或
     * <li>虚假的调用(也就是说，没有任何理由)返回。
     * </ul>
     *
     * <p>此方法<em>不</em>报告是哪些原因导致该方法返回。调用者应该首先重新检查导致线程park的条件。
     * 例如，调用者检查线程返回时的中断状态。
     * @param nanos 最大等待的纳秒数
     */
    public static void parkNanos(long nanos) {
        if (nanos > 0)
            UNSAFE.park(false, nanos);
    }

    /**
     * 注释基本同{@link #parkUntil(Object, long)}
     *
     * 为了线程调度的目的，在指定的截止日期之前禁用当前线程，除非许可证可用。
     *
     * <p>如果许可证可用，则使用许可证并立即返回;
     * 否则，当前线程将出于线程调度的目的而被禁用，并处于休眠状态，直到发生以下四种情况之一:
     * <ul>
     * <li>其他一些线程以当前线程为目标调用{@code unpark};或
     * <li>其他一些线程中断{@linkplain Thread#interrupt()}当前线程;或
     * <li>指定的等候时间已过;或
     * <li>虚假的调用(也就是说，没有任何理由)返回。
     * </ul>
     *
     * <p>此方法<em>不</em>报告是哪些原因导致该方法返回。调用者应该首先重新检查导致线程park的条件。
     *  例如，调用者检查线程返回时的中断状态。
     *
     * @param deadline 等待直到绝对时间到来，从Epoch开始的毫秒数，
     * @since 1.6
     */

    public static void parkUntil(long deadline) {
        UNSAFE.park(true, deadline);
    }

    /**
     * 返回伪随机初始化或更新的辅助种子。由于包访问限制，从ThreadLocalRandom复制。
     */
    static final int nextSecondarySeed() {
        int r;
        Thread t = Thread.currentThread();
        if ((r = UNSAFE.getInt(t, SECONDARY)) != 0) {
            r ^= r << 13;   // xorshift
            r ^= r >>> 17;
            r ^= r << 5;
        } else if ((r = java.util.concurrent.ThreadLocalRandom.current().nextInt()) == 0)
            r = 1; // avoid zero
        UNSAFE.putInt(t, SECONDARY, r);
        return r;
    }

}
