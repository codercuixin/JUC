package juc.locks;
import java.util.concurrent.TimeUnit;
import java.util.Collection;


/**
 * 可重入互斥{@link Lock}具有与使用{@code synchronized}方法和语句访问的隐式monitor锁相同的基本行为和语义，
 * 但提供了扩展功能。
 *
 * <p>一个{@code ReentrantLock}是被最后一次成功加锁，但是还没有解锁的线程拥有。
 * 当锁不属于其他线程时，调用{@code lock}的线程将返回，并成功获取锁。
 * 如果当前线程已经拥有锁，则该方法将立即返回。
 * 可以使用方法{@link #isHeldByCurrentThread}和{@link #getHoldCount}进行检查。
 *
 * <p>这个类的构造函数接受一个可选的<em>公平性(fairness)</em>参数。
 * 当设置{@code true}时，在争用状态下，锁倾向于授予对最长等待线程的访问权。否则，此锁不保证任何特定的访问顺序。
 * 使用多个线程访问的公平锁的程序比那些使用默认设置的程序可能会显示较低的总体吞吐量(通常要慢得多)，但是在获得锁和保证不会锁饥饿（注：长时间等待的线程始终获取不到锁）方面的时间差异更小。
 * 但是请注意，锁的公平性并不保证线程调度的公平性。因此，使用公平锁的多个线程中的一个可能会连续多次获得它，而其他活动线程没有进展，当前也没有持有锁。
 *
 * <p>还要注意，不定时的{@link #tryLock()}方法不遵循公平性设置。如果锁可用，即使其他线程正在等待，它也会成功。
 *
 * <p>推荐的做法是<em>总是</em>在调用{@code lock}后紧接着一个{@code try}块，最典型的是在一个before/after construction，如:
 *
 *  <pre> {@code
 * class X {
 *   private final ReentrantLock lock = new ReentrantLock();
 *   // ...
 *
 *   public void m() {
 *     lock.lock();  // block until condition holds
 *     try {
 *       // ... method body
 *     } finally {
 *       lock.unlock()
 *     }
 *   }
 * }}</pre>
 *
 * <p>除了实现{@link Lock}接口外，该类还定义了许多{@code public}和{@code protected}方法来检查锁的状态。
 * 其中一些方法仅对检测和监视有用。
 *
 * <p>序列化这个类的行为与内置锁的行为相同:反序列化锁处于解锁状态，无论它在序列化时的状态如何。
 *
 * <p>此锁支持同一线程最多2147483647( 注：由于底层用AQS state来表示锁的持有数，state定义为int，最大值就是2^31 -1)递归加锁。
 *  试图超过此限制将导致{@link Error}从锁定方法抛出。
 *
 * @since 1.5
 * @author Doug Lea
 */
public class ReentrantLock implements Lock, java.io.Serializable {
    private static final long serialVersionUID = 7373984872572414699L;
    /** 提供所有实现机制的同步器*/
    private final Sync sync;

    /**
     * Base of synchronization control for this lock. Subclassed
     * into fair and nonfair versions below. Uses AQS state to
     * represent the number of holds on the lock.
     * 这个锁的同步控制基础。下面有公平和非公平的子类版本。使用AQS state表示锁上的持有数。
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = -5179523762034025860L;

        /**
         * 执行{@link Lock＃lock}。 子类化的主要原因是允许为非公平版本提供快速路径。
         */
        abstract void lock();

        /**
         * 执行不公平的tryLock。 tryAcquire是在子类中实现的，但是都需要对trylock方法进行不公平的尝试。
         */
        final boolean nonfairTryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            //c等于0，表示当前这个时刻还没有线程获取锁
            if (c == 0) {
                //尝试使用CAS设置state
                //如果期望值0等于实际值，就将实际值更新为acquires，获取锁成功，并进入if 内部，设置当前线程为独占线程，然后返回true；
                //否则就返回false
                if (compareAndSetState(0, acquires)) {
                    //设置当前线程为独占线程。
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            // c! = 0 但是当前线程为独占线程，更新state值。
            else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0) // 溢出，则抛出Error。
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);
                return true;
            }
            return false;
        }

        /**
         * 在独占模式中，尝试设置状态（state）反映释放。
         * 用存的state值减去releases。如果剩余值变为0，设置独占线程为null，返回true，也就是独占锁被释放了；否则返回false，也就是独占锁没有被释放。
         */
        protected final boolean tryRelease(int releases) {
            int c = getState() - releases;
            //只有独占线程才可以调用这个方法，如果不是则抛出IllegalMonitorStateException异常。
            if (Thread.currentThread() != getExclusiveOwnerThread())
                throw new IllegalMonitorStateException();
            //free用来表明锁是不是可用了
            boolean free = false;
            if (c == 0) {
                free = true;
                //设置独占线程为null
                setExclusiveOwnerThread(null);
            }
            setState(c);
            return free;
        }

        /**
         * 返回当前线程是否是独占线程。
         */
        protected final boolean isHeldExclusively() {
            // While we must in general read state before owner,
            // we don't need to do so to check if current thread is owner
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        // 从外部类继承的方法

        //返回获取锁的线程，如果没有就返回null。
        final Thread getOwner() {
            return getState() == 0 ? null : getExclusiveOwnerThread();
        }

        //获取锁的持有数。
        final int getHoldCount() {
            return isHeldExclusively() ? getState() : 0;
        }

        final boolean isLocked() {
            return getState() != 0;
        }

        /**
         * 从流中重构实例（即反序列化它）。
         */
        private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            setState(0); // 重置到unlocked状态
        }
    }

    /**
     * 非公平锁的Sync 对象
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = 7316153563782823691L;

        /**
         * 执行加锁。 先尝试一下CAS state，失败了就回退到正常的acquire方法。
         */
        final void lock() {
            //尝试CAS state，如果内存上的实际值等于期望值0，就将内存上的实际值更新为1.
            if (compareAndSetState(0, 1))
                //CAS 成功就设置独占线程为当前线程
                setExclusiveOwnerThread(Thread.currentThread());
            else
                acquire(1);
        }

        protected final boolean tryAcquire(int acquires) {
            return nonfairTryAcquire(acquires);
        }
    }

    /**
     * 公平锁的Sync对象
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = -3000897897090466540L;

        final void lock() {
            acquire(1);
        }

        /**
         * 公平版本的tryAcquire。 除非是递归调用或同步等待队列为空或者是同步等待队列中的第一个，否则不授予访问权限。
         */
        protected final boolean tryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                //如果同步队列上没有前继节点，并且CAS成功，则设置独占线程为当前线程。
                if (!hasQueuedPredecessors() &&
                    compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            //递归调用，即当前线程是独占线程，就尝试更新state。
            else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0)
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);
                return true;
            }
            return false;
        }
    }

    /**
     * 创建一个ReentrantLock的实例，等价于{@code ReentrantLock(false)}.
     */
    public ReentrantLock() {
        sync = new NonfairSync();
    }

    /**
     * 使用给定的公平性策略创建{@code ReentrantLock}的实例。
     *
     * @param fair {@code true} 如果此锁应使用公平的排序策略
     */
    public ReentrantLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
    }

    /**
     * 获取锁。
     * <p>如果没有其他线程持有该锁，则获取该锁并立刻返回，将锁保持计数设置为1。
     * <p>如果当前线程已经持有该锁，则持有计数将增加一，该方法将立即返回。
     * <p>如果该锁由另一个线程持有，则当前线程出于线程调度目的而被禁用，并且在获取该锁（这时将锁持有计数设置为1）之前一直处于休眠状态。
     */
    public void lock() {
        sync.lock();
    }

    /**
     * 获取锁，除非当前线程被中断{@linkplain Thread#interrupt}。
     *
     * <p>获得锁，如果它不是由另一个线程持有，并立即返回，设置锁持有计数为1。
     * <p>如果当前线程已经持有这个锁，那么持有计数增加1，方法立即返回。
     * <p>如果锁是由另一个线程持有，那么当前线程将被禁用线程调度的目的，并处于休眠状态，直到发生以下两种情况之一:
     * <ul>
     * <li>锁被当前线程获取;或
     * <li>其他某个线程中断了当前线程{@linkplain Thread#interrupt}
     * </ul>
     *
     * <p>如果锁被当前线程获取，那么锁持有计数被设置为1。
     *
     * <p>如果当前线程:
     * <ul>
     * <li>在进入此方法时已将其中断状态设置;或
     * <li>获取锁被中断{@linkplain Thread#interrupt}
     * </ul>
     * 那么{@link InterruptedException}就会被抛出，并清除当前线程的中断状态。
     *
     *
     * <p>在这个实现中，因为这个方法是一个显式的中断点，所以优先响应中断，而不是正常的或可重入的获取锁。
     *
     * @throws InterruptedException 如果当前线程被中断
     */
    public void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1);
    }

    /**
     * 仅当在调用方法时锁还没有被其他线程持有时，才获取锁。
     *
     * <p>获得锁，如果它没有被其他线程持有，并立即返回值{@code true}，设置锁持有计数为1。
     * 即使当这个锁被设置为使用公平的排序策略时，如果锁是可用的，无论其他线程是否正在等待锁，当前调用{@code tryLock()}的线程 <em>将</em>立即获得锁。
     * 这个"barging"行为在某些情况下是有用的，即使它破坏了公平。
     * 如果你想遵循为这个锁的公平性设置，那么使用{@link #tryLock(long, TimeUnit) tryLock(0, TimeUnit.SECONDS)}，这几乎是等价的(它也检测到中断)。
     *
     * <p>如果当前线程已经持有这个锁，那么持有计数增加1，方法返回{@code true}。
     *
     * <p>如果锁被另一个线程持有，那么这个方法将立即返回值{@code false}。
     *
     * @return 如果锁是可用的并且被当前线程获取，或者锁已经被当前线程持有，则返回true；否则返回false
     */
    public boolean tryLock() {
        return sync.nonfairTryAcquire(1);
    }

    /**
     * 如果在给定的等待时间内锁没有被另一个线程持有，并且当前线程没有被中断{@linkplain Thread#interrupt}，则获取锁。
     *
     * 获得锁，如果它没有被另一个线程持有，并立即返回值{@code true}，设置锁持有计数为1。
     * 如果这个锁被设置为使用公平的排序策略，那么如果其他线程正在等待这个锁，那么可用的锁<em>将不会</em>被当前线程获取。
     * 这与{@link #tryLock()}方法形成对比。
     * 如果你想要一个定时的{@code tryLock}，它允许对一个公平的锁进行操作，那么把定时的和不定时两种形式合在一起:
     *  <pre> {@code
     * if (lock.tryLock() ||
     *     lock.tryLock(timeout, unit)) {
     *   ...
     * }}</pre>
     *
     * <p>如果当前线程已经持有该锁，那么持有数将增加1，方法返回true。
     *
     * <p>如果锁是由另一个线程持有，那么当前线程就会出于线程调度的目的而被禁用，并处于休眠状态，直到发生以下三种情况之一:
     * <ul>
     * <li>锁被当前线程获取;或
     * <li>其他一些线程中断当前线程{@linkplain Thread#interrupt};或
     * <li>指定的等待时间已经过了
     * </ul>
     *
     * <p>如果获取了锁，则返回true值，并将锁持有数设置为1。
     * <p>如果当前线程:
     * <ul>
     * <li>在进入此方法时已设置其中断状态;或
     * <li>在获取锁时中断{@linkplain Thread#interrupt}，
     * 然后抛出InterruptedException，并清除当前线程的中断状态。
     * </ul>
     *
     * <p>如果指定的等待时间过期，则返回false值。如果时间小于或等于0，则该方法根本不会等待。
     *
     * <p>在这个实现中，由于这个方法是一个显式的中断点，所以优先响应中断，而不是正常的或可重入的获取锁，或者报告等待时间的流逝。
     *
     * @param timeout  等待锁的时间
     * @param unit  参数timeout的时间单位
     * @return 如果锁是空闲的并且被当前线程获取，或者锁已经被当前线程持有，则返回true;
     *         如果在锁可能被获取之前，等待时间已经过了，则返回false。
     * @throws InterruptedException 如果当前线程被中断
     * @throws NullPointerException 如果时间单位为null
     */
    public boolean tryLock(long timeout, TimeUnit unit)
            throws InterruptedException {
        return sync.tryAcquireNanos(1, unit.toNanos(timeout));
    }

    /**
     * 试图释放此锁。
     *
     * 如果当前线程是这个锁的持有者，那么持有计数将递减。如果持有计数现在为零，则锁就被释放了。
     * 如果当前线程不是这个锁的持有者，那么抛出IllegalMonitorStateException。
     *
     * @throws IllegalMonitorStateException 如果当前线程不是这个锁的持有者
     */
    public void unlock() {
        sync.release(1);
    }

    /**
     *
     * <ul>
     *
     * <li>If this lock is not held when any of the {@link Condition}
     * {@linkplain Condition#await() waiting} or {@linkplain
     * Condition#signal signalling} methods are called, then an {@link
     * IllegalMonitorStateException} is thrown.
     *
     * <li>When the condition {@linkplain Condition#await() waiting}
     * methods are called the lock is released and, before they
     * return, the lock is reacquired and the lock hold count restored
     * to what it was when the method was called.
     *
     * <li>If a thread is {@linkplain Thread#interrupt interrupted}
     * while waiting then the wait will terminate, an {@link
     * InterruptedException} will be thrown, and the thread's
     * interrupted status will be cleared.
     *
     * <li> Waiting threads are signalled in FIFO order.
     *
     * <li>The ordering of lock reacquisition for threads returning
     * from waiting methods is the same as for threads initially
     * acquiring the lock, which is in the default case not specified,
     * but for <em>fair</em> locks favors those threads that have been
     * waiting the longest.
     *
     * </ul>
     * 返回与此Lock实例一起使用的Condition实例。
     *
     * <p>当与内置monitor锁一起使用时，返回的Condition实例支持与Object监视器方法（wait，notify和notifyAll）相同的用法。
     *
     * <ul>
     * <li>如果在调用任何Condition 等待{@linkplain Condition#await()}方法 或通知方法{@linkplain Condition#signal }时，这个锁没有被持有，那么IllegalMonitorStateException会被抛出。
     * <li>Condition的一些等待方法被调用时，该锁被释放了，并在这些等待方法返回之前，锁被重新获取了，并将锁保持计数恢复到调用该方法时的状态。
     * <li>如果线程在等待时被中断，则等待将终止，将抛出InterruptedException，并清除线程的中断状态。
     * <li>等待线程以FIFO顺序被通知。
     * <li>从等待方法返回的线程的锁重新获取顺序与最初获取锁的线程的顺序相同（默认情况下没有指定这个），但对于公平锁，优先使用等待时间最长的线程。
     *</ul>
     * @return the Condition object
     */
    public Condition newCondition() {
        return sync.newCondition();
    }

    /**
     * Queries the number of holds on this lock by the current thread.
     *
     * <p>A thread has a hold on a lock for each lock action that is not
     * matched by an unlock action.
     *
     * <p>The hold count information is typically only used for testing and
     * debugging purposes. For example, if a certain section of code should
     * not be entered with the lock already held then we can assert that
     * fact:
     * 查询当前线程对该锁的持有数。
     *
     * <p>对于每个未与unlock动作匹配的lock动作，线程都会有一个对于该锁的持有数。
     *
     * <p>持有数信息通常仅用于测试和调试目的。 例如，如果在锁已经被持有的状态下，不应该进入特定的代码段，则可以断言以下事实：
     *
     *  <pre> {@code
     * class X {
     *   ReentrantLock lock = new ReentrantLock();
     *   // ...
     *   public void m() {
     *     assert lock.getHoldCount() == 0;
     *     lock.lock();
     *     try {
     *       // ... method body
     *     } finally {
     *       lock.unlock();
     *     }
     *   }
     * }}</pre>
     *
     * @return 返回当前线程对于该锁的持有数，如果当前先吃不持有该锁，则返回0.
     */
    public int getHoldCount() {
        return sync.getHoldCount();
    }

    /**
     * Queries if this lock is held by the current thread.
     *
     * <p>Analogous to the {@link Thread#holdsLock(Object)} method for
     * built-in monitor locks, this method is typically used for
     * debugging and testing. For example, a method that should only be
     * called while a lock is held can assert that this is the case:
     * 查询此锁是否由当前线程持有。
     *
     * <p>与内置monitor锁的{@link Thread#holdsLock(Object)}方法类似，此方法通常用于调试和测试。
     * 例如，仅在持有锁的情况下,方法才应被调用，可以断言是下面这种情况：
     *  <pre> {@code
     * class X {
     *   ReentrantLock lock = new ReentrantLock();
     *   // ...
     *
     *   public void m() {
     *       assert lock.isHeldByCurrentThread();
     *       // ... method body
     *   }
     * }}</pre>
     *
     * <p>它还可以用于确保以不可重入方式来使用可重入锁，例如：
     *  <pre> {@code
     * class X {
     *   ReentrantLock lock = new ReentrantLock();
     *   // ...
     *
     *   public void m() {
     *       assert !lock.isHeldByCurrentThread();
     *       lock.lock();
     *       try {
     *           // ... method body
     *       } finally {
     *           lock.unlock();
     *       }
     *   }
     * }}</pre>
     *
     * @return {@code true} 如果当前线程持有该锁
     *         {@code false} 否则
     */
    public boolean isHeldByCurrentThread() {
        return sync.isHeldExclusively();
    }

    /**
     * 查询此锁是否由任何线程持有。 此方法设计用于监视系统状态，而不用于同步控制。
     *
     * @return {@code true} 如果当前线程持有该锁
     *         {@code false} 否则
     */
    public boolean isLocked() {
        return sync.isLocked();
    }

    /**
     * 如果此锁的公平性设置为true，则返回{@code true}。
     *
     * @return {@code true} 如果此锁的公平性设置为true
     */
    public final boolean isFair() {
        return sync instanceof FairSync;
    }

    /**
     * 返回当前拥有此锁的线程；如果该锁没有被占有，则返回null。
     * 当非所有者线程调用此方法时，返回值反映当前锁定状态的最大努力近似值（best-effort approximation）。
     * 例如，即使有线程试图获取锁，但还没有获取到，那么所有者可能暂时为null。
     * 设计此方法是为了便于构造提供更广泛的锁监视功能的子类。
     *
     * @return the owner, or {@code null} if not owned
     */
    protected Thread getOwner() {
        return sync.getOwner();
    }

    /**
     * 查询是否有线程正在等待获取此锁。
     * 请注意，由于取消可能随时发生，因此返回true不能保证任何其他线程都将获得此锁。
     * 此方法主要设计用于监视系统状态。
     *
     * @return {@code true} 如果有线程正在等待获取此锁。
     */
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * 查询给定线程是否正在等待获取此锁。
     * 请注意，由于取消可能随时发生，因此返回true不能保证此线程将会获得此锁。
     * 此方法主要设计用于监视系统状态。
     *
     * @param thread the thread
     * @return {@code true} 如果给定线程在排队等待该锁。
     * @throws NullPointerException 如果线程为null
     */
    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    /**
     * 返回等待获取此锁的线程数的估计值。
     * 该值只是一个估计值，因为在此方法遍历内部数据结构时，线程数可能会动态变化。
     * 此方法设计用于监视系统状态，而不用于同步控制。
     *
     * @return 等待此锁的估计线程数
     */
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     * 返回一个包含可能正在等待获取此锁的线程的集合。
     * 因为实际的线程集在构造此结果时可能会动态更改，所以返回的集合只是尽力而为的估计。
     * 返回的集合的元素没有特定的顺序。 设计此方法是为了便于构造子类，以提供更广泛的监视功能。
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    /**
     * 查询是否有任何线程正在等待与此锁关联的给定条件。
     * 请注意，因为超时和中断可能随时发生，所以返回true并不保证将来的signal（通知）会唤醒任何线程。
     * 此方法主要设计用于监视系统状态。
     *
     * @param condition 条件
     * @return {@code true} 如果有等待线程
     * @throws IllegalMonitorStateException 如果锁没有被持有
     * @throws IllegalArgumentException 如果给定的条件没有与此锁关联。
     * @throws NullPointerException 如果条件为null
     */
    public boolean hasWaiters(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * 返回等待与此锁关联的给定条件的线程数的估计值。
     * 请注意，由于超时和中断可能随时发生，因此估算值仅用作实际侍者数的上限。
     * 此方法设计用于监视系统状态，而不用于同步控制。
     *
     * @param condition 条件
     * @return 估计的等待线程数
     * @throws IllegalMonitorStateException 如果锁没有被持有
     * @throws IllegalArgumentException 如果给定的条件没有与此锁关联。
     * @throws NullPointerException 如果条件为null
     */
    public int getWaitQueueLength(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * 返回一个包含可能正在等待与此锁相关的给定条件的线程的集合。
     * 因为实际的线程集在构造此结果时可能会动态更改，所以返回的集合只是尽力而为的估计。
     * 返回的集合的元素没有特定的顺序。
     * 设计此方法是为了便于构造提供更广泛的状态监视工具的子类。
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException 如果锁没有被持有
     * @throws IllegalArgumentException 如果给定的条件没有与此锁关联。
     * @throws NullPointerException 如果条件为null
     */
    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * 返回标识此锁及其锁状态的字符串。
     * The state, in brackets, includes either the String {@code "Unlocked"}
     * or the String {@code "Locked by"} followed by the
     * {@linkplain Thread#getName name} of the owning thread.
     *
     * @return a string identifying this lock, as well as its lock state
     */
    public String toString() {
        Thread o = sync.getOwner();
        return super.toString() + ((o == null) ?
                                   "[Unlocked]" :
                                   "[Locked by thread " + o.getName() + "]");
    }
}
