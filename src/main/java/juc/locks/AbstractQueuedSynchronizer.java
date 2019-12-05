package juc.locks;

import sun.misc.Unsafe;
import unsafeTest.GetUnsafeFromReflect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 提供一个框架，用于实现依赖于先进先出（FIFO）等待队列的阻塞锁和相关的同步器（信号量，事件等）。
 * 此类旨在为大多数依赖单个原子int值表示状态的同步器提供有用的基础。
 * 子类必须定义更改底层state的protected方法，并定义该state值对于获取或释放此对象而言意味着什么。
 * 鉴于这些，此类中的其他方法将执行所有排队和阻塞机制。
 * 子类可以维护其他状态字段，但只有与同步相关的原子更新的int值会被跟踪（使用方法getState（），setState（int）
 * 和compareAndSetState（int，int）操作该int值）。
 *
 *
 *
 * <p>AQS的子类应该被定义为用于实现其封闭类的同步属性的非public内部帮助器类。
 * 类AbstractQueuedSynchronizer不实现任何同步接口。
 * 相反，它定义了诸如acquireInterruptible（int）之类的方法，
 * 可以通过具体的锁和相关的同步器适当地调用这些方法以实现其公共方法。
 * (解释： 比如在ReentrantLock 中就定义了静态Sync类，并按需求实现了AQS对应protected的方法。下面的两个示例也是如此)
 *
 *
 *
 * <p>此类支持默认<em>独占模式</em>和<em>共享模式</em>之一或两者。
 * 当以独占方式进行获取时，其他线程尝试进行的获取将无法成功。
 * 由多个线程获取的共享模式可能（但不一定）成功。该类不“理解”这些区别，
 * 只是从机械意义上说，当共享模式获取成功时，下一个等待线程（如果存在）还必须确定它是否也可以获取。
 * 在不同模式下等待的线程共享相同的FIFO队列。
 * 通常，实现子类仅支持这些模式之一，但两种模式都支持也可能存在，例如ReadWriteLock。
 * 仅支持互斥模式或仅支持共享模式的子类无需定义未使用模式的方法。
 * （解释：独占模式要实现AQS中isHeldExclusively，tryAcquire，tryRelease三个protected方法，见下面的使用示例1；
 * 而共享模式要实现AQS中tryAcquireShared，tryReleaseShared两个protected方法，见下面的使用示例2）.
 *
 *
 *
 * <p>AQS中定义了一个嵌套的AbstractQueuedSynchronizer.ConditionObject类（翻译的不明白），
 * 该类可以被支持独占模式的子类用作Condition实现。
 * 对于该独占模式，方法isHeldExclusively（）报告当前线程访问时同步是否已经被独占地持有，
 * 方法release（int）由当前线程调用getState（）值完全释放该对象，
 * 并在获得此保存状态值的情况下获取（int）最终将其恢复为先前的获取状态。
 * 否则，没有AbstractQueuedSynchronizer方法会创建这样的条件，
 * 因此，如果不能满足此约束，请不要使用它。
 * 当然，AbstractQueuedSynchronizer.ConditionObject的行为取决于其同步器实现的语义。
 *
 *
 *
 * <p>AQS给内部队列和条件对象提供了检查，检测和监视方法。
 * 可以根据需要使用AbstractQueuedSynchronizer将它们导出到类中以实现其同步机制。
 * <p>
 * <p>
 * <p>
 * 此类的序列化仅存储底层原子整数维护状态，因此反序列化的对象的线程队列是空的。
 * 需要可序列化的典型子类将定义一个readObject方法，该方法可在反序列化时将其恢复为已知的初始状态。
 *
 * <h3>使用</h3>
 *
 * <p>要将此类用作同步器的基础，请使用getState（），setState（int）
 * 和/或compareAndSetState（int，int）检查和/或修改同步状态，以重新定义以下方法：
 *
 * <ul>
 * <li> {@link #tryAcquire}
 * <li> {@link #tryRelease}
 * <li> {@link #tryAcquireShared}
 * <li> {@link #tryReleaseShared}
 * <li> {@link #isHeldExclusively}
 * </ul>
 * <p>
 * 默认情况下，这些方法中的每一个都会引发UnsupportedOperationException。
 * 这些方法的实现必须在内部是线程安全的，并且通常应简短且不阻塞。
 * 定义这些方法是使用此类的唯一方法。 所有其他方法都被声明为final方法，因为它们不能独立变化。
 *
 * <p>你可能还会发现从AbstractOwnableSynchronizer继承的方法对于跟踪拥有独占同步器的线程很有用。
 * 鼓励你使用它们-这将启用监视和诊断工具，以帮助用户确定哪些线程持有锁。
 * <p>
 * 即使此类基于内部FIFO队列，它也不会自动执行FIFO获取策略。 独占同步的核心采取以下形式：
 * <pre>
 * Acquire:
 *     while (!tryAcquire(arg)) {
 *        <em>enqueue thread if it is not already queued</em>;
 *        <em>possibly block current thread</em>;
 *     }
 *
 * Release:
 *     if (tryRelease(arg))
 *        <em>unblock the first queued thread</em>;
 * </pre>
 * （共享模式相似，但可能涉及级联信号。）
 * <p id="barging"> 因为获取检查是在入队之前被调用的，所以新获取线程可能会在其他被阻塞和排队的线程之前进行插入。
 * 但是，你可以根据需要定义tryAcquire和/或tryAcquireShared以通过内部调用一种或多种检查方法来禁用插入，
 * 从而提供公平的FIFO获取顺序。特别地，如果hasQueuedPredecessors（）（一种专门为公平同步器设计的方法）返回true，
 * 则大多数公平同步器都可以定义tryAcquire返回false。其他变化也是可能的。
 *
 *
 *
 * <p> 对于默认插入（也称为贪婪，放弃和避免拥护）策略，吞吐量和可伸缩性通常最高。
 * 尽管不能保证这是公平的，也不会出现饥饿现象，但是可以让较早排队的线程在较晚排队的线程到来之前进行重新竞争，
 * 并且每次重新争用都可以毫无偏向地成功抵御将来的线程。
 * 同样，尽管获取通常不会“自旋”，但是在阻塞之前，它们可能会执行tryAcquire的多次调用，并插入其他计算。
 * 当仅短暂地保持独占同步时，这将提供自旋的大部分好处，而在不进行排他同步时，则不会带来很多负担。
 * 如果需要的话，可以通过在调用之前使用“fast-path”检查来获取方法来增强此功能，
 * 并可能预先检查hasContended（）和/或hasQueuedThreads（）以仅在可能不争用同步器的情况下这样做。
 *
 *
 * <p>此类为同步提供了有效且可扩展的基础，部分原因是通过将同步使用范围专用于可以依赖于int state，
 * 获取和释放参数以及内部FIFO等待队列的同步器。
 * 如果这还不够，你可以使用原子类，你自己的自定义Queue类和LockSupport阻塞支持，从较低级别构建同步器。
 *
 * <h3>使用示例</h3>
 * <p>这是一个不可重入的互斥锁类，使用值0表示解锁状态，使用值1表示锁定状态。
 * 尽管不可重入锁并不严格要求记录当前所有者线程，但是无论如何，此类记录了以使得使用情况更易于监视。
 * 它还支持条件，并公开了一种检测方法：
 *
 * <pre> {@code
 * class Mutex implements Lock, java.io.Serializable {
 *
 *   // Our internal helper class
 *   private static class Sync extends AbstractQueuedSynchronizer {
 *     // Reports whether in locked state
 *     protected boolean isHeldExclusively() {
 *       return getState() == 1;
 *     }
 *
 *     // Acquires the lock if state is zero
 *     public boolean tryAcquire(int acquires) {
 *       assert acquires == 1; // Otherwise unused
 *       if (compareAndSetState(0, 1)) {
 *         setExclusiveOwnerThread(Thread.currentThread());
 *         return true;
 *       }
 *       return false;
 *     }
 *
 *     // Releases the lock by setting state to zero
 *     protected boolean tryRelease(int releases) {
 *       assert releases == 1; // Otherwise unused
 *       if (getState() == 0) throw new IllegalMonitorStateException();
 *       setExclusiveOwnerThread(null);
 *       setState(0);
 *       return true;
 *     }
 *
 *     // Provides a Condition
 *     Condition newCondition() { return new ConditionObject(); }
 *
 *     // Deserializes properly
 *     private void readObject(ObjectInputStream s)
 *         throws IOException, ClassNotFoundException {
 *       s.defaultReadObject();
 *       setState(0); // reset to unlocked state
 *     }
 *   }
 *
 *   // The sync object does all the hard work. We just forward to it.
 *   private final Sync sync = new Sync();
 *
 *   public void lock()                { sync.acquire(1); }
 *   public boolean tryLock()          { return sync.tryAcquire(1); }
 *   public void unlock()              { sync.release(1); }
 *   public Condition newCondition()   { return sync.newCondition(); }
 *   public boolean isLocked()         { return sync.isHeldExclusively(); }
 *   public boolean hasQueuedThreads() { return sync.hasQueuedThreads(); }
 *   public void lockInterruptibly() throws InterruptedException {
 *     sync.acquireInterruptibly(1);
 *   }
 *   public boolean tryLock(long timeout, TimeUnit unit)
 *       throws InterruptedException {
 *     return sync.tryAcquireNanos(1, unit.toNanos(timeout));
 *   }
 * }}</pre>
 * <p>
 * 这是一个类似于CountDownLatch的闩锁类，只不过它只需要触发一个信号即可。
 * 因为闩锁是非排他性的，所以它使用共享的获取和释放方法。
 * <pre> {@code
 * class BooleanLatch {
 *
 *   private static class Sync extends AbstractQueuedSynchronizer {
 *     boolean isSignalled() { return getState() != 0; }
 *
 *     protected int tryAcquireShared(int ignore) {
 *       return isSignalled() ? 1 : -1;
 *     }
 *
 *     protected boolean tryReleaseShared(int ignore) {
 *       setState(1);
 *       return true;
 *     }
 *   }
 *
 *   private final Sync sync = new Sync();
 *   public boolean isSignalled() { return sync.isSignalled(); }
 *   public void signal()         { sync.releaseShared(1); }
 *   public void await() throws InterruptedException {
 *     sync.acquireSharedInterruptibly(1);
 *   }
 * }}</pre>
 *
 * @author Doug Lea
 * @since 1.5
 */
public abstract class AbstractQueuedSynchronizer
        extends AbstractOwnableSynchronizer
        implements java.io.Serializable {

    /**
     * 以纳秒为单位的自旋速度要比定时休眠（park)速度快。粗略的估计就足以在非常短的超时情况下提高响应能力。
     */
    static final long spinForTimeoutThreshold = 1000L;
    private static final long serialVersionUID = 7373984972572414691L;
    /**
     * 一些设置来支持compareAndSet。
     * 我们需要在这里本地实现它:为了允许将来的增强，我们不能显式地子类化AtomicInteger，否则它将是有效和有用的。
     * 因此，作为缺点中的优点，我们使用hotspot intrinsics API在本地实现它。
     * 同时，我们还对其他CASable字段执行相同的操作(可以通过原子字段更新器执行)。
     */
    private static final Unsafe unsafe = GetUnsafeFromReflect.getUnsafe();
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long waitStatusOffset;
    private static final long nextOffset;

    static {
        try {
            stateOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset
                    (Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset
                    (Node.class.getDeclaredField("next"));

        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    // 队列相关工具方法

    /**
     * 等待队列的头节点，延迟初始化。
     * 除初始化外，只能通过setHead方法进行修改。
     * 注意如果存在head，则保证其waitStatus不是CANCELLED。
     */
    private transient volatile Node head;
    /**
     * 等待队列的尾节点，延迟初始化。
     * 仅通过方法enq进行修改以添加新的等待节点。
     */
    private transient volatile Node tail;
    /**
     * 同步状态
     */
    private volatile int state;

    /**
     * Creates a new {@code AbstractQueuedSynchronizer} instance
     * with initial synchronization state of zero.
     */
    protected AbstractQueuedSynchronizer() {
    }

    /**
     * 检查并更新失败获取了的节点的状态。
     * 如果线程应该阻塞，则返回true。
     * 这是所有acquire循环中的主要信号控制。 要求pred == node.prev。
     *
     * @param pred 节点的前继节点保存状态
     * @param node 节点
     * @return {@code true} 如果线程需要阻塞
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int ws = pred.waitStatus;
        if (ws == Node.SIGNAL)
            /*
             * 该节点已经设置了状态，要求一个释放以发出信号，以便可以安全地停放。
             */
            return true;
        //如果前继节点状态大于0(也就是CANCELLED), 就重新设置node的前继节点（跳过那些CANCELLED节点）
        if (ws > 0) {
            /*
             * 前继节点被取消了。
             * 跳过被取消的前继节点并且一直重试。
             */
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;
        } else {
            //前继节点的状态为其他值（<=0, 但排除Node.SIGNAL）
            /*
             * waitStatus必须为0或PROPAGATE。
             * 表示我们需要一个信号，但不要park。
             * 调用者将需要重试以确保在parking之前无法获取。
             */
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;
    }

    /**
     * 中断当前线程
     */
    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }

    /**
     * CAS 设置一个节点的waitStatus值。
     */
    private static final boolean compareAndSetWaitStatus(Node node,
                                                         int expect,
                                                         int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset,
                expect, update);
    }

    // 各种acquire版本的工具方法

    /**
     * CAS设置一个节点的next字段。
     */
    private static final boolean compareAndSetNext(Node node,
                                                   Node expect,
                                                   Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }

    /**
     * 返回同步状态的当前值。
     * 此操作有读{@code volatile} 的内存语义。
     *
     * @return current state value
     */
    protected final int getState() {
        return state;
    }

    /**
     * 设置同步状态的值。
     * 此操作有写{@code volatile} 的内存语义。
     *
     * @param newState the new state value
     */
    protected final void setState(int newState) {
        state = newState;
    }

    /**
     * 如果当前同步状态等于期望值expect, 那么就原子地设置同步状态为给定的更新值update
     * 此操作有读写{@code volatile} 的内存语义。
     *
     * @param expect 期望值
     * @param update 新值
     * @return {@code true} if successful. 返回false则表明实际值不等于期望值。
     */
    protected final boolean compareAndSetState(int expect, int update) {
        // See below for intrinsics setup to support this
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    /*
     * Various flavors of acquire, varying in exclusive/shared and
     * control modes.  Each is mostly the same, but annoyingly
     * different.  Only a little bit of factoring is possible due to
     * interactions of exception mechanics (including ensuring that we
     * cancel if tryAcquire throws exception) and other control, at
     * least not without hurting performance too much.
     */

    /**
     * 将节点插入队列，必要时进行初始化。 参见上图。
     *
     * @param node 节点要插入的节点
     * @return 节点的前继节点
     */
    private Node enq(final Node node) {
        for (; ; ) {
            Node t = tail;
            if (t == null) { //尾节点的快照值为null，则尝试初始化。
                if (compareAndSetHead(new Node())) //这里用到了Node的无参构造函数
                    //由于头节点只能设置一次，如果可以设置成功，则表示是在初始化该等待队列。
                    tail = head;
            } else {
                node.prev = t;//尝试将该node连接到尾节点，不断尝试直到成功为止。
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }

    /**
     * 为当前线程和给定模式创建并入队节点。
     *
     * @param mode 模式 Node.EXCLUSIVE用于独占，Node.SHARED用于共享
     * @return 新加的节点
     */
    private Node addWaiter(Node mode) {
        Node node = new Node(Thread.currentThread(), mode);
        //尝试enq的快速路径（下面if的判断，如果在没有多线程争用CAS尾节点的话，就会直接成功,所以叫做快速路径）；
        //失败的话就回滚到完整enq逻辑
        Node pred = tail;
        if (pred != null) {
            node.prev = pred;
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }
        //完整的入队操作
        enq(node);
        return node;
    }

    /**
     * 将node设置成队列的头结点head，从而使得之前的头结点出队。
     * 仅通过acquire方法调用。为了进行GC和抑制不必要的信号和遍历，还会将未使用的字段置为null。
     *
     * @param node the node
     */
    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }

    /**
     * 唤醒unpark节点node的后继节点，如果存在的话。
     *
     * @param node the node
     */
    private void unparkSuccessor(Node node) {
        /*
         * 如果状态是负数（即可能需要信号），请尝试清除该状态以预期发出信号。
         * 如果失败或被等待线程更改状态，也是可以的。
         */
        int ws = node.waitStatus;
        if (ws < 0)
            //尝试将节点node的状态更改为0
            compareAndSetWaitStatus(node, ws, 0);

        /*
         * 要唤醒unpark的线程被保存在后续节点中，它通常只是下一个节点。
         * 但是，如果下一个节点被取消或显然为空，则从tail向前遍历以找到实际的未取消的后继节点。
         */
        //s指向后继节点
        Node s = node.next;
        //如果下一个节点被取消或显然为空，则从tail向前遍历以找到实际的未取消的后继节点。
        if (s == null || s.waitStatus > 0) {
            s = null;
            for (Node t = tail; t != null && t != node; t = t.prev)
                if (t.waitStatus <= 0)
                    s = t;
        }
        if (s != null)
            //唤醒unpark这个后继节点
            LockSupport.unpark(s.thread);
    }


    /**
     * 共享模式下的释放动作——通知后继者，并确保传播。
     * (注意:对于独占模式，如果需要通知头结点，释放仅相当于对头结点head调用unparkSuccessor。)
     */
    private void doReleaseShared() {
        /*
         * 无限循环：
         *  如果头节点快照不为空，且不为尾节点。
         *      如果头节点快照h的等待状态是SIGNAL，则一直CAS设置，直到某个线程将节点状态为0，并唤醒后继节点(unparkSuccessor)。
         *      如果头节点快照h的等待状态是0，则一直CAS设置，直到某个线程将节点状态为PROPAGATE
         *  如果经过上面的条件判断及相关逻辑，头节点快照等于头节点内存实时值，就跳出循环。
         */
        for (; ; ) {
            Node h = head;
            if (h != null && h != tail) {
                int ws = h.waitStatus;
                //如果头节点状态为SIGNAL，就会就会试图唤醒head的后继节点（利用unparkSuccessor）
                if (ws == Node.SIGNAL) {
                    //cas更新节点等待状态，如果实际值等于期望值SIGANL，则更新为0。
                    //如果CAS成功，则执行unparkSuccessor，如果CAS失败，就重新循环。
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                        continue;            // 如果上面CAS失败的话，会重新循环以重试。
                    //唤醒后继节点。
                    unparkSuccessor(h);
                }
                // 如果头节点状态为0，则将状态设置为PROPAGATE，以确保在release后传播仍将继续。
                else if (ws == 0 &&
                        !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                    continue;                // 如果上面CAS失败的话，会重新循环以重试。
            }
            if (h == head)                   // 如果头结点状态稳定了，就跳出循环。
                break;
        }
    }


    /**
     * 设置队列的头节点head，先检查是否propagate参数>0或者节点node状态被设置成PROPAGATE，如果检查为true，并检查next是否在共享模式，如果是则继续传播（执行doReleaseShared）
     *
     * @param node      the node
     * @param propagate 调用tryAcquireShared的返回值
     */
    private void setHeadAndPropagate(Node node, int propagate) {
        //为了下面的检查记录老的头节点
        Node h = head;
        //设置当前node为新的头节点。
        setHead(node);
        /*
         * 尝试去通知下一个排队的节点，如果：
         *    参数propagate大于0，或者对老的头节点和新的头节点进行判断，即 h == null || h.waitStatus < 0 || (h = head) == null || h.waitStatus < 0部分，这里使用
         *    符号判断的原因是因为节点的waitStatus(等待状态)可能会从PROPAGATE转移到SIGNAL
         * 然后就执行if里面的逻辑，
         *    如果当前节点node的next节点为null或这他是共享模式的，则执行doReleaseShared
         * 这两项检查中的保守性可能会导致不必要的唤醒，但只有在有多个线程竞争获取/发布时，因此无论现在还是不久，大多数线程还是需要通知。
         */
        if (propagate > 0 || h == null || h.waitStatus < 0 ||
                (h = head) == null || h.waitStatus < 0) {
            Node s = node.next;
            if (s == null || s.isShared())
                doReleaseShared();
        }
    }

    // 主要导出的方法

    /**
     * 取消正在进行的获取尝试。
     *
     * @param node the node
     */
    private void cancelAcquire(Node node) {
        // Ignore if node doesn't exist
        if (node == null)
            return;

        node.thread = null;

        // 跳过取消了的前继节点（waitStatus> 0，只能是CANCELLED)
        Node pred = node.prev;
        while (pred.waitStatus > 0)
            node.prev = pred = pred.prev;


        /**
         * todo 翻译准确性
         * predNext明显是要取消连接节点。下面的CAS操作将失败，如果没有失败，在这种情况下，我们没有与另一个节点取消或通知竞争，
         * 所以不需要进一步的行动。
         */
        // predNext is the apparent node to unsplice. CASes below will
        // fail if not, in which case, we lost race vs another cancel
        // or signal, so no further action is necessary.
        Node predNext = pred.next;

        //这里可以使用无条件写代替CAS。
        //在这个原子步骤之后，其他节点可以跳过我们。（因为我们已经被设置成CANCELLED了）。
        //以前，我们没有来自其他线程的干扰。
        // Can use unconditional write instead of CAS here.
        // After this atomic step, other Nodes can skip past us.
        // Before, we are free of interference from other threads.
        node.waitStatus = Node.CANCELLED;

        // 如果我们是尾结点tail，就移除我们自己。
        if (node == tail && compareAndSetTail(node, pred)) {
            //CAS将原来的尾结点置null。
            compareAndSetNext(pred, predNext, null);
        } else {
            /**
             * 如果后继节点需要信号，尝试设置pred的next链接，这样它将得到一个。否则唤醒它传播。
             */
            // If successor needs signal, try to set pred's next-link
            // so it will get one. Otherwise wake it up to propagate.
            int ws;
            if (pred != head &&
                    ((ws = pred.waitStatus) == Node.SIGNAL ||
                            (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
                    pred.thread != null) {
                Node next = node.next;
                if (next != null && next.waitStatus <= 0)
                    compareAndSetNext(pred, predNext, next);
            } else {
                unparkSuccessor(node);
            }

            node.next = node; // help GC
        }
    }

    /**
     * park的便捷方法，然后检查是否中断
     *
     * @return {@code true} 如果中断
     */
    private final boolean parkAndCheckInterrupt() {
        LockSupport.park(this);
        return Thread.interrupted();
    }

    /**
     * 在已排队的线程上，以排他的不间断模式获取。
     * （注释，就是不听地尝试让node获取许可证成功）
     * 被条件等待方法以及acquire方法使用。
     *
     * @param node 节点
     * @param arg  acquire参数
     * @return {@code true} 如果在等待时被打断
     */
    final boolean acquireQueued(final Node node, int arg) {
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (; ; ) {
                final Node p = node.predecessor();
                //如果当期节点的前继节点为head，则尝试以独占模式进行获取。
                if (p == head && tryAcquire(arg)) {
                    //成功获取许可，直接设置头节点为当前节点node
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return interrupted;
                }
                //shouldParkAfterFailedAcquire(p, node)判断是否需要使当前节点线程休眠park.
                //parkAndCheckInterrupt 如果需要的话就休眠当前线程，并检查线程是否被中断，如果是则设置interrupted为true。
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * 以不可中断模式独占地获取。
     *
     * @param arg the acquire argument
     */
    private void doAcquireInterruptibly(int arg)
            throws InterruptedException {
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return;
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * 以独占地定时的模式来获取。(尝试在指定的时间获取独占锁）
     *
     * @param arg          the acquire argument
     * @param nanosTimeout max wait time
     * @return {@code true} if acquired
     */
    private boolean doAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L)
            return false;
        final long deadline = System.nanoTime() + nanosTimeout;
        //以独占模式将节点添加到同步队列尾部
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                //如果当前节点的前继节点是头节点，就尝试调用tryAcquire获取独占锁。
                if (p == head && tryAcquire(arg)) {
                    //获取独占锁成功，就设置新的头节点为当前节点node
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return true;
                }
                //如果上面if条件没有成功，则计算剩余的等待时间。
                nanosTimeout = deadline - System.nanoTime();
                //如果等待时间<=0， 即超时了，就直接返回了。
                if (nanosTimeout <= 0L)
                    return false;
                //shouldParkAfterFailedAcquire判断获取锁失败之后是否需要使当前节点线程休眠park.
                //如果shouldParkAfterFailedAcquire返回true，并且当前超时时间剩余大于spinForTimeoutThreshold，则使得当前线程阻塞nanosTimeout纳秒。
                if (shouldParkAfterFailedAcquire(p, node) &&
                        nanosTimeout > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * 以共享的不可中断的模式来获取
     *
     * @param arg the acquire argument
     */
    private void doAcquireShared(int arg) {
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        if (interrupted)
                            selfInterrupt();
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * 以共享的可中断的模式来获取
     *
     * @param arg the acquire argument
     */
    private void doAcquireSharedInterruptibly(int arg)
            throws InterruptedException {
        //以共享模式添加当前节点到同步队列的尾部。
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                //如果前继节点是头节点
                if (p == head) {
                    //尝试获取共享锁
                    int r = tryAcquireShared(arg);
                    //如果返回值>=0, 根据tryAcquireShared方法规范可知，此时已经获取到了共享锁。
                    if (r >= 0) {
                        //设置头节点并传播
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return;
                    }
                }
                //shouldParkAfterFailedAcquire(p, node)判断是否需要使当前节点线程休眠park.
                //parkAndCheckInterrupt 如果需要的话就休眠当前线程，并检查线程是否被中断，如果是则设置interrupted为true。
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * 以共享的定时的模式来获取
     *
     * @param arg          the acquire argument
     * @param nanosTimeout max wait time
     * @return {@code true} if acquired
     */
    private boolean doAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L)
            return false;
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return true;
                    }
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L)
                    return false;
                if (shouldParkAfterFailedAcquire(p, node) &&
                        nanosTimeout > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * 尝试以独占模式进行获取。
     * 此方法应查询对象的状态state是否允许以独占模式获取对象，如果允许则获取对象。
     *                                                                 
     * <p>此方法始终由执行acquire的线程调用。
     * 如果此方法报告失败，则acquire方法可将线程排队（如果尚未排队），直到该线程被其他线程的一个释放信号为止。
     * 这可以用来实现方法{@link Lock＃tryLock（）}。
     * <p>默认的实现抛出 {@link UnsupportedOperationException}.
     *
     * @param arg 获取参数。 该值始终是传递给acquire方法的值，或者是在进入条件等待时保存的值。
     *            否则该值将无法解释，并且可以代表你喜欢的任何内容。
     * @return {@code true} 如果成功的话就返回true。成功的话，这个对象已经被获取到了。
     * @throws IllegalMonitorStateException  如果获取会使该同步器处于非法状态，则抛出IllegalMonitorStateException。
     *                                       必须以一致的方式抛出此异常，以使同步正常工作。
     * @throws UnsupportedOperationException 如果独占模式不支持
     */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 在独占模式中，尝试设置状态（state）反映释放。
     *
     *
     * <p>始终由执行释放的线程调用此方法。
     *
     * <p>默认的实现抛出
     * {@link UnsupportedOperationException}.
     *
     * @param arg release参数。该值始终是传递给release方法的值，或者是在进入条件等待时的当前状态值。
     *            否则该值将无法解释，并且可以代表你喜欢的任何内容。
     * @return {@code true}，如果此对象现在处于完全释放状态，则任何等待线程都可以尝试获取； 否则为{@code false}。
     * @throws IllegalMonitorStateException  如果释放将使该同步器处于非法状态。
     *                                       必须以一致的方式抛出此异常，以使同步正常工作。
     * @throws UnsupportedOperationException 如果独占模式不支持
     */
    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试以共享模式获取。该方法应该查询对象的状态是否允许在共享模式下获取它，如果允许，则应该获取它。
     *
     * <p>此方法总是由执行获取的线程调用。如果此方法报告失败，则获取方法可能会对线程进行排队(如果它还没有排队)，
     * 直到通过其他线程的发出释放信号。
     *
     * <p>默认实现抛出 {@link UnsupportedOperationException}.
     *
     * @param arg 获取参数。这个值总是被传递给一个获取方法，或者是在进入一个条件wait时被保存。该值是未解释的，可以表示你喜欢的任何内容。
     * @return 返回负值表示失败;
     *         返回0表示，这次共享模式下的获取成功，但是后续的共享模式获取都不会成功;
     *         返回正数表示，这次共享模式下获取成功，并且随后的共享模式获取也可能成功，那么在这种情况下，后续的等待线程必须检查可用性。
     *         (支持三种不同的返回值，使此方法可以用于仅在某些情况下才进行获取的上下文中。)
     *         成功之后，这个对象就获得了。
     * @throws IllegalMonitorStateException  如果获取将使该同步器处于非法状态。必须以一致的方式抛出此异常，以便同步工作正常。
     * @throws UnsupportedOperationException 如果共享模式不支持。
     */
    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试设置状态来反映共享模式下的释放。
     *
     * <p>这个方法总是被执行release的线程调用。
     *
     * <p>默认实现抛出 {@link UnsupportedOperationException}.
     *
     * @param arg 获取参数。这个值总是被传递给一个获取方法，或者是在进入一个条件wait时被保存。该值是未解释的，可以表示你喜欢的任何内容。
     * @return 如果共享模式下的释放可以运行（共享的或独占的）等待的获取成功，就返回true，否则返回false。
     * @throws IllegalMonitorStateException  如果获取将使该同步器处于非法状态。必须以一致的方式抛出此异常，以便同步工作正常。
     * @throws UnsupportedOperationException 如果共享模式不支持。
     */
    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 如果同步被当前（调用）线程独占，就返回true。
     * 此方法在每次调用{@link ConditionObject}的非等待方法时被调用。（等待方法，而不是调用调用{@link #release(int)}.
     *
     * <p>默认的实现抛出 {@link UnsupportedOperationException}.
     * 此方法仅在{@link ConditionObject}方法内部调用，因此如果不使用条件Condition，则无需定义。
     *
     * @return 如果同步被独占 返回true，否则返回false;
     * @throws UnsupportedOperationException 如果条件不支持。
     */
    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    // 队列检测方法(Queue inspection methods)

    /**
     * 以独占模式获取，忽略中断。
     * 通过至少一次调用{@link #tryAcquire}来实现，成功后返回。
     * 否则线程将排队，可能会重复阻塞和取消阻塞，调用{@link #tryAcquire}直到成功。
     * 此方法可用于实现方法{@link Lock#lock}。
     *
     * @param arg 获取参数。这个值被传递给 {@link #tryAcquire}。该值是未解释的，可以表示你喜欢的任何内容。
     */
    public final void acquire(int arg) {
        if (!tryAcquire(arg) &&
                acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }

    /**
     * 以独占模式获取，如果中断则中止。首先检查中断状态，然后至少调用一次{@link #tryAcquire}，成功后返回。
     * 否则，线程将排队，可能会重复阻塞和取消阻塞，调用{@link #tryAcquire}，直到成功或线程被中断。
     * 此方法可用于实现方法{@link Lock#lockInterruptibly}。
     *
     * @param arg 获取参数。这个值被传递给 {@link #tryAcquire}。该值是未解释的，可以表示你喜欢的任何内容。
     * @throws InterruptedException 如果当前线程被中断
     */
    public final void acquireInterruptibly(int arg)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        if (!tryAcquire(arg))
            doAcquireInterruptibly(arg);
    }

    /**
     * 尝试以独占模式获取，如果中断将中止，如果超时了将失败。
     *
     * 首先检查中断状态，然后至少调用一次{@link #tryAcquire}，成功后返回。
     * 否则，线程将排队，可能会重复阻塞和取消阻塞，调用{@link #tryAcquire}，直到成功或线程中断或超时结束。
     * 此方法可用于实现方法{@link Lock#tryLock(long, TimeUnit)}。
     * @param arg 获取参数。这个值被传递给 {@link #tryAcquire}。该值是未解释的，可以表示你喜欢的任何内容。
     * @param nanosTimeout 最大等待的纳秒数
     * @return 如果获取成功则返回true，如果超时则返回false。
     * @throws InterruptedException 如果当前线程被中断
     */
    public final boolean tryAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquire(arg) ||
                doAcquireNanos(arg, nanosTimeout);
    }

    /**
     * 以独占模式释放锁。 如果{@link #tryRelease}返回true，则通过解除阻塞一个或多个线程来实现。
     * 此方法可用于实现方法{@link Lock＃unlock}。
     *
     * @param arg 释放个数参数。该值将传递给{@link #tryRelease}。但该不会被解释，并且可以表示你喜欢的任何内容。
     * @return {@link #tryRelease}返回的值
     */
    public final boolean release(int arg) {
        if (tryRelease(arg)) {
            Node h = head;
            if (h != null && h.waitStatus != 0)
                unparkSuccessor(h);
            return true;
        }
        return false;
    }

    /**
     * 忽略中断，以共享模式获取。
     * 首先调用至少一次{@link #tryAcquireShared}，成功后返回。
     * 否则线程将排队，可能会重复阻塞和取消阻塞，调用{@link #tryAcquireShared}直到成功。
     *
     * @param arg 获取参数. 这个值被传递给{@link #tryAcquireShared} 。但该不会被解释，并且可以表示你喜欢的任何内容。
     */
    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0)
            doAcquireShared(arg);
    }

    /**
     * 以共享模式获取，如果中断将中止。
     * 首先检查中断状态，然后至少调用一次{@link #tryAcquireShared}，成功后返回。
     * 否则，线程将排队，可能会重复阻塞和取消阻塞，调用{@link #tryAcquireShared}，直到成功或线程被中断。
     *
     * @param arg 获取参数. 这个值被传递给{@link #tryAcquireShared} 。但该不会被解释，并且可以表示你喜欢的任何内容。
     * @throws InterruptedException 如果当前线程被中断
     */
    public final void acquireSharedInterruptibly(int arg)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        //尝试获取共享锁，如果获取失败，会返回负数，然后会执行if里面的doAcquireSharedInterruptibly逻辑；如果获取成功，返回值就是大于等于0的数，就直接返回了。
        if (tryAcquireShared(arg) < 0)
            doAcquireSharedInterruptibly(arg);
    }

    /**
     * 尝试以共享模式获取，如果中断将中止，并且如果给定超时超时将失败。
     * 首先检查中断状态，然后至少调用一次{@link #tryAcquireShared}，成功后返回。
     * 否则，线程将排队，可能会重复阻塞和取消阻塞，调用{@link #tryAcquireShared}，直到成功或线程中断或超时结束。
     *
     * @param arg 获取参数. 这个值被传递给{@link #tryAcquireShared} 。但该值不会被解释，并且可以表示你喜欢的任何内容。
     * @param nanosTimeout 最大等待的纳秒数
     * @throws InterruptedException 如果当前线程被中断
     * @return {@code true} 如果成功获取; {@code false} 如果超时
     */
    public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquireShared(arg) >= 0 ||
                doAcquireSharedNanos(arg, nanosTimeout);
    }


    // 仪器和监测方法（Instrumentation and monitoring methods）

    /**
     * 以共享模式释放。如果{@link #tryReleaseShared}返回true，则通过解除一个或多个线程的阻塞来实现。
     * @param arg 释放参数.这个值被传递给{@link #tryReleaseShared}，但该值不会被解释，并且可以表示你喜欢的任何内容。
     * @return 从{@link #tryReleaseShared}返回的值
     */
    public final boolean releaseShared(int arg) {
        if (tryReleaseShared(arg)) {
            doReleaseShared();
            return true;
        }
        return false;
    }

    /**
     * 查询是否有线程正在等待获取。
     * 注意，由于中断和超时导致的取消可能随时发生，因此返回true并不保证任何其他线程将会获得。
     *
     * 在此实现中，此操作以常数时间返回。
     *
     * @return {@code true}如果可能有其他线程等待获取
     *
     */
    public final boolean hasQueuedThreads() {
        return head != tail;
    }

    /**
     * 查询是否有任何线程争用过此同步器;也就是说，是否一个获取方法曾经被阻塞。
     *
     * 在此实现中，此操作以常数时间返回。
     *
     * @return {@code true} 如果已经出现了争用
     */
    public final boolean hasContended() {
        return head != null;
    }

    /**
     * 返回队列中的第一个(等待时间最长的)线程，如果当前没有线程排队，则返回{@code null}。
     *
     * 在这个实现中，这个操作通常在常数时间内返回，但是如果其他线程同时修改队列，则可能在争用时进行迭代。
     * @return 队列中的第一个(等待时间最长的)线程，如果当前没有线程排队，则返回{@code null}
     */
    public final Thread getFirstQueuedThread() {
        // handle only fast path, else relay
        return (head == tail) ? null : fullGetFirstQueuedThread();
    }

    /**
     * Version of getFirstQueuedThread called when fastpath fails
     */
    private Thread fullGetFirstQueuedThread() {
        /*
         * The first node is normally head.next. Try to get its
         * thread field, ensuring consistent reads: If thread
         * field is nulled out or s.prev is no longer head, then
         * some other thread(s) concurrently performed setHead in
         * between some of our reads. We try this twice before
         * resorting to traversal.
         */
        Node h, s;
        Thread st;
        if (((h = head) != null && (s = h.next) != null &&
                s.prev == head && (st = s.thread) != null) ||
                ((h = head) != null && (s = h.next) != null &&
                        s.prev == head && (st = s.thread) != null))
            return st;

        /*
         * Head's next field might not have been set yet, or may have
         * been unset after setHead. So we must check to see if tail
         * is actually first node. If not, we continue on, safely
         * traversing from tail back to head to find first,
         * guaranteeing termination.
         */

        Node t = tail;
        Thread firstThread = null;
        while (t != null && t != head) {
            Thread tt = t.thread;
            if (tt != null)
                firstThread = tt;
            t = t.prev;
        }
        return firstThread;
    }


    // Condition的内部支持方法

    /**
     * 如果给定的线程已经排队了就返回true。
     *
     * <p>此实现遍历队列来决定给定线程是否存在。
     *
     * @param thread 给定线程
     * @return  如果给定线程存在队列上就返回true，否则false。
     * @throws NullPointerException 如果给定线程为null
     */
    public final boolean isQueued(Thread thread) {
        if (thread == null)
            throw new NullPointerException();
        for (Node p = tail; p != null; p = p.prev)
            if (p.thread == thread)
                return true;
        return false;
    }

    /**
     * 如果第一个排队的线程(如果存在)明显正在排它模式中等待，则返回{@code true}。
     * 如果这个方法返回{@code true}，并且当前线程正在尝试以共享模式获取(也就是说，这个方法是从{@link #tryAcquireShared}调用的)，那么可以保证当前线程不是第一个排队的线程。
     * 这个方法只能作为ReentrantReadWriteLock中的一个启发使用。
     */
    final boolean apparentlyFirstQueuedIsExclusive() {
        Node h, s;
        return (h = head) != null &&
                (s = h.next) != null &&
                !s.isShared() &&
                s.thread != null;
    }

    /**
     * 查询是否有任何线程等待获取的时间比当前线程长。
     *
     *
     * 此方法的调用相当于(但可能更有效于):
     * <pre> {@code
     *      * getFirstQueuedThread() != Thread.currentThread() &&
     *      * hasQueuedThreads()}</pre>
     *
     *
     * 注意，由于中断和超时导致的取消可能随时发生，返回true并不保证其他线程在当前线程之前获得。
     * 同样，在此方法返回false后，由于队列为空，其他线程也可能竞争赢得入队。
     *
     * <p>该方法被设计用于一个公平的同步器来避免<a href="AbstractQueuedSynchronizer#barging">barging</a>。
     * 如果这个方法返回{@code true}，这样一个公平同步器的{@link #tryAcquire}方法应该返回{@code false}，
     * 并且该同步器的{@link # tryacquiremrered}方法应该返回一个负值，(除非这是一个重入获取)。
     * 例如，一个公平的、可重入的、独占模式同步器的{@code tryAcquire}方法可能是这样的:
     * <pre> {@code
     * protected boolean tryAcquire(int arg) {
     *   if (isHeldExclusively()) {
     *     // A reentrant acquire; increment hold count
     *     return true;
     *   } else if (hasQueuedPredecessors()) {
     *     return false;
     *   } else {
     *     // try to acquire normally
     *   }
     * }}</pre>
     *
     * @return 如果当前线程之前有一个排队的线程，则返回true;如果当前线程位于队列的最前面或队列为空，则返回false
     * @since 1.7
     */
    public final boolean hasQueuedPredecessors() {
         //这种方法的正确性取决于头节点在尾节点之前被初始化，并且如果当前线程是队列中的第一个线程，则head.next是精确的。
        Node t = tail; // 按反初始化顺序读取字段
        Node h = head;
        Node s;
        return h != t &&
                ((s = h.next) == null || s.thread != Thread.currentThread());
    }

    /**
     * 返回等待获取的线程数量的估计值。
     * 这个值只是一个估计值，因为当这个方法遍历内部数据结构时，线程的数量可能会动态变化。
     * 该方法用于监控系统状态，不用于同步控制。
     *
     * @return 等待获取的线程数量的估计值。
     */
    public final int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null; p = p.prev) {
            if (p.thread != null)
                ++n;
        }
        return n;
    }

    /**
     * 返回一个包含可能正在等待获取的线程的集合。
     * 因为在构造这个结果时，实际的线程集可能会动态变化，所以返回的集合只是一个最佳效果的估计。
     * 返回集合的元素没有特定的顺序。这种方法的目的是为了方便构建提供更广泛的监视设施的子类。
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
        return list;
    }

    // 条件测量方法（Instrumentation methods for conditions）

    /**
     * 返回一个集合，其中包含可能正在等待以独占模式获取的线程。
     * 它具有与{@link #getQueuedThreads}相同的属性，只是它只返回那些由于独占获取而等待的线程。
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getExclusiveQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (!p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * 返回一个集合，其中包含可能在共享模式下等待获取的线程。
     * 它具有与{@link #getQueuedThreads}相同的属性，只是它只返回那些由于共享获取而等待的线程。
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getSharedQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * 返回一个字符串标识这个同步器，以及它的状态
     * 在方括号中，状态包括字符串{@code "State ="}和当前值{@link #getState}，以及{@code "nonempty"}或{@code "empty"}，这取决于队列是否为空。
     * @return 一个字符串标识这个同步器，以及它的状态
     *
     * @return a string identifying this synchronizer, as well as its state
     */
    public String toString() {
        int s = getState();
        String q = hasQueuedThreads() ? "non" : "";
        return super.toString() +
                "[State = " + s + ", " + q + "empty queue]";
    }

    /**
     * 如果某个节点（总是最初放置在条件队列中的一个节点）现在在同步队列上等待重新获取，则返回true。
     *
     * @param node the node
     * @return true if is reacquiring
     */
    final boolean isOnSyncQueue(Node node) {
        //如果等待状态是条件，说明还在条件队列上，则不再同步队列上。
        if (node.waitStatus == Node.CONDITION || node.prev == null)
            return false;
        //如果有后继节点，肯定在某个队列上，上面已经排除了条件队列，只能是同步队列了。
        if (node.next != null) // If has successor, it must be on queue
            return true;
        /*
         * node.prev可以为非null，但node可能尚未在队列上，因为将node放入队列的CAS可能会失败。
         * 因此，我们必须从尾部开始遍历以确保它确实做到了。
         * 在此方法的调用中，它将始终靠近tail尾节点，除非CAS失败（这不太可能），
         * 否则它将一直在CAS中，因此我们几乎不会遍历太多。
         */
        return findNodeFromTail(node);
    }

    /**
     * 如果从尾节点向前搜索时，node在同步队列中，则返回true。
     * 仅在isOnSyncQueue需要时调用。
     *
     * @return 存在就返回true
     */
    private boolean findNodeFromTail(Node node) {
        Node t = tail;
        for (; ; ) {
            if (t == node)
                return true;
            if (t == null)
                return false;
            t = t.prev;
        }
    }

    /**
     * 将一个节点从条件队列转移到同步队列。
     * 返回true如果成功的话。
     *
     * @param node the node
     * @return 如果成功转移，则返回true（否则，节点在信号（signal）之前被取消）
     */
    final boolean transferForSignal(Node node) {
        /*
         * 如果不能改变waitStatus，那么节点就已经被取消了
         */
        if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
            return false;

        /*
         * 拼接到同步队列上, 并返回node的前继节点，记为p
         */
        Node p = enq(node);
        //并尝试设置前继节点p的waitStatus以表明当前线程（可能）正在等待。
        int ws = p.waitStatus;
        if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
            //如果取消或尝试设置waitStatus失败，请唤醒以重新同步
            //在这种情况下，waitStatus可能会暂时性且无害地错误）。
            LockSupport.unpark(node.thread);
        return true;
    }

    /**
     * 如果需要的话，在一个取消等待之后将节点node转移到同步队列
     * 如果线程在被通知之前被取消，则返回true。
     *
     * @param node 节点
     * @return 如果线程在被通知之前被取消，则返回true。
     */
    final boolean transferAfterCancelledWait(Node node) {
        //尝试CAS设置节点node的等待状态，如果存的waitStatus为期望值Node.CONDITION,那么就设置为更新值0。
        if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
            //当前节点假如同步队列。
            enq(node);
            return true;
        }
        /*
         * 如果我们丢失了一个signal()，那么我们就不能继续，直到节点完成它的enq()操作。
         * 在不完全转移过程中取消是罕见的，也是短暂的，所以只要自旋spin就可以了。
         */
        //node不在同步队列上，就自旋spin。
        while (!isOnSyncQueue(node))
            //当前线程愿意放弃处理器。
            Thread.yield();
        return false;
    }

    /**
     * 用当前state值调用release方法； 返回保存的state。
     * 取消节点并在失败时抛出异常。
     *
     * @param node 等待的条件节点
     * @return 之前的同步状态
     */
    final int fullyRelease(Node node) {
        boolean failed = true;
        try {
            //获取state的值
            int savedState = getState();
            //以独占模式释放锁
            if (release(savedState)) {
                failed = false;
                return savedState;
            } else {
                throw new IllegalMonitorStateException();
            }
        } finally {
            if (failed)
                node.waitStatus = Node.CANCELLED;
        }
    }

    /**
     * Queries whether the given ConditionObject
     * uses this synchronizer as its lock.
     *
     * @param condition the condition
     * @return {@code true} if owned
     * @throws NullPointerException if the condition is null
     */
    public final boolean owns(ConditionObject condition) {
        return condition.isOwnedBy(this);
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with this synchronizer. Note that because timeouts
     * and interrupts may occur at any time, a {@code true} return
     * does not guarantee that a future {@code signal} will awaken
     * any threads.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @param condition the condition
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *                                      is not held
     * @throws IllegalArgumentException     if the given condition is
     *                                      not associated with this synchronizer
     * @throws NullPointerException         if the condition is null
     */
    public final boolean hasWaiters(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.hasWaiters();
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with this synchronizer. Note that
     * because timeouts and interrupts may occur at any time, the
     * estimate serves only as an upper bound on the actual number of
     * waiters.  This method is designed for use in monitoring of the
     * system state, not for synchronization control.
     *
     * @param condition the condition
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *                                      is not held
     * @throws IllegalArgumentException     if the given condition is
     *                                      not associated with this synchronizer
     * @throws NullPointerException         if the condition is null
     */
    public final int getWaitQueueLength(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitQueueLength();
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with this
     * synchronizer.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate. The elements of the
     * returned collection are in no particular order.
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *                                      is not held
     * @throws IllegalArgumentException     if the given condition is
     *                                      not associated with this synchronizer
     * @throws NullPointerException         if the condition is null
     */
    public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitingThreads();
    }

    /**
     * CAS 设置head头节点 字段. 被enq（入队操作）使用.
     * 可以看到CAS中expect的值为null，也就是只有从未设置过头节点才会成功，换句话说就是头节点只会被设置一次。
     */
    private final boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    /**
     * CAS 设置tail尾节点字段. 被enq（入队操作）使用.
     */
    private final boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    /**
     * Wait queue node class.
     * <p>
     * 等待队列是“ CLH”（Craig，Landin和 Hagersten）锁定队列的变体。
     * CLH锁通常用于自旋锁。相反，我们将它们用于阻塞同步器，但是使用相同的基本策略，
     * 即将有关线程的某些控制信息保存在其节点的前任节点中。
     * 每个节点中的“status”字段将跟踪线程是否应阻塞。
     * 节点的前任节点释放锁时会发出信号。否则，队列的每个节点都充当一个特定信号样式的monitor，
     * 该monitor包含一个等待线程。虽然status字段不控制是否授予线程锁等。
     * 如果线程排在队列的第一个，那么该线程可能会尝试获取锁。
     * 但是作为第一个尝试获取锁的线程并不能保证成功。它只赋予了争用锁的权利。
     * 因此，当前发布的竞争者线程可能需要重新等待。
     *
     * <p>
     * CLH锁中的入队，你需要原子地将新Node连接起来并作为新的tail（尾部节点）
     * 出队，你需要设置head（头节点）
     * <pre>
     *      +------+  prev +-----+       +-----+
     * head |      | <---- |     | <---- |     |  tail
     *      +------+       +-----+       +-----+
     * </pre>
     *
     * <p>插入到CLH队列中，只需要对“tail”(尾节点）执行一次原子操作，
     * 因此从未入队到入队存在一个简单的原子分界。同样，出队仅涉及更新head节点（头节点）。
     * 但是，节点需要花费更多的精力来确定其后继节点是谁，部分原因是要处理由于超时和中断而可能导致的取消。
     *
     * <p>"prev"节点(在原始CLH锁中未使用）主要用于处理取消。
     * 如果取消某个节点，则其后继节点（通常）会重新链接到未取消的前任节点。
     * 有关自旋锁情况下类似机制的说明，请参见Scott和Scherer的论文，网址为
     * http://www.cs.rochester.edu/u/scott/synchronization/
     *
     * <p>我们还使用“next”节点来实现阻塞机制。每个节点的线程ID保留在其自己的节点中，
     * 因此，前继节点通过遍历下一个链接以确定它是哪个线程，来信号下一个节点唤醒。
     * 确定后继者必须避免与新排队的节点竞争以设置其前继节点的“ next”字段。
     * 在必要时，如果节点的后继节点似乎为空时，则可以通过原子更新“tail”节点，向后检查来解决此问题。
     * （或者换句话说，next链接是一种优化，因此我们通常不需要向后扫描。）
     * <p>取消将一些保守性引入到基本算法中。由于我们必须轮询其他节点的取消，
     * 因此我们可能会遗漏没有注意到已取消的节点在我们前面还是后面。
     * 要解决此问题，必须始终在取消时unparking后继者，使他们能够稳定在新的前任者身上，
     * 除非我们能确定一个未取消的前任者将承担这一责任。
     *
     * <p> CLH队列需要一个虚拟header节点才能开始。但是，我们不会在构建过程中创建它们，
     * 因为如果没有争用，那将是徒劳。取而代之的是，构造节点，并在第一次争用时设置head和tail指针。
     *
     * <p>等待条件的线程使用相同的节点，但是使用额外的链接。
     * 条件只需要链接节点在简单（非并发）链接队列中，因为它们是仅在持有独占锁时被访问。
     * 收到信号后，该节点将转移到主队列。状态字段的特殊值用于标记节点所在的队列。
     */
    static final class Node {
        /**
         * 用于表明节点在共享模式等待的标志
         */
        static final Node SHARED = new Node();
        /**
         * 用于表明节点在独占模式等待的标志
         */
        static final Node EXCLUSIVE = null;

        /**
         * waitStatus 值用来表明线程已经取消了
         */
        static final int CANCELLED = 1;
        /**
         * waitStatus 值用来表明候机节点的线程需要unparking
         */
        static final int SIGNAL = -1;
        /**
         * waitStatus 值用来表明线程正在等待条件满足
         */
        static final int CONDITION = -2;
        /**
         * waitStatus 值用来表明下一次调用acquireShared时应该无条件广播。
         */
        static final int PROPAGATE = -3;

        /**
         * waitStatus 字段, 只会去下面的值:
         * CANCELLED:  这个节点由于超市或者中断被取消。
         * 处于CANCELLED的节点永远都会处于此状态。
         * 特别的是，已取消节点中的线程永远不会再阻塞。
         * SIGNAL:     当前节点的后继节点（或即将）被阻塞（通过park），所以当前节点
         * 必须unpark他的后继节点当它释放或者取消时。为了避免竞争，
         * acquire方法必须首先表明他们需要一个signal，然后重试原子地acquire，
         * 并且如果失败的话就阻塞。
         * CONDITION:  这个节点正处于条件队列中。
         * 该节点直到被转移其他状态才会被用作同步队列里的一个节点。
         * (此处使用此值与该字段的其他用途无关 ，仅仅为了简化机制)
         * PROPAGATE:  一个releaseShared应该被广播给其他节点.
         * 这是在doReleaseShared中设置的(仅针对head节点)，
         * 以确保广播能够继续，即使其他操作已经介入。
         * 0:          不同于以上值。
         * <p>
         * 这些值以数字方式排列以简化使用。非负值(即CANCELLED)表示节点不需要signal。
         * 因此，大多数代码不需要检查特定值，仅需检查正负即可。
         * <p>
         * 对于普通同步节点，该字段初始化为0，对于条件节点，该字段初始化为CONDITION。
         * 使用CAS（或在可能的情况下进行无条件的volatile写操作）对其进行修改。
         */
        volatile int waitStatus;

        /**
         * 链接到前继节点，当前节点/线程用前继节点来检查waitStatus。
         * 在入队期间分配，并且仅在出队时将其置空（出于GC的考虑）。
         * 同样，在取消前继节点后，我们会短路，同时找到一个未取消的前继节点，将始终能够找到该节点，
         * 因为head节点永远不会被取消：
         * 只有成功acquire后，节点才变为head节点。
         * 取消的线程永远不会成功acquire，并且线程只会取消自己，不会取消任何其他节点。
         */
        volatile Node prev;

        /**
         * 链接到后继节点，当前节点/线程在释放时将该后继节点unpark。
         * 在入队期间分配，调整时需要绕过已取消的前继节点，在出队时置空（出于GC的考虑）。
         * enq操作直到入队成功后才分配前任的next字段，因此看到一个null的next字段，
         * 并不一定意味着该节点位于队列末尾。
         * 但是，如果next字段似乎为空，则我们可以从tail扫描上prev以进行二次检查。
         * cancelled节点的next字段设置为指向自己而不是null，以使isOnSyncQueue的工作更轻松。
         */
        volatile Node next;

        /**
         * 入队节点对应的线程。 在构造时初始化，使用后置空。
         */
        volatile Thread thread;

        /**
         * 链接到等待条件的下一个节点，或者链接到特殊值SHARED。
         * 由于条件队列仅在以独占模式持有时才被访问，因此当节点等待条件时，我们只需要一个简单的链接队列来保存节点。
         * 然后将它们转移到队列中以re-acquire。
         * 并且由于条件只能是独占的，因此我们通过使用特殊值来指示共享模式来保存字段。
         */
        Node nextWaiter;

        Node() {    // 用来创建一个初始的head节点或者SHARED标志。
        }

        Node(Thread thread, Node mode) {     // 被addWaiter使用
            this.nextWaiter = mode;
            this.thread = thread;
        }

        Node(Thread thread, int waitStatus) { // 被Condition使用。
            this.waitStatus = waitStatus;
            this.thread = thread;
        }

        /**
         * 如果当前节点在共享模式等待，返回true。
         */
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * 返回前继节点，如果前节点为null则抛出空指针异常。
         *
         * @return the predecessor of this node
         */
        final Node predecessor() throws NullPointerException {
            Node p = prev;
            if (p == null)
                throw new NullPointerException();
            else
                return p;
        }
    }

    /**
     * AbstractQueuedSynchronizer的Conditon实现，用作Lock实现的基础。
     * 此类的方法文档从Lock和Condition用户的角度描述了机制，而不是行为规范。
     * 此类的导出版本通常需要随附描述条件语义的文档，这些条件语义依赖于关联的AbstractQueuedSynchronizer的语义。
     * <p>
     * 此类是可序列化的，但是所有字段都是transient的，因此反序列化ConditionObject没有waiters
     */
    public class ConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 1173984872572414699L;
        /**
         * 模式意味着退出等待时重新中断
         */
        private static final int REINTERRUPT = 1;
        /**
         * 模式意味着在退出等待时抛出InterruptedException
         */
        private static final int THROW_IE = -1;
        /**
         * 条件队列的第一个节点.
         */
        private transient Node firstWaiter;

        // 内部方法
        /**
         * 条件队列的最后一个节点.
         */
        private transient Node lastWaiter;

        /**
         * 创建一个ConditionObject实例
         */
        public ConditionObject() {
        }

        /**
         * 添加一个新的waiter到等待队列中
         *
         * @return 新添加的节点
         */
        private Node addConditionWaiter() {
            Node t = lastWaiter;
            // 如果 lastWaiter被取消了，就清理掉。
            if (t != null && t.waitStatus != Node.CONDITION) {
                //遍历条件队列取消链接非Condition状态的节点。
                unlinkCancelledWaiters();
                t = lastWaiter;
            }

            //当前线程，添加到条件队列尾部
            Node node = new Node(Thread.currentThread(), Node.CONDITION);
            if (t == null)
                firstWaiter = node;
            else
                t.nextWaiter = node;
            lastWaiter = node;
            return node;
        }

        /**
         * 删除并转移节点，直到命中未取消的一个或为null。
         * 从信号中分离出来，部分是为了鼓励编译器内联没有等待者的情况。
         *
         * @param first (non-null) 条件队列上的第一个节点
         */
        private void doSignal(Node first) {
            do {
                if ((firstWaiter = first.nextWaiter) == null)
                    lastWaiter = null;
                first.nextWaiter = null;
            } while (!transferForSignal(first) &&
                    (first = firstWaiter) != null);
        }

        // public 方法

        /**
         * 删除并转移所有节点
         *
         * @param first (non-null) 条件队列上的第一个节点
         */
        private void doSignalAll(Node first) {
            lastWaiter = firstWaiter = null;
            do {
                Node next = first.nextWaiter;
                first.nextWaiter = null;
                transferForSignal(first);
                first = next;
            } while (first != null);
        }

        /**
         * 从条件队列中取消链接已取消等待节点。
         * （注释：效果等价从firstWaiter遍历到lastWaiter，去掉中间节点状态不等于CONDITION的节点）
         * 仅在持有锁的状态下调用。
         * 当在条件等待期间发生取消操作时，以及在看到lastWaiter被取消后插入新的waiter时，调用此方法。
         * 需要这种方法来避免在没有信号(signal)的情况下遗留垃圾。
         * 所以即使它可能需要完全遍历，只有在没有信号(signal)的情况下发生超时或取消时，它才起作用。
         * 它遍历所有节点，而不是停在特定目标上，以取消所有指向垃圾节点的指针的链接，而无需在取消风暴期间进行多次遍历。
         * （注释：可以举个列子
         * Node1(CONDITION)->Node2(CANCELLED)->NODE3(CONDITION)->NODE4(CANCELLED)->null
         * 经过下面的方法就变为Node1(CONDITION)->Node3(CONDITION)->null）
         */
        private void unlinkCancelledWaiters() {
            Node t = firstWaiter;
            //trail指向上一个Condition节点
            Node trail = null;
            while (t != null) {
                Node next = t.nextWaiter;
                if (t.waitStatus != Node.CONDITION) {
                    t.nextWaiter = null;
                    if (trail == null)
                        firstWaiter = next;
                    else
                        trail.nextWaiter = next;
                    if (next == null)
                        lastWaiter = trail;
                } else
                    trail = t;
                t = next;
            }
        }

        /**
         * 将等待时间最长的线程（如果存在）从该条件的等待队列移至拥有锁的等待队列。
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *                                      returns {@code false}
         */
        public final void signal() {
            //如果不持有独占锁，就抛出异常
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            //尝试通知条件队列上firstWaiter来获取独占锁
            Node first = firstWaiter;
            if (first != null)
                doSignal(first);
        }

        /*
         * 对于可中断的等待，我们需要跟踪是否抛出InterruptedException(如果在条件下阻塞时被中断)，
         * 以及是否重新中断当前线程(如果在阻塞时中断，等待重新获得)。
         */

        /**
         * 将所有线程从这个条件的等待队列移到拥有锁的等待队列。
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *                                      returns {@code false}
         */
        public final void signalAll() {
            //如果不持有独占锁，就抛出异常
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node first = firstWaiter;
            //尝试通知条件条件队列上的所有节点来获取独占锁。
            if (first != null)
                doSignalAll(first);
        }

        /**
         * 实现不可中断的条件等待。
         * <ol>
         * <li> 保存由AbstractQueuedSynchronizer.getState()返回的锁定状态。
         * <li> 以保存的state作为参数调用AbstractQueuedSynchronizer.release（int），如果失败则抛出IllegalMonitorStateException。
         * <li> 阻塞直到被信号为止。
         * <li> 通过将保存的state作为参数调用acquire（int）的专用版本来重新获取。
         */
        public final void awaitUninterruptibly() {
            //当前线程添加到条件等待队列。
            Node node = addConditionWaiter();
            //释放节点
            int savedState = fullyRelease(node);
            boolean interrupted = false;
            while (!isOnSyncQueue(node)) {
                //node不在同步队列上，就一直尝试park休眠当前线程。
                LockSupport.park(this);
                //如果当前线程被中断了，则记录下来
                if (Thread.interrupted())
                    interrupted = true;
            }
            //如果node获取许可成功，或者当前线程被中断，就执行一次线程中断。
            if (acquireQueued(node, savedState) || interrupted)
                selfInterrupt();
        }

        /**
         * 检查中断，如果在信号之前中断，返回THROW_IE;如果在信号之后中断，返回REINTERRUPT;如果没有中断，返回0。
         */
        private int checkInterruptWhileWaiting(Node node) {
            return Thread.interrupted() ?
                    (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
                    0;
        }

        /**
         * 抛出InterruptedException，重新中断当前线程，或者什么都不做，这取决于模式。
         */
        private void reportInterruptAfterWait(int interruptMode)
                throws InterruptedException {
            if (interruptMode == THROW_IE)
                throw new InterruptedException();
            else if (interruptMode == REINTERRUPT)
                selfInterrupt();
        }

        /**
         * 实现不可中断的条件等待。
         * <ol>
         * <li>如果当前线程被中断，则抛出InterruptedException。
         * <li>保存{@link #getState}返回的锁定状态。
         * <li>使用保存的state作为参数调用{@link #release}，
         *       如果失败，则抛出IllegalMonitorStateException。
         * <li>阻塞，直到被信号，中断或超时为止。
         * <li>通过将保存的state作为参数, 调用{@link #acquire}特殊版本来重新获取    
         * <li>如果在步骤4中阻塞中而被中断，则抛出InterruptedException。
         * </ol>
         */
        public final void await() throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null) // clean up if cancelled
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
        }

        /**
         * 实现定时条件等待。
         * <p>
         * （注释：下面的说明同{@link #await()}）
         * <ol>
         * <li>如果当前线程被中断，则抛出InterruptedException。
         * <li>保存{@link #getState}返回的锁定状态。
         * <li>使用保存的state作为参数调用{@link #release}，
         *       如果失败，则抛出IllegalMonitorStateException。
         * <li>阻塞，直到被信号，中断或超时为止。
         * <li>通过将保存的state作为参数, 调用{@link #acquire}特殊版本来重新获取    
         * <li>如果在步骤4中阻塞中而被中断，则抛出InterruptedException。
         * </ol>
         */
        public final long awaitNanos(long nanosTimeout)
                throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            //添加到条件等待队列
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return deadline - System.nanoTime();
        }

        /**
         * 实现绝对等待时间的条件等待
         * <p>
         * （注释：下面的说明同{@link #await()}）
         * <ol>
         * <li>如果当前线程被中断，则抛出InterruptedException。
         * <li>保存{@link #getState}返回的锁定状态。
         * <li>使用保存的state作为参数调用{@link #release}，
         *       如果失败，则抛出IllegalMonitorStateException。
         * <li>阻塞，直到被信号，中断或超时为止。
         * <li>通过将保存的state作为参数, 调用{@link #acquire}特殊版本来重新获取    
         * <li>如果在步骤4中阻塞中而被中断，则抛出InterruptedException。
         * </ol>
         */
        public final boolean awaitUntil(Date deadline)
                throws InterruptedException {
            long abstime = deadline.getTime();
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (System.currentTimeMillis() > abstime) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                LockSupport.parkUntil(this, abstime);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        /**
         * 实现定时的条件等待
         * <p>
         * （注释：下面的说明同{@link #await()}）
         * <ol>
         * <li>如果当前线程被中断，则抛出InterruptedException。
         * <li>保存{@link #getState}返回的锁定状态。
         * <li>使用保存的state作为参数调用{@link #release}，
         *       如果失败，则抛出IllegalMonitorStateException。
         * <li>阻塞，直到被信号，中断或超时为止。
         * <li>通过将保存的state作为参数, 调用{@link #acquire}特殊版本来重新获取    
         * <li>如果在步骤4中阻塞中而被中断，则抛出InterruptedException。
         * </ol>
         */
        public final boolean await(long time, TimeUnit unit)
                throws InterruptedException {
            long nanosTimeout = unit.toNanos(time);
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        // 下面的方法都是一些支持方法。

        /**
         * 如果该条件是由给定的同步对象创建的，则返回true。
         *
         * @return {@code true} if owned
         */
        final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
            return sync == AbstractQueuedSynchronizer.this;
        }

        /**
         * 查询是否有线程在此条件下等待。
         * 实现了{@link AbstractQueuedSynchronizer#hasWaiters(ConditionObject)}.。
         *
         * @return {@code true}如果有任何正在等待的线程
         * @throws IllegalMonitorStateException 如果{@link #isHeldExclusively()} 抛出IllegalMonitorStateException
         *                                      返回{@code false}
         */
        protected final boolean hasWaiters() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    return true;
            }
            return false;
        }

        /**
         * 返回等待此条件
         * 返回在此条件下等待的线程数的估计值。
         * 实现 {@link AbstractQueuedSynchronizer#getWaitQueueLength(ConditionObject)}.
         *
         * @return 等待的线程数的估计值。
         * @throws IllegalMonitorStateException 如果{@link #isHeldExclusively()} 抛出IllegalMonitorStateException
         *                                      返回{@code false}
         */
        protected final int getWaitQueueLength() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            int n = 0;
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    ++n;
            }
            return n;
        }

        /**
         * 返回一个集合，其中包含可能在此条件下等待的线程。
         * 实现 {@link AbstractQueuedSynchronizer#getWaitingThreads(ConditionObject)}.
         *
         * @return 线程的集合
         * @throws IllegalMonitorStateException 如果{@link #isHeldExclusively()} 抛出IllegalMonitorStateException
         *                                      返回{@code false}
         */
        protected final Collection<Thread> getWaitingThreads() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                    Thread t = w.thread;
                    if (t != null)
                        list.add(t);
                }
            }
            return list;
        }
    }
}
