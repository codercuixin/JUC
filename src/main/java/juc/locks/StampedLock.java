package juc.locks;


import unsafeTest.GetUnsafeFromReflect;

import java.util.concurrent.TimeUnit;

/**
 * A capability-based lock with three modes for controlling read/write
 * access.  The state of a StampedLock consists of a version and mode.
 * Lock acquisition methods return a stamp that represents and
 * controls access with respect to a lock state; "try" versions of
 * these methods may instead return the special value zero to
 * represent failure to acquire access. Lock release and conversion
 * methods require stamps as arguments, and fail if they do not match
 * the state of the lock. The three modes are:
 * 一种基于功能的锁，具有三种模式来控制读/写访问。StampedLock的状态由版本和模式组成。
 * 锁获取方法返回一个戳记(stamp)，该戳记表示锁状态并控制对锁的访问；
 * 这些方法的“try”版本可能会返回特殊值零，以表示无法获取访问权限。
 * 锁释放和转换方法需要使用戳记(stamp)作为参数，如果它们与锁的状态不匹配，则会失败。
 * 三种模式是：
 * <ul>
 *
 *  <li>写模式{@link #writeLock}可能会为了独占访问而阻塞等待，返回可以在方法{@link #unlockWrite(long)}中使用的戳记(stamp)以释放锁。
 *  还提供了{@link #tryWriteLock}｝的非定时版本和定时版本。当锁保持在写模式时，可能不会获得任何读锁，并且所有乐观读验证都将失败。</li>
 *
 *  <li>读模式{@link #readLock}可能为了非独占访问而阻塞等待，返回可以在方法{@link #unlockRead(long)｝中使用的戳记以释放锁。
 *   还提供了{@link #tryReadLock}的非定时版本和定时版本。</li>
 *
 *  <li><b>乐观读模式.</b> </li>仅当锁目前未被在写模式下持有，方法{@link #tryOptimisticRead()}才返回非零戳记(stamp)。
 *  如果自获取给定戳记以来没有在写模式下获取锁，则{@link #validate(long)}方法将返回true。
 *  可以将此模式视为读锁的极弱版本，写线程可以随时将其破坏。
 *  对较短的只读代码段使用乐观模式通常可以减少争用并提高吞吐量。
 *  但是，乐观锁的使用是天生就是脆弱的。
 *  乐观的读取部分代码应仅读取字段并将其保存在局部变量中，以供验证后使用。
 *  在乐观模式下读取的字段可能完全不一致，因此用法仅在你足够熟悉数据表示以检查一致性和/或重复调用方法validate（）时适用。
 *  例如，当首先读取对象或数组引用，然后访问其字段，元素或方法之一时，通常需要执行上面这些步骤 </li>
 * </ul>
 *
 * <p>This class also supports methods that conditionally provide
 * conversions across the three modes. For example, method {@link
 * #tryConvertToWriteLock} attempts to "upgrade" a mode, returning
 * a valid write stamp if (1) already in writing mode (2) in reading
 * mode and there are no other readers or (3) in optimistic mode and
 * the lock is available. The forms of these methods are designed to
 * help reduce some of the code bloat that otherwise occurs in
 * retry-based designs.
 * 此类还支持有条件地在三种模式之间提供转换的方法。
 * 例如，方法tryConvertToWriteLock(long)}尝试“升级”一个模式，如果（1）已经处于写模式（2）处于读模式并且没有其他读线程，或者（3）处于乐观读模式 并且锁是可用的，则返回有效的写戳记。
 * 这些方法的形式旨在帮助减少在基于重试的设计中原本会发生的某些代码膨胀。
 *
 * <p>StampedLocks被设计为在开发线程安全组件时用作内部实用程序。
 * 它们的使用取决于对它们所保护的数据，对象和方法的内部属性的了解。
 * 它们不是可重入的，因此锁定的主体不应调用可能尝试重新获取锁的其他未知方法（尽管你可以将戳记传递给可以使用或转换该戳记的其他方法）。
 * 读锁模式的使用依赖于相关的代码段无副作用。
 * 未经验证的乐观读代码段无法调用未知的方法来容忍潜在的不一致。
 * 戳记使用有限表示，并且不是加密安全的（即有效的戳记可能是可猜测的）。
 * 戳记值可能会在（不超过）连续运行一年后回收重新使用。
 * 未经使用或验证持有超过该期限的戳记可能无法正确验证。
 * StampedLocks是可序列化的，但始终反序列化为初始解锁状态，因此它们对于远程锁定没有用。
 *
 * <p>StampedLock的调度策略并不总是相对于写线程更倾向于读线程，反之亦然
 * 所有“try”方法都是尽力而为，不一定符合任何调度或公平性策略。
 * 任何用于获取或转换锁的“try”方法的零返回值都不会携带有关锁状态的任何信息；随后的调用可能会成功。
 *
 * <p>因为它支持跨多种锁模式的协调使用，所以此类不直接实现{@link Lock}或{@link ReadWriteLock}接口。
 * 但是，在仅需要相关功能集的应用程序中，StampedLock可以被视为 {@link #asReadLock()}，{@link #asWriteLock()}或{@link #asReadWriteLock()}。
 *
 * <p><b>示例用法</b> 下面在一个维护简单二维点的类中说明了一些用法惯用法。
 * 该示例代码说明了一些try/catch约定，尽管这里并不一定要严格遵守它们，因为它们的主体中不会出现异常。
 *
 *  <pre>{@code
 * class Point {
 *   private double x, y;
 *   private final StampedLock sl = new StampedLock();
 *
 *   void move(double deltaX, double deltaY) { // an exclusively locked method
 *     long stamp = sl.writeLock();
 *     try {
 *       x += deltaX;
 *       y += deltaY;
 *     } finally {
 *       sl.unlockWrite(stamp);
 *     }
 *   }
 *
 *   double distanceFromOrigin() { // A read-only method
 *     long stamp = sl.tryOptimisticRead();
 *     double currentX = x, currentY = y;
 *     if (!sl.validate(stamp)) {
 *        stamp = sl.readLock();
 *        try {
 *          currentX = x;
 *          currentY = y;
 *        } finally {
 *           sl.unlockRead(stamp);
 *        }
 *     }
 *     return Math.sqrt(currentX * currentX + currentY * currentY);
 *   }
 *
 *   void moveIfAtOrigin(double newX, double newY) { // upgrade
 *     // Could instead start with optimistic, not read mode
 *     long stamp = sl.readLock();
 *     try {
 *       while (x == 0.0 && y == 0.0) {
 *         long ws = sl.tryConvertToWriteLock(stamp);
 *         if (ws != 0L) {
 *           stamp = ws;
 *           x = newX;
 *           y = newY;
 *           break;
 *         }
 *         else {
 *           sl.unlockRead(stamp);
 *           stamp = sl.writeLock();
 *         }
 *       }
 *     } finally {
 *       sl.unlock(stamp);
 *     }
 *   }
 * }}</pre>
 *  todo 看论文
 * @since 1.8
 * @author Doug Lea
 *  第7位统计读锁的持有数
 *  第8位统计写锁是否被某个线程独占持有
 */
public class StampedLock implements java.io.Serializable {

    /**
     * Algorithmic notes:
     *
     * The design employs elements of Sequence locks
     * (as used in linux kernels; see Lameter's
     * http://www.lameter.com/gelato2005.pdf
     * and elsewhere; see
     * Boehm's http://www.hpl.hp.com/techreports/2012/HPL-2012-68.html)
     * and Ordered RW locks (see Shirako et al
     * http://dl.acm.org/citation.cfm?id=2312015)
     *
     * Conceptually, the primary state of the lock includes a sequence
     * number that is odd when write-locked and even otherwise.
     * However, this is offset by a reader count that is non-zero when
     * read-locked.  The read count is ignored when validating
     * "optimistic" seqlock-reader-style stamps.  Because we must use
     * a small finite number of bits (currently 7) for readers, a
     * supplementary reader overflow word is used when the number of
     * readers exceeds the count field. We do this by treating the max
     * reader count value (RBITS) as a spinlock protecting overflow
     * updates.
     *
     * Waiters use a modified form of CLH lock used in
     * AbstractQueuedSynchronizer (see its internal documentation for
     * a fuller account), where each node is tagged (field mode) as
     * either a reader or writer. Sets of waiting readers are grouped
     * (linked) under a common node (field cowait) so act as a single
     * node with respect to most CLH mechanics.  By virtue of the
     * queue structure, wait nodes need not actually carry sequence
     * numbers; we know each is greater than its predecessor.  This
     * simplifies the scheduling policy to a mainly-FIFO scheme that
     * incorporates elements of Phase-Fair locks (see Brandenburg &
     * Anderson, especially http://www.cs.unc.edu/~bbb/diss/).  In
     * particular, we use the phase-fair anti-barging rule: If an
     * incoming reader arrives while read lock is held but there is a
     * queued writer, this incoming reader is queued.  (This rule is
     * responsible for some of the complexity of method acquireRead,
     * but without it, the lock becomes highly unfair.) Method release
     * does not (and sometimes cannot) itself wake up cowaiters. This
     * is done by the primary thread, but helped by any other threads
     * with nothing better to do in methods acquireRead and
     * acquireWrite.
     *
     * These rules apply to threads actually queued. All tryLock forms
     * opportunistically try to acquire locks regardless of preference
     * rules, and so may "barge" their way in.  Randomized spinning is
     * used in the acquire methods to reduce (increasingly expensive)
     * context switching while also avoiding sustained memory
     * thrashing among many threads.  We limit spins to the head of
     * queue. A thread spin-waits up to SPINS times (where each
     * iteration decreases spin count with 50% probability) before
     * blocking. If, upon wakening it fails to obtain lock, and is
     * still (or becomes) the first waiting thread (which indicates
     * that some other thread barged and obtained lock), it escalates
     * spins (up to MAX_HEAD_SPINS) to reduce the likelihood of
     * continually losing to barging threads.
     *
     * Nearly all of these mechanics are carried out in methods
     * acquireWrite and acquireRead, that, as typical of such code,
     * sprawl out because actions and retries rely on consistent sets
     * of locally cached reads.
     *
     * As noted in Boehm's paper (above), sequence validation (mainly
     * method validate()) requires stricter ordering rules than apply
     * to normal volatile reads (of "state").  To force orderings of
     * reads before a validation and the validation itself in those
     * cases where this is not already forced, we use
     * Unsafe.loadFence.
     *
     * The memory layout keeps lock state and queue pointers together
     * (normally on the same cache line). This usually works well for
     * read-mostly loads. In most other cases, the natural tendency of
     * adaptive-spin CLH locks to reduce memory contention lessens
     * motivation to further spread out contended locations, but might
     * be subject to future improvements.
     *
     *
     * 算法注释：
     *
     * StampedLock的设计采用了序列锁的元素（在Linux内核中使用：请参见Lameter的http://www.lameter.com/gelato2005.pdf和
     * 其他地方：请参见Boehm的http://www.hpl.hp.com/techreports/2012/HPL-2012-68.html）和有序的RW锁（请参见Shirako等人http://dl.acm.org/citation.cfm?id=2312015）

     *
     * 从概念上讲，锁的主要状态包括一个序列号，该序列号在写锁定时是奇数，在其他情况下是偶數。 但
     * 是，当读锁定时，读线程计数非0时，state是偏移量。验证 "optimisitic" seqlock-reader 样式戳记时，将忽略读计数。
     * 因为我们必须为读线程们使用少量的有限位数（当前为7），所以当读线程的数量超过该7个计数位时，将使用补充的读线程溢出字（即readerOverflow字段）。
     * 为此，我们将最大读线程计数值（RBITS）视为保护溢出更新的自旋锁。
     *
     *  等待线程们使用AbstractQueuedSynchronizer使用的CLH锁的修改形式（有关完整的描述，请参阅AQS内部文档），其中每个节点都被标记（field mode）为读线程或写线程。
     *  等待读线程集合被分组（链接）在一个公共节点（field cowait）下，因此相对于大多数CLH机制而言，这一组等待读线程集合充当单个节点。
     *  由于队列结构的原因，等待节点们实际上不需要携带序列号；我们知道每一个等待节点的序列号都比其前继节点更大。
     *  这将调度策略简化为主要是FIFO方案，包含了Phase-Fair locks的元素（请参见Brandenburg＆Anderson，尤其是http://www.cs.unc.edu/~bbb/diss/）。
     *  特别地，我们使用phase-fair  phase-fair anti-barging(反插入)规则：如果在读锁被持有的同时一个新的读线程来了，但同时已经有排队的写线程，则该新来的读线程处于排队状态
     *  。（此规则是造成方法acquireRead的某些复杂性的原因，但是如果没有它，锁将变得非常不公平。）
     *  方法release不会（有时不能）自己唤醒cowaiters。唤醒cowaiters,这是由主线程完成的，但是得到了其他任何线程的帮助，由于在方法acquireRead和acquireWrite中没有更好的事情要做。
     *
     * 这些规则适用于实际排队的线程。所有tryLock形式都会尝试获取锁，而不管偏好规则，因此可能会插到最前面（尝试直接获取到锁）。
     * 随机自旋在获取方法中被使用来减少（越来越昂贵的）上下文切换，同时随机旋转还避免了许多线程之间持续的内存抖动。我们将旋转限制在队列的头节点。
     * 线程在阻塞之前最多自旋等待SPINS次（每次迭代以50％的概率减少自旋计数）。
     * 如果唤醒后它未能获得锁，并且仍然是（或成为）第一个等待线程（这表明其他一些线程已经插进来了并获得了锁），则它将提高自旋次数（最多MAX_HEAD_SPINS）以减少不断丢失到插入线程的可能性。
     *
     * 几乎所有这些机制都是在acquireWrite和acquireRead方法中执行的，这两个方法作为此类代码的典型代表，因为操作和重试依赖于本地缓存的读取的一致集合，它们会扩展。
     * （注释：这就说明了为什么acquireWrite和acquireRead方法里面的代码相对来说特别长）
     *
     * 正如上面Boehm的论文所述，序列验证（主要是validate()方法）要求的顺序规则比应用于正常的volatile读取state的排序规则更严格。
     * 为了在那些顺序不被强制要求的情况下，要确保在验证之前读取和验证本身两者的顺序，我们使用Unsafe.loadFence。
     *
     * 内存布局将锁状态和队列指针保持在一起（通常在同一高速缓存行上）。
     * 这通常适用于读占大多数的负载。在大多数其他情况下，自适应旋转CLH锁减少内存争用的自然趋势，减少了进一步分散竞争位置的动力，但可能会受将来的改进的支配。
     * */

    private static final long serialVersionUID = -6001602636862214147L;

    /** 处理器的数量，为了自旋控制 */
    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    /** 获取锁时，入队之前最多尝试多少次 */
    private static final int SPINS = (NCPU > 1) ? 1 << 6 : 0;

    /** 获取锁时，在头节点head上阻塞之前，最多尝试多少次 */
    private static final int HEAD_SPINS = (NCPU > 1) ? 1 << 10 : 0;

    /** 在重新阻塞之前，最大尝试次数。*/
    private static final int MAX_HEAD_SPINS = (NCPU > 1) ? 1 << 16 : 0;

    /** 等待溢出自旋锁时的yielding时间*/
    private static final int OVERFLOW_YIELD_RATE = 7; // 必须是2的幂-1

    /** 在溢出前用于读线程计数的位数*/
    private static final int LG_READERS = 7;

    // 锁状态和戳记操作相关的数值 Values for lock state and stamp operations
    private static final long RUNIT = 1L; //Read Unit
    //由于低7位给了读线程计数，写线程只能有一个用第8位表示是否写锁是否被占有。
    private static final long WBIT  = 1L << LG_READERS; //Write Bit  1000000
    private static final long RBITS = WBIT - 1L; //Read Bits ,低8位为：1111111
    private static final long RFULL = RBITS - 1L; //Read Full,低8位为：1111110
    private static final long ABITS = RBITS | WBIT; //All Bits 11111111
    private static final long SBITS = ~RBITS; // Synchronization BITS 同步相关位，用于获取写锁状态位。注意与ABITS有重叠（第8位）25个1跟7个0，1111 1111 1111 1111 1111 1111 1000 0000

    // 锁状态的初始值； 避免故障值零Initial value for lock state; avoid failure value zero
    private static final long ORIGIN = WBIT << 1;

    // 取消获取方法中的特殊值，因此调用方可以抛出IE（InterruptedException） Special value from cancelled acquire methods so caller can throw IE
    private static final long INTERRUPTED = 1L;

    // 节点状态的值； 顺序很重要 Values for node status; order matters
    private static final int WAITING   = -1;
    private static final int CANCELLED =  1;

    // 节点的模式（不为布尔值以允许算术） Modes for nodes (int not boolean to allow arithmetic)
    private static final int RMODE = 0;
    private static final int WMODE = 1;

    /** 等待节点 */
    static final class WNode {
        volatile WNode prev;
        volatile WNode next;
        volatile WNode cowait;    // list of linked readers， 连接多个多线程的列表
        volatile Thread thread;   // non-null while possibly parked 阻塞的时候非空
        volatile int status;      // 0, WAITING, or CANCELLED
        final int mode;           // RMODE or WMODE 读还是写
        WNode(int m, WNode p) { mode = m; prev = p; }
    }

    /** Head of CLH queue */
    private transient volatile WNode whead;
    /** Tail (last) of CLH queue */
    private transient volatile WNode wtail;

    // views
    transient ReadLockView readLockView;
    transient WriteLockView writeLockView;
    transient ReadWriteLockView readWriteLockView;

    /** Lock sequence/state */
    private transient volatile long state;
    /** 当state里面的读计数饱和时，额外的读线程计数。extra reader count when state read count saturated */
    private transient int readerOverflow;

    /**
     * 创建一个新的锁，最初处于解锁状态。
     */
    public StampedLock() {
        state = ORIGIN;
    }

    /**
     * 独占地获取写锁，如果有必须就阻塞直到锁可用。
     *
     * @return 可以用来解锁或转换模式的戳记
     */
    public long writeLock() {
        long s, next;  // bypass acquireWrite in fully unlocked case only
        //(((s = state) & ABITS) == 0L 表明state中低8位都为0，也就是既没有读线程，也没有写线程。
        //((s = state) & ABITS) == 0L为true， 则尝试CAS更新state的值
        //      1.如果CAS成功，返回state的最新值
        //      2.如果CAS不成功，则返回调用acquireWrite的返回值。
        return ((((s = state) & ABITS) == 0L &&
                 U.compareAndSwapLong(this, STATE, s, next = s + WBIT)) ?
                next : acquireWrite(false, 0L));
    }

    /**
     * 如果写锁立即可用，则以独占方式获取写锁。
     *
     * @return 可以用来解锁或转换模式的戳记，0表示锁不可用
     */
    public long tryWriteLock() {
        long s, next;
        //(((s = state) & ABITS) == 0L 表明state中低8位都为0，也就是既没有读线程，也没有写线程。
        //((s = state) & ABITS) == 0L为true， 则尝试CAS更新state的值
        //      1.如果CAS成功，返回state的最新值
        //      2.如果CAS不成功，直接返回0，表示尝试不成功。
        return ((((s = state) & ABITS) == 0L &&
                 U.compareAndSwapLong(this, STATE, s, next = s + WBIT)) ?
                next : 0L);
    }

    /**
     * 如果写锁在给定时间内可用并且当前线程尚未中断，则以独占方式获取该写锁。
     * 超时和中断下的行为与与方法{@link Lock#tryLock(long, TimeUnit)}指定的行为一致。
     * @param time the maximum time to wait for the lock
     * @param unit the time unit of the {@code time} argument
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    public long tryWriteLock(long time, TimeUnit unit)
        throws InterruptedException {
        long nanos = unit.toNanos(time);
        if (!Thread.interrupted()) {
            long next, deadline;
            //尝试直接获取写锁
            if ((next = tryWriteLock()) != 0L)
                return next;
            if (nanos <= 0L)
                return 0L;
            if ((deadline = System.nanoTime() + nanos) == 0L)
                deadline = 1L;
            if ((next = acquireWrite(true, deadline)) != INTERRUPTED)
                return next;
        }
        throw new InterruptedException();
    }

    /**
     * 独占地获取写锁，必要时阻塞，直到写锁可用或当前线程中断为止。
     * 中断下的行为与方法 {@link Lock#lockInterruptibly()}指定的行为匹配。
     *
     * @return a stamp that can be used to unlock or convert mode
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    public long writeLockInterruptibly() throws InterruptedException {
        long next;
        if (!Thread.interrupted() &&
            (next = acquireWrite(true, 0L)) != INTERRUPTED)
            return next;
        throw new InterruptedException();
    }

    /**
     * Non-exclusively acquires the lock, blocking if necessary
     * until available.
     * 非排他性地获取锁，必要时阻塞直到可用。
     *
     * @return 可以用于解锁或转换模式的戳记
     */
    public long readLock() {
        long s = state, next;  // bypass acquireRead on common uncontended case
        //s & ABITS) < RFULL 表明没有设置过写锁
        // U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))  尝试CAS直接设置state值
        //  如果成功的话，就返回next值
        //  如果不成功的话，则调用acquireRead
        return ((whead == wtail && (s & ABITS) < RFULL &&
                 U.compareAndSwapLong(this, STATE, s, next = s + RUNIT)) ?
                next : acquireRead(false, 0L));
    }

    /**
     * 如果读锁立即可用，则以非排他方式获取读锁。
     *
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     */
    public long tryReadLock() {
        for (;;) {
            long s, m, next;
            //如果写锁被某个线程持有
            if ((m = (s = state) & ABITS) == WBIT)
                return 0L;
            //state上的读锁持有数未满，
            else if (m < RFULL) {
                //CAS，尝试给state加RUNIT，表明读锁持有数增加1.
                if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))
                    return next;
            }
            //state上的读锁持有数已满，尝试使用readOverFlow帮忙统计额外的读锁持有数。
            else if ((next = tryIncReaderOverflow(s)) != 0L)
                return next;
        }
    }

    /**
     * 如果读锁在给定时间内可用并且当前线程尚未中断，则以非排他方式获取该读锁。
     * 超时和中断下的行为与为方法{@link Lock#tryLock(long,TimeUnit)}.指定的行为匹配。
     * @param time the maximum time to wait for the lock
     * @param unit the time unit of the {@code time} argument
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    public long tryReadLock(long time, TimeUnit unit)
        throws InterruptedException {
        long s, m, next, deadline;
        long nanos = unit.toNanos(time);
        if (!Thread.interrupted()) {
            //如果未持有写锁，第8位不为1
            if ((m = (s = state) & ABITS) != WBIT) {
                //state上的读锁持有数未满，
                if (m < RFULL) {
                    //CAS，尝试给state加RUNIT，表明读锁持有数增加1.
                    if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))
                        return next;
                }
                //state上的读锁持有数已满，尝试使用readOverFlow帮忙统计额外的读锁持有数。
                else if ((next = tryIncReaderOverflow(s)) != 0L)
                    return next;
            }
            if (nanos <= 0L)
                return 0L;
            if ((deadline = System.nanoTime() + nanos) == 0L)
                deadline = 1L;
            if ((next = acquireRead(true, deadline)) != INTERRUPTED)
                return next;
        }
        throw new InterruptedException();
    }

    /**
     * 非排他地获取读锁，必要时阻塞，直到读锁可用或当前线程中断为止。
     * 处于中断状态的行为与为方法{@link Lock#lockInterruptibly()}指定的行为匹配。
     *
     * @return a stamp that can be used to unlock or convert mode
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    public long readLockInterruptibly() throws InterruptedException {
        long next;
        if (!Thread.interrupted() &&
            (next = acquireRead(true, 0L)) != INTERRUPTED)
            return next;
        throw new InterruptedException();
    }

    /**
     * Returns a stamp that can later be validated, or zero
     * if exclusively locked.
     * 返回稍后可以验证的戳记；如果排他锁定，则返回零。
     *
     * @return a stamp, or zero if exclusively locked
     */
    public long tryOptimisticRead() {
        long s;
        //((s = state) & WBIT) == 0L
        //      如果为true，则表明无写锁，然后返回 s & SBITS 保留了除了第7位的位，这是因为读锁可以共享，所以第7位可以随便变，而其他位则不行。
        //      如果为false，则返回0

        return (((s = state) & WBIT) == 0L) ? (s & SBITS) : 0L;
    }

    /**
     * Returns true if the lock has not been exclusively acquired
     * since issuance of the given stamp. Always returns false if the
     * stamp is zero. Always returns true if the stamp represents a
     * currently held lock. Invoking this method with a value not
     * obtained from {@link #tryOptimisticRead} or a locking method
     * for this lock has no defined effect or result.
     * 如果自发放给定戳记以来，还没有线程独占获得写锁，则返回true。
     * 如果戳记为零，则始终返回false。
     * 如果戳记代表当前持有的锁，则始终返回true。
     * 使用未从{@link #tryOptimisticRead}获得的值调用此方法或此锁的锁定方法没有定义的效果或结果。
     *
     * @param stamp a stamp
     * @return {@code true} 如果自发放给定戳记以来，还没有线程独占获得该锁，则返回true，否则返回false。
     */
    public boolean validate(long stamp) {
        // 确保屏障前的loads不会与屏障后的loads或stores重排序。
        U.loadFence();
        //因为上面的loadFence, 保证下面的state的加载（load）不会被重排序到之前屏障之前。
        //比较高25位是否相同, 忽略用来表示读锁持有数的低7位。
        return (stamp & SBITS) == (state & SBITS);
    }

    /**
     * If the lock state matches the given stamp, releases the
     * exclusive lock.
     * 如果锁状态与给定的戳记相匹配，则释放互斥锁。
     * @param stamp 有writeLock操作返回的戳记
     * @throws IllegalMonitorStateException 如果戳记与此锁的当前状态不匹配
     */
    public void unlockWrite(long stamp) {
        WNode h;
        //如果stamp不等于state的值，或者写锁位为0。
        if (state != stamp || (stamp & WBIT) == 0L)
            throw new IllegalMonitorStateException();
        //更新state的值
        state = (stamp += WBIT) == 0L ? ORIGIN : stamp;
        //唤醒后继节点对应的线程。
        if ((h = whead) != null && h.status != 0)
            release(h);
    }

    /**
     * If the lock state matches the given stamp, releases the
     * non-exclusive lock.
     * 如果锁状态与给定的戳记匹配，则释放非排他锁（读锁，共享锁）。
     *
     * @param stamp a stamp returned by a read-lock operation
     * @throws IllegalMonitorStateException if the stamp does
     * not match the current state of this lock
     */
    public void unlockRead(long stamp) {
        long s, m; WNode h;
        for (;;) {
            //如果下面四者之一为true，抛出IllegalMonitorStateException异常
            //   高25位，即读锁相关位前后不一致;或者
            //    (stamp & ABITS) == 0L，即stamp低8位都为0，即表明未获取读写锁；或者
            //      (m = s & ABITS) == 0L，即最新state快照值第8位都为0，也就是读锁持有数已经被其他线程减少（读锁已经被其他线程释放）或者
            //     m == WBIT，即写锁被某个线程占有
            if (((s = state) & SBITS) != (stamp & SBITS) ||
                (stamp & ABITS) == 0L || (m = s & ABITS) == 0L || m == WBIT)
                throw new IllegalMonitorStateException();
            //如果写相关位未满，即低7位小于1111110
            if (m < RFULL) {
                //CAS 减少state值
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    //如果m为最后一个写锁持有计数，则尝试释放头节点whead。
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    break;
                }
            }
            //如果写相关为已满，则尝试减少readerOverflow的值。
            else if (tryDecReaderOverflow(s) != 0L)
                break;
        }
    }

    /**
     * 如果锁状态与给定的戳记匹配，则释放相应的锁模式。
     *
     * @param stamp a stamp returned by a lock operation
     * @throws IllegalMonitorStateException if the stamp does
     * not match the current state of this lock
     */
    public void unlock(long stamp) {
        long a = stamp & ABITS, m, s; WNode h;
        while (((s = state) & SBITS) == (stamp & SBITS)) { //state高25位是否发生变更，即写锁相关位是否发生变更。
            if ((m = s & ABITS) == 0L)//如果低8位都为0，即既没有写锁，也没有读锁
                break;
            else if (m == WBIT) { //当前状态为写锁被某个线程占有
                if (a != m)
                    break;
                state = (s += WBIT) == 0L ? ORIGIN : s;
                if ((h = whead) != null && h.status != 0)
                    release(h);
                return;
            }
            else if (a == 0L || a >= WBIT)
                break;
            else if (m < RFULL) { //当前状态只有读锁被持有，写锁没有
                //CAS将state减少RUNIT
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    //如果当前加锁状态是最后一个读锁
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        //唤醒h的后继节点。
                        release(h);
                    return;
                }
            }
            //如果读锁持有数溢出，则尝试将readOverFlow减少
            else if (tryDecReaderOverflow(s) != 0L)
                return;
        }
        throw new IllegalMonitorStateException();
    }

    /**
     * If the lock state matches the given stamp, performs one of
     * the following actions. If the stamp represents holding a write
     * lock, returns it.  Or, if a read lock, if the write lock is
     * available, releases the read lock and returns a write stamp.
     * Or, if an optimistic read, returns a write stamp only if
     * immediately available. This method returns zero in all other
     * cases.
     * 如果锁状态与给定的戳记相匹配，执行以下操作之一。
     * 如果该戳记表示持有写锁，则将其返回。
     * 或者，如果该戳记表示持有读锁，如果此时写锁可用，则释放该读锁并返回一个写戳记。
     * 或者，如果是乐观读取，则仅在锁立即可用时才返回写标记。
     *
     * 在所有其他情况下，此方法均返回零。
     * @param stamp a stamp
     * @return a valid write stamp, or zero on failure
     */
    public long tryConvertToWriteLock(long stamp) {
        long a = stamp & ABITS, m, s, next;
        //判断前后SBITS对应的高25值是否发生变更，也就是写锁相关状态是否发生变更。
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((m = s & ABITS) == 0L) { //表明读写锁都已经释放。
                if (a != 0L) //表明该stamp已经过期，按理是应该都释放读写锁，也就是为0的。
                    break;
                if (U.compareAndSwapLong(this, STATE, s, next = s + WBIT)) //尝试CAS设置写锁状态位为1，
                    return next;
            }
            else if (m == WBIT) { //如果写锁已经被占有
                if (a != m) //写锁不是被当前调用线程占有，跳出循环最后返回0.
                    break;
                return stamp;
            }
            else if (m == RUNIT && a != 0L) { //只有读锁被占有且最后一个读锁被释放
                //尝试CAS设置写锁状态位为1
                if (U.compareAndSwapLong(this, STATE, s,
                                         next = s - RUNIT + WBIT))
                    return next;
            }
            else
                break;
        }
        return 0L;
    }

    /**
     * If the lock state matches the given stamp, performs one of
     * the following actions. If the stamp represents holding a write
     * lock, releases it and obtains a read lock.  Or, if a read lock,
     * returns it. Or, if an optimistic read, acquires a read lock and
     * returns a read stamp only if immediately available. This method
     * returns zero in all other cases.
     * 如果锁状态与给定的戳记相匹配，执行以下操作之一。
     * 如果戳记表示持有一个写锁，那么就释放它然后获得读锁。
     * 或者，如果有读锁，则将其返回。
     * 或者，如果是乐观读，则仅在读锁立即可用时获取读锁并返回读取戳记。
     *
     * 在所有其他情况下，此方法均返回零。
     *
     * @param stamp a stamp
     * @return a valid read stamp, or zero on failure
     */
    public long tryConvertToReadLock(long stamp) {
        long a = stamp & ABITS, m, s, next; WNode h;
        //同步相关位是否发生变更，即高25位是否发生变更
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((m = s & ABITS) == 0L) {//如果当前读写锁未被持有
                if (a != 0L) //如果stamp代表读写锁有被持有，不一致
                    break;
                else if (m < RFULL) { //当前低7位代表的读持有数小于RFULL
                    //CAS给state上的读持有数增加RUNIT
                    if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))
                        return next;
                }
                //当前低7位代表的读持有数大于等于RFULL
                else if ((next = tryIncReaderOverflow(s)) != 0L)
                    return next;
            }
            else if (m == WBIT) {//写锁被占有，没有读锁被占有
                if (a != m) //读写锁相关位发生变更
                    break;
                state = next = s + (WBIT + RUNIT);
                if ((h = whead) != null && h.status != 0)
                    // 唤醒h的后继者对应的线程
                    release(h);
                return next;
            }
            else if (a != 0L && a < WBIT) //只有读锁被持有，写锁没有被独占。
                return stamp;
            else
                break;
        }
        return 0L;
    }

    /**
     * If the lock state matches the given stamp then, if the stamp
     * represents holding a lock, releases it and returns an
     * observation stamp.  Or, if an optimistic read, returns it if
     * validated. This method returns zero in all other cases, and so
     * may be useful as a form of "tryUnlock".
     * 如果锁状态与给定的戳记匹配
     *      则如果该戳记表示持有一把锁，则将其释放并返回观察戳记。
     *      或者，如果是乐观读，如果经过验证，则返回结果。
     * 在所有其他情况下，此方法都返回零，因此可能作为“tryUnlock”的一种形式有用。
     *
     * @param stamp a stamp
     * @return a valid optimistic read stamp, or zero on failure
     */
    public long tryConvertToOptimisticRead(long stamp) {
        long a = stamp & ABITS, m, s, next; WNode h;
        //load栅栏，确保屏障前的loads不会与屏障后的loads或stores重排序。
        U.loadFence();
        for (;;) {
            //如果同步状态相关位（高25位）发生变更
            if (((s = state) & SBITS) != (stamp & SBITS))
                break;
            //如果当前没有持有读锁或写锁。
            if ((m = s & ABITS) == 0L) {
                if (a != 0L)//如果过去的stamp持有读锁或写锁
                    break;
                return s;
            }
            else if (m == WBIT) {//如果只持有写锁
                if (a != m) //如果过去的stamp未持有写锁
                    break;
                state = next = (s += WBIT) == 0L ? ORIGIN : s;
                if ((h = whead) != null && h.status != 0)
                    release(h);
                return next;
            }
            else if (a == 0L || a >= WBIT)
                break;
            else if (m < RFULL) { //如果只有读锁，且state上读锁持有数未满
                if (U.compareAndSwapLong(this, STATE, s, next = s - RUNIT)) {
                    //如果m是最后一个持有读锁的
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        //唤醒h的后继节点。
                        release(h);
                    return next & SBITS;
                }
            }
            else if ((next = tryDecReaderOverflow(s)) != 0L)
                return next & SBITS;
        }
        return 0L;
    }

    /**
     * Releases the write lock if it is held, without requiring a
     * stamp value. This method may be useful for recovery after
     * errors.
     * 释放写锁（如果写锁已被持有），而无需戳记值。 此方法对于出错后的恢复可能很有用。
     *
     * @return {@code true} if the lock was held, else false
     */
    public boolean tryUnlockWrite() {
        long s; WNode h;
        if (((s = state) & WBIT) != 0L) { //如果写锁被某个线程持有（不一定是当前线程）
            state = (s += WBIT) == 0L ? ORIGIN : s;
            if ((h = whead) != null && h.status != 0)
                //释放h的后继节点
                release(h);
            return true;
        }
        return false;
    }

    /**
     * Releases one hold of the read lock if it is held, without
     * requiring a stamp value. This method may be useful for recovery
     * after errors.
     * 释放读锁的一个持有数（如果读锁已被持有），而无需戳记值。 此方法对于出错后的恢复可能很有用。
     *
     * @return {@code true} if the read lock was held, else false
     */
    public boolean tryUnlockRead() {
        long s, m; WNode h;
        while ((m = (s = state) & ABITS) != 0L && m < WBIT) { // 如果持有读锁
            if (m < RFULL) { //如果state上的读锁持有位（第7位）的值小于RFULL
                //CAS 尝试给state的值减RUNIT
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    //如果是最后一个读锁持有数被释放
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        //唤醒h的后继节点对应的线程
                        release(h);
                    return true;
                }
            }
            //如果state上的读锁持有位满了，即>=RFULL, 则尝试减少readOverFlow。
            else if (tryDecReaderOverflow(s) != 0L)
                return true;
        }
        return false;
    }

    // status monitoring methods

    /**
     * 返回对于给定state s 结合state持有值和readerOverflow的读锁持有数。
     * Returns combined state-held and overflow read count for given
     * state s.
     */
    private int getReadLockCount(long s) {
        long readers;
        if ((readers = s & RBITS) >= RFULL)
            readers = RFULL + readerOverflow;
        return (int) readers;
    }

    /**
     * Returns {@code true} if the lock is currently held exclusively.
     *
     * @return {@code true} if the lock is currently held exclusively
     */
    public boolean isWriteLocked() {
        return (state & WBIT) != 0L;
    }

    /**
     * Returns {@code true} if the lock is currently held non-exclusively.
     *
     * @return {@code true} if the lock is currently held non-exclusively
     */
    public boolean isReadLocked() {
        return (state & RBITS) != 0L;
    }

    /**
     * Queries the number of read locks held for this lock. This
     * method is designed for use in monitoring system state, not for
     * synchronization control.
     * @return the number of read locks held
     */
    public int getReadLockCount() {
        return getReadLockCount(state);
    }

    /**
     * Returns a string identifying this lock, as well as its lock
     * state.  The state, in brackets, includes the String {@code
     * "Unlocked"} or the String {@code "Write-locked"} or the String
     * {@code "Read-locks:"} followed by the current number of
     * read-locks held.
     *
     * @return a string identifying this lock, as well as its lock state
     */
    public String toString() {
        long s = state;
        return super.toString() +
            ((s & ABITS) == 0L ? "[Unlocked]" :
             (s & WBIT) != 0L ? "[Write-locked]" :
             "[Read-locks:" + getReadLockCount(s) + "]");
    }

    // views

    /**
     * Returns a plain {@link Lock} view of this StampedLock in which
     * the {@link Lock#lock} method is mapped to {@link #readLock},
     * and similarly for other methods. The returned Lock does not
     * support a {@link Condition}; method {@link
     * Lock#newCondition()} throws {@code
     * UnsupportedOperationException}.
     *
     * @return the lock
     */
    public Lock asReadLock() {
        ReadLockView v;
        return ((v = readLockView) != null ? v :
                (readLockView = new ReadLockView()));
    }

    /**
     * Returns a plain {@link Lock} view of this StampedLock in which
     * the {@link Lock#lock} method is mapped to {@link #writeLock},
     * and similarly for other methods. The returned Lock does not
     * support a {@link Condition}; method {@link
     * Lock#newCondition()} throws {@code
     * UnsupportedOperationException}.
     *
     * @return the lock
     */
    public Lock asWriteLock() {
        WriteLockView v;
        return ((v = writeLockView) != null ? v :
                (writeLockView = new WriteLockView()));
    }

    /**
     * Returns a {@link ReadWriteLock} view of this StampedLock in
     * which the {@link ReadWriteLock#readLock()} method is mapped to
     * {@link #asReadLock()}, and {@link ReadWriteLock#writeLock()} to
     * {@link #asWriteLock()}.
     *
     * @return the lock
     */
    public ReadWriteLock asReadWriteLock() {
        ReadWriteLockView v;
        return ((v = readWriteLockView) != null ? v :
                (readWriteLockView = new ReadWriteLockView()));
    }

    // view classes
    //实现Lock接口，将对ReadLockView的方法调用转移给StampedLock里面的方法
    final class ReadLockView implements Lock {
        public void lock() { readLock(); }
        public void lockInterruptibly() throws InterruptedException {
            readLockInterruptibly();
        }
        public boolean tryLock() { return tryReadLock() != 0L; }
        public boolean tryLock(long time, TimeUnit unit)
            throws InterruptedException {
            return tryReadLock(time, unit) != 0L;
        }
        public void unlock() { unstampedUnlockRead(); }
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }
    //实现Lock接口，将对WriteLockView的方法调用转移给StampedLock里面的方法
    final class WriteLockView implements Lock {
        public void lock() { writeLock(); }
        public void lockInterruptibly() throws InterruptedException {
            writeLockInterruptibly();
        }
        public boolean tryLock() { return tryWriteLock() != 0L; }
        public boolean tryLock(long time, TimeUnit unit)
            throws InterruptedException {
            return tryWriteLock(time, unit) != 0L;
        }
        public void unlock() { unstampedUnlockWrite(); }
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }
    //实现ReadWriteLock接口，将对WriteLockView的方法调用转移给StampedLock里面的方法
    final class ReadWriteLockView implements ReadWriteLock {
        public Lock readLock() { return asReadLock(); }
        public Lock writeLock() { return asWriteLock(); }
    }

    // Unlock methods without stamp argument checks for view classes.
    // Needed because view-class lock methods throw away stamps.
    // 无需使用戳记参数检查即可解锁视图类的方法。
    // 因为视图类锁方法会丢弃戳记，所以需要这些方法。

    final void unstampedUnlockWrite() {
        WNode h; long s;
        if (((s = state) & WBIT) == 0L) //如果state第8位没有被设置为1
            throw new IllegalMonitorStateException();
        state = (s += WBIT) == 0L ? ORIGIN : s;
        if ((h = whead) != null && h.status != 0)
            release(h);
    }

    final void unstampedUnlockRead() {
        for (;;) {
            long s, m; WNode h;
            if ((m = (s = state) & ABITS) == 0L || m >= WBIT) //如果state低8位为0或者m>=WBIT表明写锁被占有
                throw new IllegalMonitorStateException();
            else if (m < RFULL) { //如果state上的读锁持有位（第7位）的值小于RFULL
                //CAS 尝试给state的值减RUNIT
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    //如果是最后一个读锁持有数被释放
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        //唤醒h的后继节点对应的线程
                        release(h);
                    break;
                }
            }
            //如果state上的读锁持有位满了，即>=RFULL, 则尝试减少readOverFlow。
            else if (tryDecReaderOverflow(s) != 0L)
                break;
        }
    }

    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        state = ORIGIN; // reset to unlocked state
    }

    // internals

    /**
     * Tries to increment readerOverflow by first setting state
     * access bits value to RBITS, indicating hold of spinlock,
     * then updating, then releasing.
     * 尝试通过先将状态访问位的值设置为RBITS来指示增加自旋锁，然后进行更新然后释放，以增加readerOverflow。
     *
     * @param s a reader overflow stamp: (s & ABITS) >= RFULL
     * @return new stamp on success, else zero
     */
    private long tryIncReaderOverflow(long s) {
        // assert (s & ABITS) >= RFULL;
        if ((s & ABITS) == RFULL) {
            if (U.compareAndSwapLong(this, STATE, s, s | RBITS)) {
                ++readerOverflow;
                state = s;
                return s;
            }
        }
        else if ((LockSupport.nextSecondarySeed() &
                  OVERFLOW_YIELD_RATE) == 0)
            Thread.yield();
        return 0L;
    }

    /**
     * 尝试减少readerOverflow
     *
     * @param s a reader overflow stamp: (s & ABITS) >= RFULL
     * @return new stamp on success, else zero
     */
    private long tryDecReaderOverflow(long s) {
        // assert (s & ABITS) >= RFULL;
        if ((s & ABITS) == RFULL) {
            //尝试CAS，将s低7位置为1，其他位不变。
            if (U.compareAndSwapLong(this, STATE, s, s | RBITS)) {
                int r; long next;
                if ((r = readerOverflow) > 0) {
                    readerOverflow = r - 1;
                    next = s;
                }
                else
                    next = s - RUNIT;
                 state = next;
                 return next;
            }
        }
        else if ((LockSupport.nextSecondarySeed() &
                  OVERFLOW_YIELD_RATE) == 0)
            Thread.yield();
        return 0L;
    }

    /**
     * Wakes up the successor of h (normally whead). This is normally
     * just h.next, but may require traversal from wtail if next
     * pointers are lagging. This may fail to wake up an acquiring
     * thread when one or more have been cancelled, but the cancel
     * methods themselves provide extra safeguards to ensure liveness.
     * 唤醒h（通常为whead）的后继节点。
     * 这通常只是h.next，但如果下一个指针滞后，则可能需要从wtail向前遍历。
     * 取消一个或多个线程时，这可能无法唤醒获取线程，但是cancel方法本身提供了额外的保障以确保活性。
     */
    private void release(WNode h) {
        if (h != null) {
            //q指向h真正的后继节点（未取消的）
            WNode q; Thread w;
            //尝试CAS更新WNode h的状态，如果实际值等于期望值WAITING，则更新为新值0
            U.compareAndSwapInt(h, WSTATUS, WAITING, 0);
            if ((q = h.next) == null || q.status == CANCELLED) {
                for (WNode t = wtail; t != null && t != h; t = t.prev)
                    if (t.status <= 0)
                        q = t;
            }
            if (q != null && (w = q.thread) != null)
                U.unpark(w);
        }
    }

    /**
     * See above for explanation.
     *
     * @param interruptible true if should check interrupts and if so
     * return INTERRUPTED
     * @param deadline if nonzero, the System.nanoTime value to timeout
     * at (and return zero)
     * @return next state, or INTERRUPTED
     */
    private long acquireWrite(boolean interruptible, long deadline) {
        //node为当前写线程节点；p为指针，指向尾节点的快照。
        WNode node = null, p;
        for (int spins = -1;;) { // spin while enqueuing
            //m 用来记录低8为的锁的状态。
            //s 用来存储state的快照
            //ns 表示新的newState，也就是保存state的最新值。
            long m, s, ns;
            //((m = (s = state) & ABITS) == 0L表明state中低8位都为0，也就是既没有读线程，也没有写线程。
            if ((m = (s = state) & ABITS) == 0L) {
                //尝试更新state，成功表明获取写锁，并返回最新的state值。
                if (U.compareAndSwapLong(this, STATE, s, ns = s + WBIT))
                    return ns;
            }
            else if (spins < 0)
                //给spins赋值，m等于WBIT，也就是只有写锁，wtail == whead,也就是CLH 队列中只有一个节点，那么就给spins赋值为SPINS，否则赋值为0
                spins = (m == WBIT && wtail == whead) ? SPINS : 0;
            else if (spins > 0) {
                //消耗spins
                if (LockSupport.nextSecondarySeed() >= 0)
                    --spins;
            }
            //只有spins为0才会执行到下面的这些判断
            else if ((p = wtail) == null) { // 初始化队列
                //如果尾节点为null，表明CLH队列还是空的，下面就尝试初始化该队列。
                WNode hd = new WNode(WMODE, null);
                //CAS设置头节点，如果头节点实际值为期望值null，则更新为新值hd。
                if (U.compareAndSwapObject(this, WHEAD, null, hd))
                    wtail = hd;
            }
            else if (node == null) //如果p指向的尾节点不为空，node为空，就新建一个写模式的节点。
                node = new WNode(WMODE, p);
            else if (node.prev != p)
                node.prev = p;
            else if (U.compareAndSwapObject(this, WTAIL, p, node)) { //尝试CAS更新尾节点，如果尾节点实际值为期望值p，则更新为新值node,并跳出循环。
                p.next = node;
                break;
            }
        }

        //只有执行上一层最后一个else if (U.compareAndSwapObject(this, WTAIL, p, node)) 成功，才会执行下面的逻辑，也就是添加了新的节点作为尾节点。
        for (int spins = -1;;) {
            //注意，执行到这里时p仍然指向旧的尾节点，也就是是新尾节点的前继节点。
            //h指向头节点快照
            //np, node-prev的简称，即节点的前继节点
            //pp, 为上面p节点（指向尾节点快照）的前继节点，即p-prev的简称
            //ps， 节点p的状态
            WNode h, np, pp; int ps;
            //如果头节点快照为p,即尾节点的前继节点。
            if ((h = whead) == p) {
                if (spins < 0)
                    spins = HEAD_SPINS;
                else if (spins < MAX_HEAD_SPINS)
                    spins <<= 1;

                //在头节点上自旋。
                for (int k = spins;;) { // spin at head
                    long s, ns; //s存state的快照，ns存state的新值
                    //如果state中低8位都为0，也就是既没有读线程，也没有写线程。
                    if (((s = state) & ABITS) == 0L) {
                        //那么就尝试CAS更新state的值
                        if (U.compareAndSwapLong(this, STATE, s,
                                                 ns = s + WBIT)) {
                            //如果CAS成功，则表明获取写锁成功，则更新头节点，返回新的state值。
                            whead = node;
                            node.prev = null;
                            return ns;
                        }
                    }
                    //自旋，spins用完了，就跳出循环
                    else if (LockSupport.nextSecondarySeed() >= 0 &&
                             --k <= 0)
                        break;
                }
            }
            else if (h != null) { // help release stale waiters 帮助释放陈旧的等待线程
                WNode c; Thread w;
                while ((c = h.cowait) != null) {
                    //尝试CAS地将h里面的cowait字段值，从c更新为c.cowait
                    if (U.compareAndSwapObject(h, WCOWAIT, c, c.cowait) &&
                        (w = c.thread) != null)
                        //取消阻塞线程w。
                        U.unpark(w);
                }
            }
            //如果头节点快照h与头节点仍然是指向同一个对象，即在此期间头节点没有更新
            if (whead == h) {
                //如果新节点的prev已经不等于p，则表明p过期了，需要重新指向node.prev
                if ((np = node.prev) != p) {
                    if (np != null)
                        (p = np).next = node;   // stale
                }
                //如果p的状态为0，则更新为WAITING
                else if ((ps = p.status) == 0)
                    U.compareAndSwapInt(p, WSTATUS, 0, WAITING);
                //如果p的状态为CANCELLED,尝试更新node的prev跳过这个被CANCELLED的节点。
                else if (ps == CANCELLED) {
                    if ((pp = p.prev) != null) {
                        node.prev = pp;
                        pp.next = node;
                    }
                }
                else {
                    long time; // 0 argument to park means no timeout
                    if (deadline == 0L)
                        time = 0L;
                    else if ((time = deadline - System.nanoTime()) <= 0L)
                        return cancelWaiter(node, node, false);
                    Thread wt = Thread.currentThread();
                    U.putObject(wt, PARKBLOCKER, this);
                    node.thread = wt;
                    if (p.status < 0 && (p != h || (state & ABITS) != 0L) &&
                        whead == h && node.prev == p)
                        U.park(false, time);  // 模仿 LockSupport.park
                    node.thread = null;
                    U.putObject(wt, PARKBLOCKER, null);
                    if (interruptible && Thread.interrupted())
                        return cancelWaiter(node, node, true);
                }
            }
        }
    }

    /**
     * See above for explanation.
     *
     * @param interruptible  如果应该检查中断并返回INTERRUPTED，则返回true
     * @param deadline 如果不为零，则System.nanoTime值将在deadline处超时（并返回零）
     * @return 下一个state值，或者被中断。 next state, or INTERRUPTED
     */
    private long acquireRead(boolean interruptible, long deadline) {
        WNode node = null, p; //node新增的节点，p最终指向node的前继节点
        for (int spins = -1;;) {
            WNode h;
            if ((h = whead) == (p = wtail)) {
                //m 为锁模式,也就是记录低8位的值
                //s 为state的快照值
                //ns 为新的state值
                for (long m, s, ns;;) {
                    //下面的if尝试获取读锁，并增加计数
                    //(m = (s = state) & ABITS) < RFULL
                    //      如果为true，则表明没有线程获取写锁，且读锁持有数未满；然后尝试CAS给state加1，也就是读锁持有数加1.
                    //      如果为false，则(m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L)，意思是只有写锁没有被占有并且第7位已经全是1，才会去尝试增加readerOverflow的计数
                    if ((m = (s = state) & ABITS) < RFULL ?
                        U.compareAndSwapLong(this, STATE, s, ns = s + RUNIT) :
                        (m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L))
                        //上面二者有一个成功的就返回新的state值，表明增加读锁持有数成功。
                        return ns;
                    else if (m >= WBIT) { //如果写锁被某个线程持有
                        if (spins > 0) { //自旋
                            if (LockSupport.nextSecondarySeed() >= 0)
                                --spins;
                        }
                        else {
                            if (spins == 0) {
                                //自旋结束，尝试跳出循环。
                                // 如果首尾节点没有变更或者首尾节点不等，就跳出里面这层循环（注意这里跳出后，h，p分别是最新的头尾节点快照值）
                                WNode nh = whead, np = wtail;
                                if ((nh == h && np == p) || (h = nh) != (p = np))
                                    break;
                            }
                            //第一次spins为-1，只会执行下面的赋值。
                            spins = SPINS;
                        }
                    }
                }
            }
            if (p == null) { // 初始化队列。
                WNode hd = new WNode(WMODE, null);
                if (U.compareAndSwapObject(this, WHEAD, null, hd))
                    wtail = hd;
            }
            else if (node == null)
                node = new WNode(RMODE, p);
            else if (h == p || p.mode != RMODE) { //如果尾节点的模式不为读锁模式，将新节点添加到队列上，作为新的尾节点。
                if (node.prev != p)
                    node.prev = p;
                else if (U.compareAndSwapObject(this, WTAIL, p, node)) {
                    p.next = node;
                    break;
                }
            }
            // 尝试CAS更新p的cowait为新值node，如果实际值等于p.cowait.
            else if (!U.compareAndSwapObject(p, WCOWAIT,
                                             node.cowait = p.cowait, node))
                node.cowait = null;
            else {
                for (;;) {
                    WNode pp, c; Thread w;
                    if ((h = whead) != null && (c = h.cowait) != null &&
                        U.compareAndSwapObject(h, WCOWAIT, c, c.cowait) &&
                        (w = c.thread) != null) // help release
                        U.unpark(w);
                    if (h == (pp = p.prev) || h == p || pp == null) {
                        long m, s, ns;
                        do {
                            // 下面的if尝试增加读锁计数。
                            // (m = (s = state) & ABITS) < RFULL
                            //      如果为true，则表明没有线程获取写锁，且读锁持有数未满；然后尝试CAS给state加1，也就是读锁持有数加1.
                            //      如果为false，则(m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L)，意思是只有写锁没有被占有并且第7位已经全是1，才会去尝试增加readerOverflow的计数
                            if ((m = (s = state) & ABITS) < RFULL ?
                                U.compareAndSwapLong(this, STATE, s,
                                                     ns = s + RUNIT) :
                                (m < WBIT &&
                                 (ns = tryIncReaderOverflow(s)) != 0L))
                                return ns;
                        } while (m < WBIT);
                    }


                    if (whead == h && p.prev == pp) {
                        long time;
                        if (pp == null || h == p || p.status > 0) {
                            node = null; // throw away
                            break;
                        }
                        if (deadline == 0L)
                            time = 0L;
                        //如果截止时间deadline过了，则需要取消等待。
                        else if ((time = deadline - System.nanoTime()) <= 0L)
                            return cancelWaiter(node, p, false);
                        Thread wt = Thread.currentThread();
                        U.putObject(wt, PARKBLOCKER, this);
                        node.thread = wt;
                        if ((h != pp || (state & ABITS) == WBIT) &&
                            whead == h && p.prev == pp)
                            U.park(false, time);
                        node.thread = null;
                        U.putObject(wt, PARKBLOCKER, null);
                        if (interruptible && Thread.interrupted())
                            //如果当前线程被中断了，也需求取消等待。
                            return cancelWaiter(node, p, true);
                    }
                }
            }
        }
        for (int spins = -1;;) {
            WNode h, np, pp; int ps; //h 为whead的快照值，np指向node prev，即node的前继节点，pp为prev->prev，即弄的节点的前继节点的前继节点
            if ((h = whead) == p) {
                if (spins < 0)
                    spins = HEAD_SPINS;
                else if (spins < MAX_HEAD_SPINS)
                    spins <<= 1;
                for (int k = spins;;) { // spin at head
                    long m, s, ns;
                    //下面的if尝试获取读锁，并增加计数
                    //(m = (s = state) & ABITS) < RFULL
                    //      如果为true，则表明没有线程获取写锁，且读锁持有数未满；然后尝试CAS给state加1，也就是读锁持有数加1.
                    //      如果为false，则(m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L)，意思是只有写锁没有被占有并且第7位已经全是1，才会去尝试增加readerOverflow的计数
                    if ((m = (s = state) & ABITS) < RFULL ?
                        U.compareAndSwapLong(this, STATE, s, ns = s + RUNIT) :
                        (m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L)) {
                        WNode c; Thread w;
                        whead = node;
                        node.prev = null;
                        //获取读锁成功，释放node节点的cowait链表上对应的线程。
                        while ((c = node.cowait) != null) {
                            //CAS 设置node的cowait字段指向下一个cowait节点。
                            if (U.compareAndSwapObject(node, WCOWAIT,
                                                       c, c.cowait) &&
                                (w = c.thread) != null)
                                U.unpark(w);
                        }
                        return ns;
                    }
                    //如果写锁被占有，就自旋一会
                    else if (m >= WBIT &&
                             LockSupport.nextSecondarySeed() >= 0 && --k <= 0)
                        break;
                }
            }
            else if (h != null) {
                WNode c; Thread w;
                //释放h节点的cowait链表上对应的线程。
                while ((c = h.cowait) != null) {
                    if (U.compareAndSwapObject(h, WCOWAIT, c, c.cowait) &&
                        (w = c.thread) != null)
                        U.unpark(w);
                }
            }
            if (whead == h) {
                if ((np = node.prev) != p) {//让p为node的前继节点
                    if (np != null)
                        (p = np).next = node;   // stale
                }
                else if ((ps = p.status) == 0) //更新节点状态
                    U.compareAndSwapInt(p, WSTATUS, 0, WAITING);
                else if (ps == CANCELLED) {
                    if ((pp = p.prev) != null) {
                        node.prev = pp;
                        pp.next = node;
                    }
                }
                else {
                    long time;
                    if (deadline == 0L)
                        time = 0L;
                    //如果截止时间已经过去，就取消等待。
                    else if ((time = deadline - System.nanoTime()) <= 0L)
                        return cancelWaiter(node, node, false);
                    Thread wt = Thread.currentThread();
                    U.putObject(wt, PARKBLOCKER, this);
                    node.thread = wt;
                    if (p.status < 0 &&
                        (p != h || (state & ABITS) == WBIT) &&
                        whead == h && node.prev == p)
                        U.park(false, time);
                    node.thread = null;
                    U.putObject(wt, PARKBLOCKER, null);
                    if (interruptible && Thread.interrupted())
                        return cancelWaiter(node, node, true);
                }
            }
        }
    }

    /**
     * If node non-null, forces cancel status and unsplices it from
     * queue if possible and wakes up any cowaiters (of the node, or
     * group, as applicable), and in any case helps release current
     * first waiter if lock is free. (Calling with null arguments
     * serves as a conditional form of release, which is not currently
     * needed but may be needed under possible future cancellation
     * policies). This is a variant of cancellation methods in
     * AbstractQueuedSynchronizer (see its detailed explanation in AQS
     * internal documentation).
     * 如果节点非空，则在可能的情况下强制取消状态并将其从队列中取消拼接，并唤醒（节点或组（如果适用）的）所有等待者，
     * 并且在任何情况下都可以帮助释放当前的第一个等待者（如果锁是空闲的）。
     * （使用空参数进行调用是释放的条件形式，当前不需要，但在将来可能的取消策略下可能需要使用释放）。
     * 这是AbstractQueuedSynchronizer中取消方法的一种变体（请参阅AQS内部文档中的详细说明）。
     *
     * @param node if nonnull, the waiter
     * @param group either node or the group node is cowaiting with
     * @param interrupted if already interrupted
     * @return INTERRUPTED if interrupted or Thread.interrupted, else zero
     */
    private long cancelWaiter(WNode node, WNode group, boolean interrupted) {
        if (node != null && group != null) {
            Thread w;
            node.status = CANCELLED;
            // 从cowait组中取消链接取消了的节点。group-> cancelled cowait->cowait 变为group->cowait
            for (WNode p = group, q; (q = p.cowait) != null;) {
                if (q.status == CANCELLED) {
                    //尝试CAS 更新当前p的cowait字段，如果实际值为期望值q，则更新为新值q.cowait。
                    U.compareAndSwapObject(p, WCOWAIT, q, q.cowait);
                    p = group; //重新开始
                }
                //探测下一个
                else
                    p = q;
            }
            //如果group==node为true，也就是gruop中没有状态为CANCELLED的节点。
            if (group == node) {
                for (WNode r = group.cowait; r != null; r = r.cowait) {
                    if ((w = r.thread) != null)
                        U.unpark(w);       // 唤醒未被取消的co-waiter们
                }
                for (WNode pred = node.prev; pred != null; ) { // unsplice
                    //succ 最终指向未取消的后继节点。
                    WNode succ, pp;        // 找到有效的后继节点
                    while ((succ = node.next) == null ||
                           succ.status == CANCELLED) {
                        WNode q = null;    //q指向后继节点， 从后往前找有效的后继节点，find successor the slow way todo 看到这里
                        for (WNode t = wtail; t != null && t != node; t = t.prev)
                            if (t.status != CANCELLED)
                                q = t;     // don't link if succ cancelled

                        //保证node->next链接的确实是未取消的后继节点
                        if (succ == q ||   // ensure accurate successor
                            U.compareAndSwapObject(node, WNEXT,
                                                   succ, succ = q)) {
                            //执行完上一步succ指向未取消的后继节点，假如node的真正后继节点为空，并且node为尾节点。
                            if (succ == null && node == wtail)
                                //CAS尝试更新尾节点为node的前继节点。
                                U.compareAndSwapObject(this, WTAIL, node, pred);
                            //跳出while循环
                            break;
                        }
                    }
                    if (pred.next == node) // 将node取消链接到它的前继节点pred
                        U.compareAndSwapObject(pred, WNEXT, node, succ);
                    if (succ != null && (w = succ.thread) != null) {
                        succ.thread = null;
                        U.unpark(w);       // wake up succ to observe new pred
                    }
                    if (pred.status != CANCELLED || (pp = pred.prev) == null)
                        break;
                    node.prev = pp;        // 重复如果新的prev 错误或者取消了repeat if new pred wrong/cancelled
                    U.compareAndSwapObject(pp, WNEXT, pred, succ);
                    pred = pp;
                }
            }
        }
        WNode h; // Possibly release first waiter
        while ((h = whead) != null) {
            long s; WNode q; // similar to release() but check eligibility
            //q 指向真正的后继节点（未取消的），从后往前扫描。
            if ((q = h.next) == null || q.status == CANCELLED) {
                for (WNode t = wtail; t != null && t != h; t = t.prev)
                    if (t.status <= 0) //小于等于0表示WAITING或者正常状态（初始化时）
                        q = t;
            }
            if (h == whead) {
                if (q != null && h.status == 0 &&
                    ((s = state) & ABITS) != WBIT && // waiter is eligible
                    (s == 0L || q.mode == RMODE))
                    release(h);
                break;
            }
        }
        return (interrupted || Thread.interrupted()) ? INTERRUPTED : 0L;
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe U;
    private static final long STATE;
    private static final long WHEAD;
    private static final long WTAIL;
    private static final long WNEXT;
    private static final long WSTATUS;
    private static final long WCOWAIT;
    private static final long PARKBLOCKER;

    static {
        try {
            U = GetUnsafeFromReflect.getUnsafe();
            Class<?> k = StampedLock.class;
            Class<?> wk = WNode.class;
            STATE = U.objectFieldOffset
                (k.getDeclaredField("state"));
            WHEAD = U.objectFieldOffset
                (k.getDeclaredField("whead"));
            WTAIL = U.objectFieldOffset
                (k.getDeclaredField("wtail"));
            WSTATUS = U.objectFieldOffset
                (wk.getDeclaredField("status"));
            WNEXT = U.objectFieldOffset
                (wk.getDeclaredField("next"));
            WCOWAIT = U.objectFieldOffset
                (wk.getDeclaredField("cowait"));
            Class<?> tk = Thread.class;
            PARKBLOCKER = U.objectFieldOffset
                (tk.getDeclaredField("parkBlocker"));

        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
