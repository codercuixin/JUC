package juc.locks;

import unsafeTest.GetUnsafeFromReflect;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * ReadWriteLock的实现，支持与ReentrantLock相似的语义。
 * <p>此类具有以下属性：
 * <ul>
 * <li><b>获取锁顺序</b>
 * <p>此类不对锁定访问强加读线程或写线程偏好顺序。但是，它确实支持可选的公平性政策。
 *
 * <dl>
 * <dt><b><i>非公平模式（默认）</i></b>
 * <dd>当构造为非公平(默认)时，读写锁的条目顺序是未指定的，这取决于可重入性约束。
 * 持续争用的非公平锁可能无限期地延迟一个或多个读写线程，但通常具有比公平锁更高的吞吐量。
 *
 * <dt><b><i>公平模式</i></b>
 * <dd>当构造为公平模式时，线程使用近似到达顺序策略竞争获取锁。
 * 当当前持有的锁被释放时，要么为等待时间最长的写线程分配写锁，
 * 要么为一组等待时间比所有等待的写线程都长的读线程分配读锁。
 *
 * <p>如果写锁被某个写线程持有，或者有一个正在等待的写线程，那么试图获取一个公平的读锁(不可重入)的线程将阻塞。
 * 在当前最老的等待写线程获得并释放写锁之前，该读线程不会获取读锁。
 * 当然，如果一个等待的写线程放弃了它的等待，留下一个或多个读线程作为队列中最长的等待线程，而且写锁是空闲的，那么这些读线程将被分配读锁。
 *
 * <p>除非读锁和写锁都是空闲的(这意味着没有等待的线程)，否则试图获取一个公平的写锁(不可重入)的线程将阻塞
 * (注意，非阻塞的{@link ReadLock#tryLock()}和{@link WriteLock#tryLock()}方法不遵守这个公平设置，并且不管正在等待的线程如何，并且在可能的情况下，都会立即获取锁。)
 * <p>
 * </dl>
 *
 * <li><b>可重入性</b>
 *
 * <p>这个锁允许读线程和写线程以ReentrantLock的形式重新获取读锁或写锁。
 * 在写线程持有的所有写锁都被释放之前，不可重入的读线程是不被允许的。
 *
 * <p>
 * 此外，写线程可以获得读锁，但反之则不行。
 * 在其他应用程序中，在对需要获取读锁执行读取的方法的调用或回调过程中，写锁一直被持有，此时可重入性就非常有用了。（注释：先获取了写锁，又获取了读锁）
 * 如果一个读线程试图获取写锁，它将永远不会成功。
 *
 * <li><b>锁降级</b>
 * <p>可重入性还允许从写锁降级为读锁，过程是获取写锁，然后获取读锁，然后释放之前的写锁。
 * 但是，从读锁升级到写锁是不可能的。
 *
 * <li><b>锁获取的中断</b>
 * <p>读锁和写锁都支持在锁获取期间中断。
 *
 * <li><b>{@link Condition}条件支持</b>
 * <p>写锁提供了一个条件{@link Condition}实现，它在写锁方面的行为与ReentrantLock.newcondition()为ReentrantLock提供的条件实现相同。
 * 当然，此条件{@link Condition}只能与写锁一起使用。
 *
 * <p>读锁不支持条件@link Condition}，readLock().newCondition()会抛出UnsupportedOperationException。
 *
 * <li><b>仪表</b>
 * <p>该类提供了用于确定锁是否被持有或竞争的方法。这些方法是为监视系统状态而设计的，不是为同步控制而设计的。
 * </ul>
 *
 * <p>这个类的序列化与内置锁的行为方式相同:反序列化的锁处于解锁状态，而与它在序列化时的状态无关。
 *
 * <p><b>示例用法</b>.
 * 下面的代码演示了如何在更新缓存后执行锁降级(异常处理在以非嵌套方式处理多个锁时特别棘手):
 * <pre> {@code
 * class CachedData {
 *   Object data;
 *   volatile boolean cacheValid;
 *   final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
 *
 *   void processCachedData() {
 *     rwl.readLock().lock();
 *     if (!cacheValid) {
 *       // Must release read lock before acquiring write lock
 *       rwl.readLock().unlock();
 *       rwl.writeLock().lock();
 *       try {
 *         // Recheck state because another thread might have
 *         // acquired write lock and changed state before we did.
 *         if (!cacheValid) {
 *           data = ...
 *           cacheValid = true;
 *         }
 *         // Downgrade by acquiring read lock before releasing write lock
 *         rwl.readLock().lock();
 *       } finally {
 *         rwl.writeLock().unlock(); // Unlock write, still hold read
 *       }
 *     }
 *
 *     try {
 *       use(data);
 *     } finally {
 *       rwl.readLock().unlock();
 *     }
 *   }
 * }}</pre>
 *
 * ReentrantReadWriteLocks可用在某些集合的某些用途中改进并发性。
 * 通常，只有在预期集合很大、读线程比写线程更多地访问集合、并且操作的开销超过同步开销时，才值得这样做。
 * 例如，这里有一个使用TreeMap的类，这个TreeMap应该很大，并且可以并发访问。
 *  <pre> {@code
 * class RWDictionary {
 *   private final Map<String, Data> m = new TreeMap<String, Data>();
 *   private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
 *   private final Lock r = rwl.readLock();
 *   private final Lock w = rwl.writeLock();
 *
 *   public Data get(String key) {
 *     r.lock();
 *     try { return m.get(key); }
 *     finally { r.unlock(); }
 *   }
 *   public String[] allKeys() {
 *     r.lock();
 *     try { return m.keySet().toArray(); }
 *     finally { r.unlock(); }
 *   }
 *   public Data put(String key, Data value) {
 *     w.lock();
 *     try { return m.put(key, value); }
 *     finally { w.unlock(); }
 *   }
 *   public void clear() {
 *     w.lock();
 *     try { m.clear(); }
 *     finally { w.unlock(); }
 *   }
 * }}</pre>
 *
 * <h3>Implementation Notes</h3>
 *
 * <p>This lock supports a maximum of 65535 recursive write locks
 * and 65535 read locks. Attempts to exceed these limits result in
 * {@link Error} throws from locking methods.
 *
 * @since 1.5
 * @author Doug Lea
 */
public class ReentrantReadWriteLock
        implements ReadWriteLock, java.io.Serializable {
    private static final long serialVersionUID = -6992448646407690164L;
    /** 提供读锁的内部类 */
    private final ReentrantReadWriteLock.ReadLock readerLock;
    /** 提供写锁的内部类 */
    private final ReentrantReadWriteLock.WriteLock writerLock;
    /** 执行所有的同步语义 */
    final Sync sync;

    /**
     * 创建一个默认（非公平）排序属性的ReentrantReadWriteLock实例
     */
    public ReentrantReadWriteLock() {
        this(false);
    }

    /**
     * 创建一个带有给定公平性策略的ReentrantReadWriteLock实例
     *
     * @param fair {@code true} if this lock should use a fair ordering policy
     */
    public ReentrantReadWriteLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
        readerLock = new ReadLock(this);
        writerLock = new WriteLock(this);
    }

    public Lock writeLock() { return writerLock; }
    public Lock readLock()  { return readerLock; }

    /**
     * 给ReentrantReadWriteLock准备的同步实现
     * 子类分成公平和非公平两个版本。
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 6317671515068378041L;

        /*
         * 读取与写入计数，相关的提取常数和函数。
     * 锁定状态在逻辑上被分为两个无符号的short：
     * 较低的16位代表独占（写线程）锁持有次数，较高的16位代表共享（读线程）持有次数。
         */
        static final int SHARED_SHIFT   = 16;
        //共享锁单位（由于共享锁用高16位表示）
        static final int SHARED_UNIT    = (1 << SHARED_SHIFT);
        //锁的最大持有数
        static final int MAX_COUNT      = (1 << SHARED_SHIFT) - 1;
        //独占锁掩码，即低16位都为1
        static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;

        /** 返回c中的共享锁持有数 */
        static int sharedCount(int c)    { return c >>> SHARED_SHIFT; }
        /** 返回c中的独占锁持有数 */
        static int exclusiveCount(int c) { return c & EXCLUSIVE_MASK; }

        /**
         * 每个线程读保持有数的计数器。
         * 作为ThreadLocal维护;缓存在cachedHoldCounter
         */
        static final class HoldCounter {
            int count = 0;
            // 为了避免垃圾保留，使用线程的真正id值，而不是引用
            final long tid = getThreadId(Thread.currentThread());
        }

        /**
         * ThreadLocal子类。为了反序列化机制，最容易明确定义。
         */
        static final class ThreadLocalHoldCounter
            extends ThreadLocal<HoldCounter> {
            public HoldCounter initialValue() {
                return new HoldCounter();
            }
        }

        /**
         * 当前线程持有的可重入读锁的数量。
         * 仅在构造函数和readObject中初始化。
         * 当线程的读持有数下降到0时被删除。
         */
        private transient ThreadLocalHoldCounter readHolds;

        /**
         * 成功获取readLock的最后一个线程的持有计数。
         * 在下一个要释放的线程是最后一个要获取的线程的常见情况下，这可以节省ThreadLocal查找。
         * 这是非volatile的，因为它仅用作启发式方法，对于线程进行缓存非常有用。
         *
         * <p>可以使正在为其缓存读取保留计数的线程超时，但是可以通过不保留对该线程的引用来避免垃圾保留。
         * <p>通过良性数据竞赛访问； 依赖于内存模型的final字段和out-of-thin-air（凭空）保证。
         */
        private transient HoldCounter cachedHoldCounter;

        /**
         * firstReader is the first thread to have acquired the read lock.
         * firstReaderHoldCount is firstReader's hold count.
         *
         * <p>More precisely, firstReader is the unique thread that last
         * changed the shared count from 0 to 1, and has not released the
         * read lock since then; null if there is no such thread.
         *
         * <p>Cannot cause garbage retention unless the thread terminated
         * without relinquishing its read locks, since tryReleaseShared
         * sets it to null.
         *
         * <p>Accessed via a benign data race; relies on the memory
         * model's out-of-thin-air guarantees for references.
         *
         * <p>This allows tracking of read holds for uncontended read
         * locks to be very cheap.
         * firstReader是第一个获得读锁的线程。 firstReaderHoldCount是firstReader的持有计数。
         *
         * <p>更确切地说，firstReader是唯一一个线程，该线程最后一次将共享计数从0更改为1，并且此后没有释放读锁。
         *  如果没有这样的线程，则返回null。
         * todo 翻译准确性 since 翻译成由于？
         * <p>除非由于tryReleaseShared将其设置为null，线程在不放弃读锁的情况下终止，否则不会导致垃圾保留。
         *
         * <p>通过良性数据竞赛访问； 依赖于内存模型的对引用的out-of-thin-air保证。
         * <p>这使得跟踪无竞争读锁的读取持有数非常便宜。
         */
        private transient Thread firstReader = null;
        private transient int firstReaderHoldCount;

        Sync() {
            readHolds = new ThreadLocalHoldCounter();
            setState(getState()); // 确保readHolds的可见性。 todo 根据volatile的语义解释
        }

        /*
         * 对公平锁和非公平锁的获取和发布使用相同的代码，但在队列非空时是否/如何允许阻塞方面有所不同。
         */

        /**
         * 如果当前线程在尝试获取读锁时，根据策略不可以赶超其他正在等待的线程，则应该阻塞并返回true；否则则返回false。
         */
        abstract boolean readerShouldBlock();

        /**
         * 如果当前线程在尝试获取写锁时(或者有资格这样做)应该阻塞，则返回true，因为策略是为了取代其他正在等待的线程。
         * 如果当前线程在尝试获取写锁时，根据策略不可以赶超其他正在等待的线程，则应该阻塞并返回true；否则则返回false。
         */
        abstract boolean writerShouldBlock();

        /*
         * Note that tryRelease and tryAcquire can be called by
         * Conditions. So it is possible that their arguments contain
         * both read and write holds that are all released during a
         * condition wait and re-established in tryAcquire.
         * 请注意，tryRelease和tryAcquire可以被Conditions调用。
         * //todo 翻译准确性，re-established
         * 因此，它们的参数可能同时包含读和写持有，所有这些都在条件等待期间释放，并在tryAcquire中重新建立。
         */

        /**
         * 在独占模式中，尝试设置状态（state）反映释放。
         */
        protected final boolean tryRelease(int releases) {
            //如果不是独占获取线程调用，就抛出IllegalMonitorStateException
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            int nextc = getState() - releases;
            //释放完之后之后是否为0，
            //   是，free为true，即释放独占锁成功，设置当前独占线程为null，更新state，返回true。
            //   不是，free为false，更新state，返回false。
            boolean free = exclusiveCount(nextc) == 0;
            if (free)
                setExclusiveOwnerThread(null);
            setState(nextc);
            return free;
        }

        /**
         * 尝试以独占模式进行获取。
         */
        protected final boolean tryAcquire(int acquires) {
            /*
             * Walkthrough:
             * 1. 如果读锁持有数非0或者写锁持有数非0，并且独占持有线程是另一个线程，则失败。
             * 2. 如果持有数饱和（大于MAX_COUNT），则失败。（这只能在持有数非0的情况下出现）
             * 3. 否则，如果该线程是可重入获取或者被队列策略允许，则有资格进行获取锁。 如果是这样，就更新state值并设置所有者。
             *  , this thread is eligible for lock if
             *    it is either a reentrant acquire or
             *    queue policy allows it. If so, update state
             *    and set owner.
             */
            Thread current = Thread.currentThread();
            int c = getState();
            int w = exclusiveCount(c);
            if (c != 0) {
                // (Note: if c != 0 and w == 0 then shared count != 0)
                //如果独占锁持有数为0，返回false。如果不为0，但当前线程不是独占线程，也返回false。
                if (w == 0 || current != getExclusiveOwnerThread())
                    return false;
                //独占锁持有数是否溢出。
                if (w + exclusiveCount(acquires) > MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                //可重入获取。
                setState(c + acquires);
                return true;
            }
            //如果当前读线程应该阻塞，则返回false；
            // 否则，就判断CAS设置state能否成功
            //      可以成功，可获取独占锁成功，即继续设置当前线程为独占线程
            //      不可以成功，则返回false。
            if (writerShouldBlock() ||
                !compareAndSetState(c, c + acquires))
                return false;
            setExclusiveOwnerThread(current);
            return true;
        }

        /**
         * 尝试设置状态来反映共享模式下的释放。
         * 释放成功返回true，否则返回false
         */
        protected final boolean tryReleaseShared(int unused) {
            Thread current = Thread.currentThread();
            //下面的if-else都是用来更新相关计数的。
            //如果当前线程是第一个获取共享锁的读线程，更新相关计数。
            if (firstReader == current) {
                //判断firstReaderHoldCount的持有数
                // assert firstReaderHoldCount > 0;
                if (firstReaderHoldCount == 1)
                    firstReader = null;
                else
                    firstReaderHoldCount--;
            } else {
                //如果当前线程不是第一个获取共享锁的读线程，也更新相关计数（只是存的位置不同，下面是存在ThreadLocal里面的）
                HoldCounter rh = cachedHoldCounter;
                if (rh == null || rh.tid != getThreadId(current))
                    rh = readHolds.get();
                int count = rh.count;
                if (count <= 1) {
                    readHolds.remove();
                    if (count <= 0)
                        throw unmatchedUnlockException();
                }
                --rh.count;
            }

            //不断尝试利用CAS更新共享持有数，也就是更新state中的高16位，直到成功。
            //如果第一次变为0，则返回true，否则返回false。
            for (;;) {
                int c = getState();
                int nextc = c - SHARED_UNIT;
                if (compareAndSetState(c, nextc)) {
                    /**
                     * 释放读锁对读线程没有影响，
                     * 但是，如果读锁和写锁现在都是空闲的，那么它可能允许等待的写线程继续工作。
                     */
                    return nextc == 0;
                }
            }
        }

        private IllegalMonitorStateException unmatchedUnlockException() {
            return new IllegalMonitorStateException(
                "attempt to unlock read lock, not locked by current thread");
        }

        /**
         * 尝试以共享模式获取。该方法应该查询对象的状态是否允许在共享模式下获取它，如果允许，则应该获取它。
         */
        protected final int tryAcquireShared(int unused) {
            /*
             * Walkthrough:
             * 1.如果写锁被另一个线程持有，失败
             * 2.否则，此线程将有资格获得锁wrt状态，因此测试它是否应该由于队列策略而阻塞。
             *    如果不是，尝试通过CAS状态和更新计数来授予读锁。
             *    注意，step没有检查可重入的获取，它被推迟到完整版本，以避免在更典型的不可重入情况下检查持有计数。
             * 3.如果第2步失败（由于线程显然不符合条件，或者CAS失败或计数饱和），就执行完整版的读锁获取。
             */
            Thread current = Thread.currentThread();
            int c = getState();
            //1.写锁（独占锁）持有数不为0，并且独占锁拥有者线程不是当前线程。
            if (exclusiveCount(c) != 0 &&
                getExclusiveOwnerThread() != current)
                return -1;
            //获取共享锁持有数
            int r = sharedCount(c);
            //2.如果读线程不需要阻塞，且共享锁持有数小于最大值，且CAS state成功
            if (!readerShouldBlock() &&
                r < MAX_COUNT &&
                compareAndSetState(c, c + SHARED_UNIT)) {
                //更新读锁相关计数。
                if (r == 0) {
                    firstReader = current;
                    firstReaderHoldCount = 1;
                } else if (firstReader == current) {
                    firstReaderHoldCount++;
                } else {
                    HoldCounter rh = cachedHoldCounter;
                    if (rh == null || rh.tid != getThreadId(current))
                        cachedHoldCounter = rh = readHolds.get();
                    else if (rh.count == 0)
                        readHolds.set(rh);
                    rh.count++;
                }
                return 1;
            }
            //3.如果第2步失败（由于线程显然不符合条件，或者CAS失败或计数饱和），就执行完整版的读锁获取。
            return fullTryAcquireShared(current);
        }

        /**
         * 读锁获取的完整版本，处理CAS失败和可重入读取，这些是在tryAcquireShared中没有处理的。
         */
        final int fullTryAcquireShared(Thread current) {
            /*
             *
             * 该代码与tryAcquireShared中的代码部分冗余，但由于不使tryAcquireShared与重试和延迟读取持有数之间的交互复杂化，因此总体上更简单。
             */

            HoldCounter rh = null;
            for (;;) {
                int c = getState();
                //如果独占锁持有数不为0
                if (exclusiveCount(c) != 0) {
                    //当前线程不是独占线程，返回-1
                    if (getExclusiveOwnerThread() != current)
                        return -1;
                    // else we hold the exclusive lock; blocking here
                    // would cause deadlock.
                } else if (readerShouldBlock()) {
                    // 确保我们不是在重入地获取读锁。
                    if (firstReader == current) {
                        // assert firstReaderHoldCount > 0;
                    } else {
                        //更新相关持有数
                        if (rh == null) {
                            rh = cachedHoldCounter;
                            if (rh == null || rh.tid != getThreadId(current)) {
                                rh = readHolds.get();
                                if (rh.count == 0)
                                    readHolds.remove();
                            }
                        }
                        if (rh.count == 0)
                            return -1;
                    }
                }
                if (sharedCount(c) == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                //尝试CAS设置共享锁状态。
                if (compareAndSetState(c, c + SHARED_UNIT)) {
                    //更新相关持有数。
                    if (sharedCount(c) == 0) {
                        firstReader = current;
                        firstReaderHoldCount = 1;
                    } else if (firstReader == current) {
                        firstReaderHoldCount++;
                    } else {
                        if (rh == null)
                            rh = cachedHoldCounter;
                        if (rh == null || rh.tid != getThreadId(current))
                            rh = readHolds.get();
                        else if (rh.count == 0)
                            readHolds.set(rh);
                        rh.count++;
                        cachedHoldCounter = rh; // cache for release
                    }
                    return 1;
                }
            }
        }

        /**
         * 尝试获取写锁，从而在两种模式下都可以进行插入。
         * 除了tryAcquire缺少对writerShouldBlock的调用外，tryWriteLock与tryAcquire在效果上是相同的。
         * */
        final boolean tryWriteLock() {
            Thread current = Thread.currentThread();
            int c = getState();
            if (c != 0) {
                //写锁（独占锁）的持有数
                int w = exclusiveCount(c);
                //如果写锁（独占锁）的持有数w等于0，而c!=0,那么就意味着读锁（共享锁）持有数不为0，立即返回false。
                //如果写锁（独占锁）的持有数w不等于0，但当前线程不是独占锁拥有线程，也返回false。
                if (w == 0 || current != getExclusiveOwnerThread())
                    return false;
                //如果写锁（独占锁）的持有数w等于MAX_COUNT，抛出溢出Error。
                if (w == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
            }
            //CAS 尝试给state加1，
            //      如果成功，则表示获取写锁（独占锁）成功，那么接下来设置当前线程为独占线程，返回true。
            //      如果失败，则表示获取写锁（度诊所）失败，返回false。
            if (!compareAndSetState(c, c + 1))
                return false;
            setExclusiveOwnerThread(current);
            return true;
        }

        /**
         * 尝试获取读锁，从而在两种模式下都可以进行插入。
         * 除了tryAcquireShared缺少对readerShouldBlock的调用外，tryReadLock与tryAcquireShared在效果上是相同的。
         */
        final boolean tryReadLock() {
            Thread current = Thread.currentThread();
            for (;;) {
                int c = getState();
                //写锁（独占锁）持有数不为0，并且独占线程不是当前线程就返回false。
                if (exclusiveCount(c) != 0 &&
                    getExclusiveOwnerThread() != current)
                    return false;
                int r = sharedCount(c);
                if (r == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                //尝试CAS设置共享锁持有数，也就是给state的高16位对应的数+1
                if (compareAndSetState(c, c + SHARED_UNIT)) {
                    //更新读锁相关计数。
                    if (r == 0) {
                        firstReader = current;
                        firstReaderHoldCount = 1;
                    } else if (firstReader == current) {
                        firstReaderHoldCount++;
                    } else {
                        HoldCounter rh = cachedHoldCounter;
                        if (rh == null || rh.tid != getThreadId(current))
                            cachedHoldCounter = rh = readHolds.get();
                        else if (rh.count == 0)
                            readHolds.set(rh);
                        rh.count++;
                    }
                    return true;
                }
            }
        }

        protected final boolean isHeldExclusively() {
            // While we must in general read state before owner,
            // we don't need to do so to check if current thread is owner
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        // Methods relayed to outer class

        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        final Thread getOwner() {
            //为了保证内存一致性，必须在返回独占线程之前读取state的值。
            return ((exclusiveCount(getState()) == 0) ?
                    null :
                    getExclusiveOwnerThread());
        }

        /**
         * 获取所有读线程读锁的持有数。
         */
        final int getReadLockCount() {
            return sharedCount(getState());
        }

        /**
         * 是否写锁已经被锁定。
         */
        final boolean isWriteLocked() {
            return exclusiveCount(getState()) != 0;
        }

        /**
         * 获取写锁（独占锁）的持有数。
         */
        final int getWriteHoldCount() {
            return isHeldExclusively() ? exclusiveCount(getState()) : 0;
        }

        /**
         * 获取当前线程的读锁持有数
         */
        final int getReadHoldCount() {
            if (getReadLockCount() == 0)
                return 0;

            Thread current = Thread.currentThread();
            if (firstReader == current)
                return firstReaderHoldCount;

            HoldCounter rh = cachedHoldCounter;
            if (rh != null && rh.tid == getThreadId(current))
                return rh.count;

            int count = readHolds.get().count;
            if (count == 0) readHolds.remove();
            return count;
        }

        /**
         * 从一个流中重建实例，（即反序列化）
         */
        private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            readHolds = new ThreadLocalHoldCounter();
            setState(0); // reset to unlocked state
        }

        final int getCount() { return getState(); }
    }

    /**
     * Sync的非公平版
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = -8159625535654395037L;
        final boolean writerShouldBlock() {
            //写线程总是可以插入。
            return false;
        }
        final boolean readerShouldBlock() {
            /*
             * 作为一种避免无限期地写线程饥饿的启发式方法，如果暂时看起来位于队列头节点的线程存在且是写线程，则阻塞读线程。
             * 这只是一种概率效应，因为如果在其他启用的读线程后面存在还有没被消耗的写线程，那么新的读线程将不会阻塞。
             */
            return apparentlyFirstQueuedIsExclusive();
        }
    }

    /**
     * Sync的公平版
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = -2274990926593161451L;
        /**
         * 查询有任何线程等待获取的时间比当前线程长，则应该阻塞当前的写线程。
         */
        final boolean writerShouldBlock() {
            return hasQueuedPredecessors();
        }
        /**
         * 查询是否有任何线程等待获取的时间比当前线程长。
         */
        final boolean readerShouldBlock() {
            return hasQueuedPredecessors();
        }
    }

    /**
     * 由方法{@link ReentrantReadWriteLock#readLock}返回的锁
     */
    public static class ReadLock implements Lock, java.io.Serializable {
        private static final long serialVersionUID = -5992448646407690164L;
        private final Sync sync;

        /**
         * Constructor for use by subclasses
         *
         * @param lock 外层的锁对象
         * @throws NullPointerException if the lock is null
         */
        protected ReadLock(ReentrantReadWriteLock lock) {
            sync = lock.sync;
        }

        /**
         * 获取读锁（共享锁）
         * <p>如果写锁没有被其他线程持有，则获取读锁并立即返回。
         *
         * <p>如果写锁被其他线程持有，则出于线程调度目的，当前线程将被禁用，并处于休眠状态，直到获取读锁为止。
         */
        public void lock() {
            sync.acquireShared(1);
        }

        /**
         * 除非当前线程被中断，否则获取读锁。
         *
         * <p>如果其他线程没有持有写锁，则当前线程获取读锁并立即返回。
         * <p>如果写锁是被其他线程持有，那么当前线程就会出于线程调度的目的而被禁用，并处于休眠状态，直到发生以下两种情况之一:
         * <ul>
         * <li>读锁由当前线程获取;或
         * <li>其他一些线程中断当前线程。
         * </ul>
         *
         * <p>如果当前线程:
         * <ul>
         * <li>在进入此方法时已设置其中断状态;或
         * <li>在获取读锁时中断，
         * </ul>
         * 那么抛出InterruptedException，并清除当前线程的中断状态。
         *
         * <p>在这个实现中，由于这个方法是一个显式的中断点，所以优先响应中断而不是正常的或可重入的锁获取。
         *
         * @throws InterruptedException if the current thread is interrupted
         */
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireSharedInterruptibly(1);
        }

        /**
         * 仅当在调用时写锁没有被其他线程持有，才获取读锁。
         *
         *  <p>如果写锁没有被另一个线程持有，则获取读锁，并立即返回值true。
         *  即使这个锁被设置为使用公平的排序策略，如果读锁可用，调用tryLock()也会立即获得读锁，不管其他线程是否正在等待读锁。
         *  这种“冲撞”行为在某些情况下是有用的，即使它破坏了公平。
         *  如果你想为这个锁执行公平设置，那么使用tryLock(0, TimeUnit.SECONDS)，这几乎是等价的(它还可以检测到中断)。
         *  <p>如果写锁被另一个线程持有，那么这个方法将立即返回值false。
         *
         * @return {@code true} if the read lock was acquired
         */
        public boolean tryLock() {
            return sync.tryReadLock();
        }

        /**
         * 如果在给定的等待时间内另一个线程未持有写锁，并且当前线程尚未中断，则获取读锁。
         *
         * <p>如果写锁没有被另一个线程持有，则获取读锁，并立即返回true值。
         *  如果已将此锁设置为使用公平的排序策略，则如果任何其他线程正在等待该锁，则将不会获取可用的锁。
         *  这与{@link #tryLock()}方法相反。
         *  如果你想要一个定时tryLock确实允许在公平锁上插入，则将定时和非定时形式组合在一起：
         *  <pre> {@code
         * if (lock.tryLock() ||
         *     lock.tryLock(timeout, unit)) {
         *   ...
         * }}</pre>
         *
         * <p>如果写锁是由另一个线程持有，那么当前线程就会出于线程调度的目的而被禁用，并处于休眠状态，直到发生以下三种情况之一:
         * <ul>
         * <li>读锁由当前线程获取;或
         * <li>其他一些线程中断当前线程;或
         * <li>指定的等待时间已经过了。
         * </ul>
         *
         * <p>如果获取了读锁，则返回true值。
         *
         * <p> 如果当前线程:
         * <ul>
         * <li>在进入此方法时已设置其中断状态;或
         * <li>在获取读锁时中断，
         * </ul> 那么就抛出InterruptedException，并清除当前线程的中断状态。
         *
         * <p> 如果指定的等待时间过去了，则返回false值。如果时间小于或等于0，则该方法根本不会等待。
         *
         * <p>在这个实现中，由于这个方法是一个显式的中断点，所以优先响应中断，而不是正常的或可重入的获取锁，或者报告等待时间的流逝。
         * @param timeout the time to wait for the read lock
         * @param unit the time unit of the timeout argument
         * @return {@code true} 如果读锁成功获取。
         * @throws InterruptedException if the current thread is interrupted
         * @throws NullPointerException if the time unit is null
         */
        public boolean tryLock(long timeout, TimeUnit unit)
                throws InterruptedException {
            return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
        }

        /**
         * 尝试释放此锁。
         *
         * <p>如果读线程的数量现在为零，那么锁就可以用于写锁尝试。
         */
        public void unlock() {
            sync.releaseShared(1);
        }

        /**
         * 抛出{@code UnsupportedOperationException}，因为{@code ReadLocks}不支持条件。
         * @throws UnsupportedOperationException always
         */
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }

        /**
         * Returns a string identifying this lock, as well as its lock state.
         * The state, in brackets, includes the String {@code "Read locks ="}
         * followed by the number of held read locks.
         *
         * @return a string identifying this lock, as well as its lock state
         */
        public String toString() {
            int r = sync.getReadLockCount();
            return super.toString() +
                "[Read locks = " + r + "]";
        }
    }

    /**
     *  由方法 {@link ReentrantReadWriteLock#writeLock}返回的锁。
     */
    public static class WriteLock implements Lock, java.io.Serializable {
        private static final long serialVersionUID = -4992448646407690164L;
        private final Sync sync;

        /**
         * 子类使用的构造方法
         *
         * @param lock 外部锁对象
         * @throws NullPointerException if the lock is null
         */
        protected WriteLock(ReentrantReadWriteLock lock) {
            sync = lock.sync;
        }

        /**
         *
         * 获取写锁。
         *
         * <p>如果读锁和写锁均没有由另一个线程持有，则获取写锁定并立即返回，将写锁持有数设置为1。
         *
         * <p>如果当前线程已经持有写锁，则持有计数将增加一，并且该方法将立即返回。
         *
         * <p>如果锁是由另一个线程持有的，则出于线程调度目的，当前线程将被禁用，并处于休眠状态，直到获取写锁为止，此时写锁的保持计数被设置为1。
         */
        public void lock() {
            sync.acquire(1);
        }

        /*
         *  除非当前线程被中断，否则获取写锁。
         *
         * <p>如果读锁和写锁都不是由另一个线程持有，则获取写锁并立即返回，将写锁持有数设置为1。
         *
         * <p>如果当前线程已经持有该锁，那么持有数将增加1，该方法立即返回。
         *
         * <p>如果锁是由另一个线程持有，那么当前线程就会出于线程调度的目的而被禁用，并处于休眠状态，直到发生以下两种情况之一:
         * <ul>
         * <li>写锁被当前线程获取;或
         * <li>其他一些线程中断当前线程。
         * </ul>
         *
         * <p>如果写锁被当前线程获取，那么锁持有计数被设置为1。
         *
         * <p>如果当前线程:
         * <ul>
         * <li>在进入此方法时已设置其中断状态;或
         * <li>在获取写锁时中断，
         * </ul>那么就抛出InterruptedException，并清除当前线程的中断状态。
         *
         * <p>在这个实现中，由于这个方法是一个显式的中断点，所以优先响应中断而不是正常的或可重入的锁获取。
         *
         * @throws InterruptedException 如果当前线程被中断。
         */
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireInterruptibly(1);
        }

        /**
         * Acquires the write lock only if it is not held by another thread
         * at the time of invocation.
         *
         * <p>Acquires the write lock if neither the read nor write lock
         * are held by another thread
         * and returns immediately with the value {@code true},
         * setting the write lock hold count to one. Even when this lock has
         * been set to use a fair ordering policy, a call to
         * {@code tryLock()} <em>will</em> immediately acquire the
         * lock if it is available, whether or not other threads are
         * currently waiting for the write lock.  This &quot;barging&quot;
         * behavior can be useful in certain circumstances, even
         * though it breaks fairness. If you want to honor the
         * fairness setting for this lock, then use {@link
         * #tryLock(long, TimeUnit) tryLock(0, TimeUnit.SECONDS) }
         * which is almost equivalent (it also detects interruption).
         *
         * <p>If the current thread already holds this lock then the
         * hold count is incremented by one and the method returns
         * {@code true}.
         *
         * <p>If the lock is held by another thread then this method
         * will return immediately with the value {@code false}.
         *
         * @return {@code true} if the lock was free and was acquired
         * by the current thread, or the write lock was already held
         * by the current thread; and {@code false} otherwise.
         * 仅当写锁在调用时不被其他线程持有时，才获取它。
         *
         * <p>如果读锁和写锁都没有由另一个线程持有的，则获取写锁，并立即返回值true，将写锁持有计数设置为1。
         * 即使这个锁被设置为使用公平的排序策略，如果锁可用，调用tryLock()也会立即获得锁，不管其他线程是否正在等待写锁。
         * 这种“冲撞”行为在某些情况下是有用的，即使它破坏了公平。
         * 如果你想为这个锁执行公平设置，那么使用tryLock(0, TimeUnit.SECONDS)，这几乎是等价的(它还可以检测到中断)。
         *
         * <p>如果当前线程已经持有该锁，那么持有数将增加1，方法返回true。
         *
         * <p>如果锁被另一个线程持有，那么这个方法将立即返回值false。
         */
        public boolean tryLock( ) {
            return sync.tryWriteLock();
        }

        /**
         * 如果写锁在给定的等待时间内没有被另一个线程持有并且当前线程尚未中断，则获取写锁。
         *
         * <p>如果读取和写锁均未由另一个线程持有，则获取写锁，并立即返回true值，将写入锁的保持计数设置为1。
         * 如果此锁已设置为使用公平的排序策略，则如果任何其他线程正在等待写锁，则不会获取该可用锁。
         * 这与{@link #tryLock()}方法相反。
         * 如果你想要一个定时tryLock，并且确实允许在公平锁上插入，则将定时和非定时形式组合在一起：
         *
         *  <pre> {@code
         * if (lock.tryLock() ||
         *     lock.tryLock(timeout, unit)) {
         *   ...
         * }}</pre>
         * <p>如果当前线程已经持有此锁，则持有数将增加一，并且该方法返回true。
         *
         * <p>如果该锁是由另一个线程持有的，则出于线程调度目的，当前线程将被禁用，并处于休眠状态，直到发生以下三种情况之一：
         * <ul>
         * <li>写锁由当前线程获取；或
         * <li>其他一些线程中断当前线程。或
         * <li>经过指定的等待时间
         * </ul>
         *
         * <p>如果获取了写锁，则返回值true，并且将写锁持有数设置为1。
         *
         * <p>如果当前线程：
         * <ul>
         * <li>在进入此方法时已设置其中断状态；或
         * <li>获取写锁时被中断，
         * </ul>那么就抛出InterruptedException，并清除当前线程的中断状态。
         *
         * <p>如果经过了指定的等待时间，则返回值false。如果时间小于或等于零，则该方法将根本不等待。
         *
         * <p>在此实现中，由于此方法是显式的中断点，因此优先于响应中断，而不是正常或可重入的锁获取，或报告等待时间的流逝。
         *
         * @throws InterruptedException if the current thread is interrupted
         * @throws NullPointerException if the time unit is null
         */
        public boolean tryLock(long timeout, TimeUnit unit)
                throws InterruptedException {
            return sync.tryAcquireNanos(1, unit.toNanos(timeout));
        }

        /**
         * 尝试释放此锁
         *
         * <p>如果当前线程是此锁的持有者，则持有数将减少。
         * 如果持有数现在为零，则释放锁。
         * 如果当前线程不是此锁的持有者，则抛出IllegalMonitorStateException。
         *
         * @throws IllegalMonitorStateException if the current thread does not
         * hold this lock
         */
        public void unlock() {
            sync.release(1);
        }

        /**
         * 返回用于此锁实例的条件{@link Condition} 实例。
         *
         * <p>当与内置的monitor锁一起使用时，返回的条件实例支持与Object monitor方法(wait、notify和notifyAll)相同的用法。
         * <ul>
         * <li>如果在调用任何条件{@link Condition}方法时未持有此写锁，则抛出IllegalMonitorStateException。
         *  (读锁独立于写锁，因此不会被检查或影响。然而，当当前线程也获取了读锁时，调用条件{@link Condition}等待方法通常是错误的，因为其他可以解除阻塞的线程将无法获取写锁)。
         * <li>当条件等待方法 {@linkplain Condition#await()}被调用时，写锁被释放，在它们返回之前，写锁被重新获得，锁持有数恢复到方法被调用时的值。
         * <li>如果线程在等待期间被中断，那么等待将终止，抛出InterruptedException，并清除线程的中断状态。
         * <li>等待线程按FIFO顺序被通知。
         * <li>从等待方法返回的线程的锁重获顺序与最初获取锁的线程相同(在默认情况下未指定)，但对于公平锁，优先使用那些等待时间最长的线程。
         * </ul>
         * @return the Condition object
         */
        public Condition newCondition() {
            return sync.newCondition();
        }

        /**
         * Returns a string identifying this lock, as well as its lock
         * state.  The state, in brackets includes either the String
         * {@code "Unlocked"} or the String {@code "Locked by"}
         * followed by the {@linkplain Thread#getName name} of the owning thread.
         *
         * @return a string identifying this lock, as well as its lock state
         */
        public String toString() {
            Thread o = sync.getOwner();
            return super.toString() + ((o == null) ?
                                       "[Unlocked]" :
                                       "[Locked by thread " + o.getName() + "]");
        }

        /**
         * Queries if this write lock is held by the current thread.
         * Identical in effect to {@link
         * ReentrantReadWriteLock#isWriteLockedByCurrentThread}.
         *
         * @return {@code true} if the current thread holds this lock and
         *         {@code false} otherwise
         * @since 1.6
         */
        public boolean isHeldByCurrentThread() {
            return sync.isHeldExclusively();
        }

        /**
         * Queries the number of holds on this write lock by the current
         * thread.  A thread has a hold on a lock for each lock action
         * that is not matched by an unlock action.  Identical in effect
         * to {@link ReentrantReadWriteLock#getWriteHoldCount}.
         *
         * @return the number of holds on this lock by the current thread,
         *         or zero if this lock is not held by the current thread
         * @since 1.6
         */
        public int getHoldCount() {
            return sync.getWriteHoldCount();
        }
    }

    // 下面是检测和状态相关的一些工具方法

    /**
     * Returns {@code true} if this lock has fairness set true.
     *
     * @return {@code true} if this lock has fairness set true
     */
    public final boolean isFair() {
        return sync instanceof FairSync;
    }

    /**
     * Returns the thread that currently owns the write lock, or
     * {@code null} if not owned. When this method is called by a
     * thread that is not the owner, the return value reflects a
     * best-effort approximation of current lock status. For example,
     * the owner may be momentarily {@code null} even if there are
     * threads trying to acquire the lock but have not yet done so.
     * This method is designed to facilitate construction of
     * subclasses that provide more extensive lock monitoring
     * facilities.
     *
     * @return the owner, or {@code null} if not owned
     */
    protected Thread getOwner() {
        return sync.getOwner();
    }

    /**
     * Queries the number of read locks held for this lock. This
     * method is designed for use in monitoring system state, not for
     * synchronization control.
     * @return the number of read locks held
     */
    public int getReadLockCount() {
        return sync.getReadLockCount();
    }

    /**
     * Queries if the write lock is held by any thread. This method is
     * designed for use in monitoring system state, not for
     * synchronization control.
     *
     * @return {@code true} if any thread holds the write lock and
     *         {@code false} otherwise
     */
    public boolean isWriteLocked() {
        return sync.isWriteLocked();
    }

    /**
     * Queries if the write lock is held by the current thread.
     *
     * @return {@code true} if the current thread holds the write lock and
     *         {@code false} otherwise
     */
    public boolean isWriteLockedByCurrentThread() {
        return sync.isHeldExclusively();
    }

    /**
     * Queries the number of reentrant write holds on this lock by the
     * current thread.  A writer thread has a hold on a lock for
     * each lock action that is not matched by an unlock action.
     *
     * @return the number of holds on the write lock by the current thread,
     *         or zero if the write lock is not held by the current thread
     */
    public int getWriteHoldCount() {
        return sync.getWriteHoldCount();
    }

    /**
     * Queries the number of reentrant read holds on this lock by the
     * current thread.  A reader thread has a hold on a lock for
     * each lock action that is not matched by an unlock action.
     *
     * @return the number of holds on the read lock by the current thread,
     *         or zero if the read lock is not held by the current thread
     * @since 1.6
     */
    public int getReadHoldCount() {
        return sync.getReadHoldCount();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire the write lock.  Because the actual set of threads may
     * change dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedWriterThreads() {
        return sync.getExclusiveQueuedThreads();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire the read lock.  Because the actual set of threads may
     * change dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedReaderThreads() {
        return sync.getSharedQueuedThreads();
    }

    /**
     * Queries whether any threads are waiting to acquire the read or
     * write lock. Note that because cancellations may occur at any
     * time, a {@code true} return does not guarantee that any other
     * thread will ever acquire a lock.  This method is designed
     * primarily for use in monitoring of the system state.
     *
     * @return {@code true} if there may be other threads waiting to
     *         acquire the lock
     */
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * Queries whether the given thread is waiting to acquire either
     * the read or write lock. Note that because cancellations may
     * occur at any time, a {@code true} return does not guarantee
     * that this thread will ever acquire a lock.  This method is
     * designed primarily for use in monitoring of the system state.
     *
     * @param thread the thread
     * @return {@code true} if the given thread is queued waiting for this lock
     * @throws NullPointerException if the thread is null
     */
    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    /**
     * Returns an estimate of the number of threads waiting to acquire
     * either the read or write lock.  The value is only an estimate
     * because the number of threads may change dynamically while this
     * method traverses internal data structures.  This method is
     * designed for use in monitoring of the system state, not for
     * synchronization control.
     *
     * @return the estimated number of threads waiting for this lock
     */
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire either the read or write lock.  Because the actual set
     * of threads may change dynamically while constructing this
     * result, the returned collection is only a best-effort estimate.
     * The elements of the returned collection are in no particular
     * order.  This method is designed to facilitate construction of
     * subclasses that provide more extensive monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with the write lock. Note that because timeouts and
     * interrupts may occur at any time, a {@code true} return does
     * not guarantee that a future {@code signal} will awaken any
     * threads.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @param condition the condition
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    public boolean hasWaiters(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with the write lock. Note that because
     * timeouts and interrupts may occur at any time, the estimate
     * serves only as an upper bound on the actual number of waiters.
     * This method is designed for use in monitoring of the system
     * state, not for synchronization control.
     *
     * @param condition the condition
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    public int getWaitQueueLength(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with the write lock.
     * Because the actual set of threads may change dynamically while
     * constructing this result, the returned collection is only a
     * best-effort estimate. The elements of the returned collection
     * are in no particular order.  This method is designed to
     * facilitate construction of subclasses that provide more
     * extensive condition monitoring facilities.
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * Returns a string identifying this lock, as well as its lock state.
     * The state, in brackets, includes the String {@code "Write locks ="}
     * followed by the number of reentrantly held write locks, and the
     * String {@code "Read locks ="} followed by the number of held
     * read locks.
     *
     * @return a string identifying this lock, as well as its lock state
     */
    public String toString() {
        int c = sync.getCount();
        int w = Sync.exclusiveCount(c);
        int r = Sync.sharedCount(c);

        return super.toString() +
            "[Write locks = " + w + ", Read locks = " + r + "]";
    }

    /**
     * 返回给定线程的线程id。我们必须直接访问它，
     * 而不是通过Thread.getId()方法，因为getId()方法不是final的，而且已经知道会以不保留惟一映射的方式覆盖它。
     */
    static final long getThreadId(Thread thread) {
        return UNSAFE.getLongVolatile(thread, TID_OFFSET);
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long TID_OFFSET;
    static {
        try {
            UNSAFE = GetUnsafeFromReflect.getUnsafe();
            Class<?> tk = Thread.class;
            TID_OFFSET = UNSAFE.objectFieldOffset
                (tk.getDeclaredField("tid"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
