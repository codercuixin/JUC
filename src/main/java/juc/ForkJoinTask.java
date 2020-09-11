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

import juc.locks.ReentrantLock;
import sun.misc2.Unsafe;
import unsafeTest.GetUnsafeFromReflect;

import java.io.Serializable;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.List;
import java.util.RandomAccess;

/**
 * Abstract base class for tasks that run within a {@link juc.ForkJoinPool}.
 * A {@code ForkJoinTask} is a thread-like entity that is much
 * lighter weight than a normal thread.  Huge numbers of tasks and
 * subtasks may be hosted by a small number of actual threads in a
 * ForkJoinPool, at the price of some usage limitations.
 * 一个在ForkJoinPool中运行的任务的抽象基类。
 *  ForkJoinTask是类似于线程的实体，其比普通线程更加轻量。
 * 大量的任务和子任务可能由ForkJoinPool中的少量实际线程托管，但以某些使用限制为代价。
 *
 *
 * <p>A "main" {@code ForkJoinTask} begins execution when it is
 * explicitly submitted to a {@link juc.ForkJoinPool}, or, if not already
 * engaged in a ForkJoin computation, commenced in the {@link
 * juc.ForkJoinPool#commonPool()} via {@link #fork}, {@link #invoke}, or
 * related methods.  Once started, it will usually in turn start other
 * subtasks.  As indicated by the name of this class, many programs
 * using {@code ForkJoinTask} employ only methods {@link #fork} and
 * {@link #join}, or derivatives such as {@link
 * #invokeAll(juc.ForkJoinTask...) invokeAll}.  However, this class also
 * provides a number of other methods that can come into play in
 * advanced usages, as well as extension mechanics that allow support
 * of new forms of fork/join processing.
 * 一个“main” ForkJoinTask在显式提交给ForkJoinPool时开始执行，或者，如果尚未参与ForkJoin计算，
 * 则通过fork()，invoke（）或相关方法在ForkJoinPool.commonPool（）中开始。
 * 一旦启动，通常将依次启动其他子任务。
 * 如此类的名称所示，许多使用ForkJoinTask的程序仅使用方法fork（）和join（）或派生类（例如invokeAll）。
 * 但是，此类还提供了许多其他可以在高级用法中使用的方法，以及允许支持新形式的fork/join处理的扩展机制。
 *
 * <p>A {@code ForkJoinTask} is a lightweight form of {@link juc.Future}.
 * The efficiency of {@code ForkJoinTask}s stems from a set of
 * restrictions (that are only partially statically enforceable)
 * reflecting their main use as computational tasks calculating pure
 * functions or operating on purely isolated objects.  The primary
 * coordination mechanisms are {@link #fork}, that arranges
 * asynchronous execution, and {@link #join}, that doesn't proceed
 * until the task's result has been computed.  Computations should
 * ideally avoid {@code synchronized} methods or blocks, and should
 * minimize other blocking synchronization apart from joining other
 * tasks or using synchronizers such as Phasers that are advertised to
 * cooperate with fork/join scheduling. Subdividable tasks should also
 * not perform blocking I/O, and should ideally access variables that
 * are completely independent of those accessed by other running
 * tasks. These guidelines are loosely enforced by not permitting
 * checked exceptions such as {@code IOExceptions} to be
 * thrown. However, computations may still encounter unchecked
 * exceptions, that are rethrown to callers attempting to join
 * them. These exceptions may additionally include {@link
 * RejectedExecutionException} stemming from internal resource
 * exhaustion, such as failure to allocate internal task
 * queues. Rethrown exceptions behave in the same way as regular
 * exceptions, but, when possible, contain stack traces (as displayed
 * for example using {@code ex.printStackTrace()}) of both the thread
 * that initiated the computation as well as the thread actually
 * encountering the exception; minimally only the latter.
 * ForkJoinTask是Future的轻量级形式。
 * ForkJoinTasks的效率源于一组限制（只能部分静态地强制执行），反映了它们的主要用途是计算任务，这些计算任务是计算纯函数或对纯隔离对象进行操作。
 * 主要的协调机制是fork（）（用于安排异步执行）和join（）（在计算任务结果之前不会继续执行）。
 * 理想情况下，计算应避免使用同步的方法或块，并应最小化其他块同步，除了joining（等待）其他任务或者使用同步器（例如Phaser被告知用来与fork/join调度合作）。
 * 可细分的任务也不应执行阻塞的I / O，并且理想情况下应访问与其他正在运行的任务访问的变量完全独立的变量。
 * 不允许抛出诸如IOExceptions之类的检查异常，从而松散地实施了这些准则。
 * 但是，计算可能仍然会遇到未经检查的异常，这些异常会被尝试joining它们的调用者重新抛出。
 * 这些异常可能还包括源自内部资源耗尽的RejectedExecutionException，例如未能分配内部任务队列。
 * 重新抛出的异常的行为与常规异常相同，但是在可能的情况下，包含启动计算的线程以及实际遇到该异常的线程的堆栈跟踪（正如使用ex.printStackTrace（）显示的）；最少会有后者。
 *
 * <p>It is possible to define and use ForkJoinTasks that may block,
 * but doing do requires three further considerations: (1) Completion
 * of few if any <em>other</em> tasks should be dependent on a task
 * that blocks on external synchronization or I/O. Event-style async
 * tasks that are never joined (for example, those subclassing {@link
 * juc.CountedCompleter}) often fall into this category.  (2) To minimize
 * resource impact, tasks should be small; ideally performing only the
 * (possibly) blocking action. (3) Unless the {@link
 * juc.ForkJoinPool.ManagedBlocker} API is used, or the number of possibly
 * blocked tasks is known to be less than the pool's {@link
 * juc.ForkJoinPool#getParallelism} level, the pool cannot guarantee that
 * enough threads will be available to ensure progress or good
 * performance.
 * 可以定义和使用可能阻塞的ForkJoinTasks，但这样做还需要三点考虑：（1）只有很少的Completion（如果有的话）应该依赖于在外部同步或I / O上阻塞的任务。
 * 从未joined的事件风格的异步任务（例如，那些CountedCompleter的子类）通常属于此类。
 * （2）为了最小化资源影响，任务应该很小；理想情况下，仅执行（可能）阻塞操作。
 *  （3）除非使用了ForkJoinPool.ManagedBlocker API，或者已知可能被阻塞的任务数小于线程池的ForkJoinPool.getParallelism（）数目，否则线程池无法保证有足够的线程可用以确保进度或良好的性能。
 *
 * <p>The primary method for awaiting completion and extracting
 * results of a task is {@link #join}, but there are several variants:
 * The {@link juc.Future#get} methods support interruptible and/or timed
 * waits for completion and report results using {@code Future}
 * conventions. Method {@link #invoke} is semantically
 * equivalent to {@code fork(); join()} but always attempts to begin
 * execution in the current thread. The "<em>quiet</em>" forms of
 * these methods do not extract results or report exceptions. These
 * may be useful when a set of tasks are being executed, and you need
 * to delay processing of results or exceptions until all complete.
 * Method {@code invokeAll} (available in multiple versions)
 * performs the most common form of parallel invocation: forking a set
 * of tasks and joining them all.
 * 等待完成和提取任务结果的主要方法是join（），但有几种变体：Future.get（）方法支持可中断和/或定时等待完成，并使用Future约定报告结果。
 * 方法invoke（）在语义上等效于fork（）; join（），但总是尝试在当前线程中开始执行。
 * 这些方法的“安静”形式不会提取结果或报告异常。当执行一组任务时，这些功能可能很有用，并且需要将结果或异常的处理延迟到所有任务完成为止。
 * 方法invokeAll（有多个版本可用）执行并行调用的最常见形式：forking分派一组任务并将等待joining它们。
 *
 * <p>In the most typical usages, a fork-join pair act like a call
 * (fork) and return (join) from a parallel recursive function. As is
 * the case with other forms of recursive calls, returns (joins)
 * should be performed innermost-first. For example, {@code a.fork();
 * b.fork(); b.join(); a.join();} is likely to be substantially more
 * efficient than joining {@code a} before {@code b}.
 * 在最典型的用法中，fork-join对的作用类似于从并行递归函数中调用（fork）和返回（join）。与其他形式的递归调用一样，返回（joins）应从最里面开始执行。
 * 例如，a.fork（）; b.fork（）; b.join（）; a.join（）;可能比在b之前等待joining a更有效。
 *
 * <p>The execution status of tasks may be queried at several levels
 * of detail: {@link #isDone} is true if a task completed in any way
 * (including the case where a task was cancelled without executing);
 * {@link #isCompletedNormally} is true if a task completed without
 * cancellation or encountering an exception; {@link #isCancelled} is
 * true if the task was cancelled (in which case {@link #getException}
 * returns a {@link juc.CancellationException}); and
 * {@link #isCompletedAbnormally} is true if a task was either
 * cancelled or encountered an exception, in which case {@link
 * #getException} will return either the encountered exception or
 * {@link juc.CancellationException}.
 * 可以从多个详细级别查询任务的执行状态：
 * 如果任务以任何方式完成（包括取消任务而未执行的情况），则isDone（）为true；否则，为false。
 * 如果任务没有取消就完成或遇到异常，则isCompletedNormally（）为true；
 * 如果任务被取消，则isCancelled（）为true（在这种情况下，getException（）返回CancellationException）；
 * 如果任务被取消或遇到异常，则isCompletedAbnormally（）为true，在这种情况下，getException（）将返回遇到的异常或CancellationException。
 *
 * <p>The ForkJoinTask class is not usually directly subclassed.
 * Instead, you subclass one of the abstract classes that support a
 * particular style of fork/join processing, typically {@link
 * RecursiveAction} for most computations that do not return results,
 * {@link RecursiveTask} for those that do, and {@link
 * juc.CountedCompleter} for those in which completed actions trigger
 * other actions.  Normally, a concrete ForkJoinTask subclass declares
 * fields comprising its parameters, established in a constructor, and
 * then defines a {@code compute} method that somehow uses the control
 * methods supplied by this base class.
 * ForkJoinTask类通常不直接子类化。
 * 相反，你子类化一个抽象类，该抽象类支持一种特殊样式的fork/join处理，
 * 对于大多数不返回结果的计算通常使用RecursiveAction，
 * 对于那些返回结果的计算则通常使用RecursiveTask，
 * 对于已完成的操作会触发其他操作的那些计算则使用CountedCompleter。
 * 通常，具体的ForkJoinTask子类声明包含其参数类型的字段，该字段在构造函数中建立，然后定义一个计算方法，该方法以某种方式使用此基类提供的控制方法。
 *
 * <p>Method {@link #join} and its variants are appropriate for use
 * only when completion dependencies are acyclic; that is, the
 * parallel computation can be described as a directed acyclic graph
 * (DAG). Otherwise, executions may encounter a form of deadlock as
 * tasks cyclically wait for each other.  However, this framework
 * supports other methods and techniques (for example the use of
 * {@link Phaser}, {@link #helpQuiesce}, and {@link #complete}) that
 * may be of use in constructing custom subclasses for problems that
 * are not statically structured as DAGs. To support such usages, a
 * ForkJoinTask may be atomically <em>tagged</em> with a {@code short}
 * value using {@link #setForkJoinTaskTag} or {@link
 * #compareAndSetForkJoinTaskTag} and checked using {@link
 * #getForkJoinTaskTag}. The ForkJoinTask implementation does not use
 * these {@code protected} methods or tags for any purpose, but they
 * may be of use in the construction of specialized subclasses.  For
 * example, parallel graph traversals can use the supplied methods to
 * avoid revisiting nodes/tasks that have already been processed.
 * (Method names for tagging are bulky in part to encourage definition
 * of methods that reflect their usage patterns.)
 * 方法join（）及其变体仅在完成依赖项为无环时才适用。
 * 也就是说，并行计算可以描述为有向无环图（DAG）。
 * 否则，执行可能会遇到死锁，因为任务会周期性地相互等待。
 * 但是，此框架支持其他方法和技术（例如，使用Phaser，helpQuiesce（）和complete（V）），这些方法和技术可用于构造特定子类来解决非静态构造为DAG的问题。
 * 为了支持这种用法，可以使用setForkJoinTaskTag（short）或compareAndSetForkJoinTaskTag（short，short）方法将ForkJoinTask自动标记一个short值，
 * 并使用getForkJoinTaskTag（）方法对ForkJoinTask 进行检查。
 * ForkJoinTask实现不出于任何目的使用这些受保护的方法或标记，但是它们可能在构造特定子类中使用。
 * 例如，并行图遍历可以使用提供的方法来避免重新访问已处理的节点/任务。 （用于标记的方法名称很大一部分是为了鼓励定义反映其使用方式的方法。）
 *
 * <p>Most base support methods are {@code final}, to prevent
 * overriding of implementations that are intrinsically tied to the
 * underlying lightweight task scheduling framework.  Developers
 * creating new basic styles of fork/join processing should minimally
 * implement {@code protected} methods {@link #exec}, {@link
 * #setRawResult}, and {@link #getRawResult}, while also introducing
 * an abstract computational method that can be implemented in its
 * subclasses, possibly relying on other {@code protected} methods
 * provided by this class.
 * 大多数基类支持方法都是final的，以防止覆盖与底层轻量级任务调度框架固定绑定的实现。
 * 创建新的 fork/join 处理基本样式的开发人员应最少实现受保护的方法 exec(), setRawResult(V), and getRawResult()，同时还引入可以在其子类中实现的抽象计算方法，可能依赖于由此类提供其他受保护的方法。
 *
 * <p>ForkJoinTasks should perform relatively small amounts of
 * computation. Large tasks should be split into smaller subtasks,
 * usually via recursive decomposition. As a very rough rule of thumb,
 * a task should perform more than 100 and less than 10000 basic
 * computational steps, and should avoid indefinite looping. If tasks
 * are too big, then parallelism cannot improve throughput. If too
 * small, then memory and internal task maintenance overhead may
 * overwhelm processing.
 * ForkJoinTasks应该执行相对少量的计算。
 * 通常应通过递归分解，将大型任务拆分为较小的子任务。
 * 作为非常粗略的经验法则，任务应执行100个以上且少于10000个基本计算步骤，并应避免无限循环。
 * 如果任务太大，则并行性无法提高吞吐量。
 * 如果太小，则内存和内部任务维护开销可能会使处理不堪重负。
 *
 * <p>This class provides {@code adapt} methods for {@link Runnable}
 * and {@link juc.Callable}, that may be of use when mixing execution of
 * {@code ForkJoinTasks} with other kinds of tasks. When all tasks are
 * of this form, consider using a pool constructed in <em>asyncMode</em>.
 * 此类提供了Runnable和Callable的适配方法，在将ForkJoinTasks与其他类型任务混合执行时可能会用到。
 * 当所有任务都采用这种形式时，请考虑使用在asyncMode中构造的线程池。
 *
 * <p>ForkJoinTasks are {@code Serializable}, which enables them to be
 * used in extensions such as remote execution frameworks. It is
 * sensible to serialize tasks only before or after, but not during,
 * execution. Serialization is not relied on during execution itself.
 * ForkJoinTasks是可序列化的，这使它们可以在诸如远程执行框架的扩展中使用。
 * 仅在执行之前或之后而不是执行期间序列化任务是明智的。在执行过程中本身中不应该序列化。
 *
 * @author Doug Lea
 * @since 1.7
 */
public abstract class ForkJoinTask<V> implements Future<V>, Serializable {

    /*
     * See the internal documentation of class ForkJoinPool for a
     * general implementation overview.  ForkJoinTasks are mainly
     * responsible for maintaining their "status" field amidst relays
     * to methods in ForkJoinWorkerThread and ForkJoinPool.
     *
     * The methods of this class are more-or-less layered into
     * (1) basic status maintenance
     * (2) execution and awaiting completion
     * (3) user-level methods that additionally report results.
     * This is sometimes hard to see because this file orders exported
     * methods in a way that flows well in javadocs.
     *  有关一般的实现概述，请参见类ForkJoinPool的内部文档。
     *  ForkJoinTasks在中继到ForkJoinWorkerThread和ForkJoinPool中的方法之间，主要负责维护其“status”字段。
     *   此类的方法大致分为
     *  （1）基本状态维护
     *  （2）执行和等待完成
     *  （3）用户级方法（另外报告结果）。 有时很难看到，因为此文件以在Javadocs中良好流动的方式对导出的方法进行排序。
     *
     *
     */

    /*
     * The status field holds run control status bits packed into a
     * single int to minimize footprint and to ensure atomicity (via
     * CAS).todo this is great!
      * Status is initially zero, and takes on nonnegative
     * values until completed, upon which status (anded with
     * DONE_MASK) holds value NORMAL, CANCELLED, or EXCEPTIONAL. Tasks
     * undergoing blocking waits by other threads have the SIGNAL bit
     * set.  Completion of a stolen task with SIGNAL set awakens any
     * waiters via notifyAll. Even though suboptimal for some
     * purposes, we use basic builtin wait/notify to take advantage of
     * "monitor inflation" in JVMs that we would otherwise need to
     * emulate to avoid adding further per-task bookkeeping overhead.
     * We want these monitors to be "fat", i.e., not use biasing or
     * thin-lock techniques, so use some odd coding idioms that tend
     * to avoid them, mainly by arranging that every synchronized
     * block performs a wait, notifyAll or both.
     *  status字段将运行控制状态位打包为一个int，以最大程度地减少占用空间并确保原子性（通过CAS）。
     *  status最初为零，并采用非负值，直到完成为止，此后（与DONE_MASK一起）状态的值保持为NORMAL，CANCELLED或EXCEPTIONAL。
     *  其他线程正在等待阻塞的任务将SIGNAL位置1。
     *  一个设置了SIGNAL集合的被盗任务完成后，将通过notifyAll唤醒任何等待者。
     *  即使出于某些目的不是最优的，我们还是使用基本的内置等待/通知来利用JVM中的“监视器膨胀”，
     *  否则我们将需要模拟JVM以避免增加每个任务的记录开销。
     *  我们希望这些监视器是“胖”的，即，不使用偏置或细锁技术，因此要使用一些奇怪的编码习惯来避免它们，
     *  主要是通过安排每个同步块执行一个wait，notifyAll或两者。
     *
     * These control bits occupy only (some of) the upper half (16
     * bits) of status field. The lower bits are used for user-defined
     * tags.
     * 这些控制位仅占用status字段的(部分)上半部分（高16位）。
     * 下半部分（低16位）用于用户定义的标签。
     */

    /**
     * The run status of this task
     */
    volatile int status; // accessed directly by pool and workers
    static final int DONE_MASK = 0xf0000000;  // mask out non-completion bits
    static final int NORMAL = 0xf0000000;  // must be negative
    static final int CANCELLED = 0xc0000000;  // must be < NORMAL
    static final int EXCEPTIONAL = 0x80000000;  // must be < CANCELLED
    static final int SIGNAL = 0x00010000;  // must be >= 1 << 16
    static final int SMASK = 0x0000ffff;  // short bits for tags

    /**
     * Marks completion and wakes up threads waiting to join this
     * task.
     * 标记完成并唤醒等待加入此任务的线程。
     *
     * @param completion one of NORMAL, CANCELLED, EXCEPTIONAL
     * @return completion status on exit
     */
    private int setCompletion(int completion) {
        for (int s; ; ) {
            if ((s = status) < 0)
                return s;
            if (U.compareAndSwapInt(this, STATUS, s, s | completion)) {
                if ((s >>> 16) != 0)
                    synchronized (this) {
                        notifyAll();
                    }
                return completion;
            }
        }
    }

    /**
     * Primary execution method for stolen tasks. Unless done, calls
     * exec and records status if completed, but doesn't wait for
     * completion otherwise.
     * 被盗任务的主要执行方法。
     * 除非完成，否则调用exec并记录状态（如果完成），否则不等待完成。
     *
     * @return status on exit from this method
     */
    final int doExec() {
        int s;
        boolean completed;
        if ((s = status) >= 0) {
            try {
                completed = exec();
            } catch (Throwable rex) {
                return setExceptionalCompletion(rex);
            }
            if (completed)
                s = setCompletion(NORMAL);
        }
        return s;
    }

    /**
     * If not done, sets SIGNAL status and performs Object.wait(timeout).
     * This task may or may not be done on exit. Ignores interrupts.
     * 如果未完成，则设置SIGNAL状态并执行Object.wait（timeout）。
     * 退出时可能会或可能不会完成此任务。 忽略中断。
     *
     * @param timeout using Object.wait conventions.
     */
    final void internalWait(long timeout) {
        int s;
        if ((s = status) >= 0 && // force completer to issue notify  强制完成者发出通知
                U.compareAndSwapInt(this, STATUS, s, s | SIGNAL)) {
            synchronized (this) {
                if (status >= 0)
                    try {
                        wait(timeout);
                    } catch (InterruptedException ie) {
                    }
                else
                    notifyAll();
            }
        }
    }

    /**
     * Blocks a non-worker-thread until completion.
     * 阻塞非工作线程，直到完成。
     *
     * @return status upon completion
     */
    private int externalAwaitDone() {
        int s = ((this instanceof CountedCompleter) ? // try helping
                ForkJoinPool.common.externalHelpComplete(
                        (CountedCompleter<?>) this, 0) :
                ForkJoinPool.common.tryExternalUnpush(this) ? doExec() : 0);
        if (s >= 0 && (s = status) >= 0) {
            boolean interrupted = false;
            do {
                if (U.compareAndSwapInt(this, STATUS, s, s | SIGNAL)) {
                    synchronized (this) {
                        if (status >= 0) {
                            try {
                                wait(0L);
                            } catch (InterruptedException ie) {
                                interrupted = true;
                            }
                        } else
                            notifyAll();
                    }
                }
            } while ((s = status) >= 0);
            if (interrupted)
                Thread.currentThread().interrupt();
        }
        return s;
    }

    /**
     * Blocks a non-worker-thread until completion or interruption.
     * 阻塞一个非工作线程直到任务完成或线程中断
     */
    private int externalInterruptibleAwaitDone() throws InterruptedException {
        int s;
        if (Thread.interrupted())
            throw new InterruptedException();
        if ((s = status) >= 0 &&
                (s = ((this instanceof CountedCompleter) ?
                        ForkJoinPool.common.externalHelpComplete(
                                (CountedCompleter<?>) this, 0) :
                        ForkJoinPool.common.tryExternalUnpush(this) ? doExec() :
                                0)) >= 0) {
            while ((s = status) >= 0) {
                if (U.compareAndSwapInt(this, STATUS, s, s | SIGNAL)) {
                    synchronized (this) {
                        if (status >= 0)
                            wait(0L);
                        else
                            notifyAll();
                    }
                }
            }
        }
        return s;
    }

    /**
     * Implementation for join, get, quietlyJoin. Directly handles
     * only cases of already-completed, external wait, and
     * unfork+exec.  Others are relayed to ForkJoinPool.awaitJoin.
     * join，get，quietlyJoin的实现。
     * 仅直接处理已经完成、外部等待和unfork + exec的情况。
     * 其他的则中继到ForkJoinPool.awaitJoin。
     *
     * @return status upon completion
     */
    private int doJoin() {
        int s;
        Thread t;
        ForkJoinWorkerThread wt;
        ForkJoinPool.WorkQueue w;
        //如果status<0,表示task已完成（NORMAL，CANCELLED或EXCEPTIONAL）

        return (s = status) < 0 ? s :
                ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
                        (w = (wt = (ForkJoinWorkerThread) t).workQueue).
                                tryUnpush(this) && (s = doExec()) < 0 ? s :
                                wt.pool.awaitJoin(w, this, 0L) :
                        externalAwaitDone();
    }

    /**
     * Implementation for invoke, quietlyInvoke.
     * invoke和quietlyInvoke的实现
     *
     * @return status upon completion
     */
    private int doInvoke() {
        int s;
        Thread t;
        ForkJoinWorkerThread wt;
        return (s = doExec()) < 0 ? s :
                ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
                        (wt = (ForkJoinWorkerThread) t).pool.
                                awaitJoin(wt.workQueue, this, 0L) :
                        externalAwaitDone();
    }

    // Exception table support

    /**
     * Table of exceptions thrown by tasks, to enable reporting by
     * callers. Because exceptions are rare, we don't directly keep
     * them with task objects, but instead use a weak ref table.  Note
     * that cancellation exceptions don't appear in the table, but are
     * instead recorded as status values.
     * 任务抛出的异常表，以使调用者能够进行报告。
     * 由于异常很少见，因此我们不直接将它们保留在任务对象中，而是使用弱引用表。
     * 请注意，取消异常未出现在表中，而是记录为状态值。
     * <p>
     * Note: These statics are initialized below in static block.
     */
    private static final ExceptionNode[] exceptionTable;
    private static final ReentrantLock exceptionTableLock;
    private static final ReferenceQueue<Object> exceptionTableRefQueue;

    /**
     * Fixed capacity for exceptionTable.
     */
    private static final int EXCEPTION_MAP_CAPACITY = 32;

    /**
     * Key-value nodes for exception table.  The chained hash table
     * uses identity comparisons, full locking, and weak references
     * for keys. The table has a fixed capacity because it only
     * maintains task exceptions long enough for joiners to access
     * them, so should never become very large for sustained
     * periods. However, since we do not know when the last joiner
     * completes, we must use weak references and expunge them. We do
     * so on each operation (hence full locking). Also, some thread in
     * any ForkJoinPool will call helpExpungeStaleExceptions when its
     * pool becomes isQuiescent.
     * 异常表的键值节点。
     * 链式哈希表对键使用身份比较，完全锁定和弱引用。
     * 该表具有固定的容量，因为它仅将任务异常维护的时间足够长，
     * 以使合并者可以访问它们，因此该表在持续时间内永远不会变得很大。
     * 但是，由于我们不知道最后一个合并者何时完成，因此必须使用弱引用并将其删除。
     * 我们对每个操作都执行此操作（因此完全锁定）。
     * 另外，任何ForkJoinPool中的某个线程在其池变为isQuiescent时都会调用helpExpungeStaleExceptions。
     */
    static final class ExceptionNode extends WeakReference<juc.ForkJoinTask<?>> {
        final Throwable ex;
        ExceptionNode next;
        final long thrower;  // use id not ref to avoid weak cycles
        final int hashCode;  // store task hashCode before weak ref disappears

        ExceptionNode(juc.ForkJoinTask<?> task, Throwable ex, ExceptionNode next) {
            super(task, exceptionTableRefQueue);
            this.ex = ex;
            this.next = next;
            this.thrower = Thread.currentThread().getId();
            this.hashCode = System.identityHashCode(task);
        }
    }

    /**
     * Records exception and sets status.
     * 记录异常并设置状态
     *
     * @return status on exit
     */
    final int recordExceptionalCompletion(Throwable ex) {
        int s;
        if ((s = status) >= 0) {
            int h = System.identityHashCode(this);
            final ReentrantLock lock = exceptionTableLock;
            lock.lock();
            try {
                expungeStaleExceptions();
                ExceptionNode[] t = exceptionTable;
                int i = h & (t.length - 1);
                for (ExceptionNode e = t[i]; ; e = e.next) {
                    if (e == null) {
                        t[i] = new ExceptionNode(this, ex, t[i]);
                        break;
                    }
                    if (e.get() == this) // already present
                        break;
                }
            } finally {
                lock.unlock();
            }
            s = setCompletion(EXCEPTIONAL);
        }
        return s;
    }

    /**
     * Records exception and possibly propagates.
     * 记录异常并可能传播
     *
     * @return status on exit
     */
    private int setExceptionalCompletion(Throwable ex) {
        int s = recordExceptionalCompletion(ex);
        if ((s & DONE_MASK) == EXCEPTIONAL)
            internalPropagateException(ex);
        return s;
    }

    /**
     * Hook for exception propagation support for tasks with completers.
     * 为具有完成程序的任务获取异常传播支持。
     */
    void internalPropagateException(Throwable ex) {
    }

    /**
     * Cancels, ignoring any exceptions thrown by cancel. Used during
     * worker and pool shutdown. Cancel is spec'ed not to throw any
     * exceptions, but if it does anyway, we have no recourse during
     * shutdown, so guard against this case.
     * 取消，忽略由取消引发的任何异常。
     * 在工作线程和池关闭期间使用。
     * 取消被指定为不引发任何异常，但是如果还是抛出了异常，我们在关闭过程中没有追索权，因此要避免这种情况。
     *
     *
     */
    static final void cancelIgnoringExceptions(juc.ForkJoinTask<?> t) {
        if (t != null && t.status >= 0) {
            try {
                t.cancel(false);
            } catch (Throwable ignore) {
            }
        }
    }

    /**
     * Removes exception node and clears status.
     */
    private void clearExceptionalCompletion() {
        int h = System.identityHashCode(this);
        final ReentrantLock lock = exceptionTableLock;
        lock.lock();
        try {
            ExceptionNode[] t = exceptionTable;
            int i = h & (t.length - 1);
            ExceptionNode e = t[i];
            ExceptionNode pred = null;
            while (e != null) {
                ExceptionNode next = e.next;
                if (e.get() == this) {
                    if (pred == null)
                        t[i] = next;
                    else
                        pred.next = next;
                    break;
                }
                pred = e;
                e = next;
            }
            expungeStaleExceptions();
            status = 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a rethrowable exception for the given task, if
     * available. To provide accurate stack traces, if the exception
     * was not thrown by the current thread, we try to create a new
     * exception of the same type as the one thrown, but with the
     * recorded exception as its cause. If there is no such
     * constructor, we instead try to use a no-arg constructor,
     * followed by initCause, to the same effect. If none of these
     * apply, or any fail due to other exceptions, we return the
     * recorded exception, which is still correct, although it may
     * contain a misleading stack trace.
     *
     * @return the exception, or null if none
     */
    private Throwable getThrowableException() {
        if ((status & DONE_MASK) != EXCEPTIONAL)
            return null;
        int h = System.identityHashCode(this);
        ExceptionNode e;
        final ReentrantLock lock = exceptionTableLock;
        lock.lock();
        try {
            expungeStaleExceptions();
            ExceptionNode[] t = exceptionTable;
            e = t[h & (t.length - 1)];
            while (e != null && e.get() != this)
                e = e.next;
        } finally {
            lock.unlock();
        }
        Throwable ex;
        if (e == null || (ex = e.ex) == null)
            return null;
        if (e.thrower != Thread.currentThread().getId()) {
            Class<? extends Throwable> ec = ex.getClass();
            try {
                Constructor<?> noArgCtor = null;
                Constructor<?>[] cs = ec.getConstructors();// public ctors only
                for (int i = 0; i < cs.length; ++i) {
                    Constructor<?> c = cs[i];
                    Class<?>[] ps = c.getParameterTypes();
                    if (ps.length == 0)
                        noArgCtor = c;
                    else if (ps.length == 1 && ps[0] == Throwable.class) {
                        Throwable wx = (Throwable) c.newInstance(ex);
                        return (wx == null) ? ex : wx;
                    }
                }
                if (noArgCtor != null) {
                    Throwable wx = (Throwable) (noArgCtor.newInstance());
                    if (wx != null) {
                        wx.initCause(ex);
                        return wx;
                    }
                }
            } catch (Exception ignore) {
            }
        }
        return ex;
    }

    /**
     * Poll stale refs and remove them. Call only while holding lock.
     * 轮询陈旧的ref并将其删除。 仅在持有锁的状态下调用。
     */
    private static void expungeStaleExceptions() {
        for (Object x; (x = exceptionTableRefQueue.poll()) != null; ) {
            if (x instanceof ExceptionNode) {
                int hashCode = ((ExceptionNode) x).hashCode;
                ExceptionNode[] t = exceptionTable;
                int i = hashCode & (t.length - 1);
                ExceptionNode e = t[i];
                ExceptionNode pred = null;
                while (e != null) {
                    ExceptionNode next = e.next;
                    if (e == x) {
                        if (pred == null)
                            t[i] = next;
                        else
                            pred.next = next;
                        break;
                    }
                    pred = e;
                    e = next;
                }
            }
        }
    }

    /**
     * If lock is available, poll stale refs and remove them.
     * Called from ForkJoinPool when pools become quiescent.
     * 如果有锁可用，则轮询过时的引用并删除它们。
     * 当池变为静态时从ForkJoinPool调用。
     */
    static final void helpExpungeStaleExceptions() {
        final ReentrantLock lock = exceptionTableLock;
        if (lock.tryLock()) {
            try {
                expungeStaleExceptions();
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * A version of "sneaky throw" to relay exceptions
     */
    static void rethrow(Throwable ex) {
        if (ex != null)
            juc.ForkJoinTask.<RuntimeException>uncheckedThrow(ex);
    }

    /**
     * The sneaky part of sneaky throw, relying on generics
     * limitations to evade compiler complaints about rethrowing
     * unchecked exceptions
     */
    @SuppressWarnings("unchecked")
    static <T extends Throwable>
    void uncheckedThrow(Throwable t) throws T {
        throw (T) t; // rely on vacuous cast
    }

    /**
     * Throws exception, if any, associated with the given status.
     */
    private void reportException(int s) {
        if (s == CANCELLED)
            throw new CancellationException();
        if (s == EXCEPTIONAL)
            rethrow(getThrowableException());
    }

    // public methods

    /**
     * Arranges to asynchronously execute this task in the pool the
     * current task is running in, if applicable, or using the {@link
     * ForkJoinPool#commonPool()} if not {@link #inForkJoinPool}.  While
     * it is not necessarily enforced, it is a usage error to fork a
     * task more than once unless it has completed and been
     * reinitialized.  Subsequent modifications to the state of this
     * task or any data it operates on are not necessarily
     * consistently observable by any thread other than the one
     * executing it unless preceded by a call to {@link #join} or
     * related methods, or a call to {@link #isDone} returning {@code
     * true}.
     * 安排在当前任务正在运行的池中异步执行此任务（如果适用），如果不在inForkJoinPool（）中，则使用ForkJoinPool.commonPool（）。
     * 尽管不一定要强制执行它，除非任务已完成并重新初始化，否则多次划分（forK）任务，这是使用错误。
     * 此任务的状态或对其执行的任何数据的后续修改都不一定可由执行该线程的线程以外的任何线程一致地观察到，
     * 除非在这之前对join（）或相关方法的调用,或对isDone（）的调用返回true 。
     *
     * @return {@code this}, to simplify usage
     */
    public final juc.ForkJoinTask<V> fork() {
        Thread t;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread)
            ((ForkJoinWorkerThread) t).workQueue.push(this);
        else
            ForkJoinPool.common.externalPush(this);
        return this;
    }

    /**
     * Returns the result of the computation when it {@link #isDone is
     * done}.  This method differs from {@link #get()} in that
     * abnormal completion results in {@code RuntimeException} or
     * {@code Error}, not {@code ExecutionException}, and that
     * interrupts of the calling thread do <em>not</em> cause the
     * method to abruptly return by throwing {@code
     * InterruptedException}.
     * 完成后返回计算结果。
     * 此方法与get（）的不同之处在于，异常完成会导致RuntimeException或Error，而不是ExecutionException，
     * 并且调用线程的中断不会导致该方法通过抛出InterruptedException突然返回。
     *
     * @return the computed result
     */
    public final V join() {
        int s;
        if ((s = doJoin() & DONE_MASK) != NORMAL)
            reportException(s);
        return getRawResult();
    }

    /**
     * Commences performing this task, awaits its completion if
     * necessary, and returns its result, or throws an (unchecked)
     * {@code RuntimeException} or {@code Error} if the underlying
     * computation did so.
     * 开始执行此任务，在必要时等待其完成，然后返回其结果，如果底层计算已执行，
     * 则抛出（未经检查）{@code RuntimeException}或{@code Error}。
     *
     * @return the computed result
     */
    public final V invoke() {
        int s;
        if ((s = doInvoke() & DONE_MASK) != NORMAL)
            reportException(s);
        return getRawResult();
    }

    /**
     * Forks the given tasks, returning when {@code isDone} holds for
     * each task or an (unchecked) exception is encountered, in which
     * case the exception is rethrown. If more than one task
     * encounters an exception, then this method throws any one of
     * these exceptions. If any task encounters an exception, the
     * other may be cancelled. However, the execution status of
     * individual tasks is not guaranteed upon exceptional return. The
     * status of each task may be obtained using {@link
     * #getException()} and related methods to check if they have been
     * cancelled, completed normally or exceptionally, or left
     * unprocessed.
     * 分叉给定的任务，在每个任务都是{@code isDone}的或遇到（未检查的）异常时返回，在这种情况下，该异常被重新抛出。
     * 如果一个以上的任务遇到一个异常，则此方法将抛出这些异常中的任何一个。
     * 如果任何任务遇到异常，则其他任务可能会被取消。
     * 但是，不能保证在异常返回时单个任务的执行状态。
     * 可以使用{@link #getException（）}和相关方法来检查每个任务的状态，以检查它们是否已被取消，正常或异常完成或未处理。
     *
     * @param t1 the first task
     * @param t2 the second task
     * @throws NullPointerException if any task is null
     */
    public static void invokeAll(juc.ForkJoinTask<?> t1, juc.ForkJoinTask<?> t2) {
        int s1, s2;
        t2.fork();
        if ((s1 = t1.doInvoke() & DONE_MASK) != NORMAL)
            t1.reportException(s1);
        if ((s2 = t2.doJoin() & DONE_MASK) != NORMAL)
            t2.reportException(s2);
    }

    /**
     * Forks the given tasks, returning when {@code isDone} holds for
     * each task or an (unchecked) exception is encountered, in which
     * case the exception is rethrown. If more than one task
     * encounters an exception, then this method throws any one of
     * these exceptions. If any task encounters an exception, others
     * may be cancelled. However, the execution status of individual
     * tasks is not guaranteed upon exceptional return. The status of
     * each task may be obtained using {@link #getException()} and
     * related methods to check if they have been cancelled, completed
     * normally or exceptionally, or left unprocessed.
     * 分叉给定的任务，在每个任务都是{@code isDone}的或遇到（未检查的）异常时返回，在这种情况下，该异常被重新抛出。
     * 如果一个以上的任务遇到一个异常，则此方法将抛出这些异常中的任何一个。
     * 如果任何任务遇到异常，则其他任务可能会被取消。
     * 但是，不能保证在异常返回时单个任务的执行状态。
     * 可以使用{@link #getException（）}和相关方法来检查每个任务的状态，以检查它们是否已被取消，正常或异常完成或未处理。
     *
     * @param tasks the tasks
     * @throws NullPointerException if any task is null
     */
    public static void invokeAll(juc.ForkJoinTask<?>... tasks) {
        Throwable ex = null;
        int last = tasks.length - 1;
        for (int i = last; i >= 0; --i) {
            juc.ForkJoinTask<?> t = tasks[i];
            if (t == null) {
                if (ex == null)
                    ex = new NullPointerException();
            } else if (i != 0)
                t.fork();
            else if (t.doInvoke() < NORMAL && ex == null)
                ex = t.getException();
        }
        for (int i = 1; i <= last; ++i) {
            juc.ForkJoinTask<?> t = tasks[i];
            if (t != null) {
                if (ex != null)
                    t.cancel(false);
                else if (t.doJoin() < NORMAL)
                    ex = t.getException();
            }
        }
        if (ex != null)
            rethrow(ex);
    }

    /**
     * Forks all tasks in the specified collection, returning when
     * {@code isDone} holds for each task or an (unchecked) exception
     * is encountered, in which case the exception is rethrown. If
     * more than one task encounters an exception, then this method
     * throws any one of these exceptions. If any task encounters an
     * exception, others may be cancelled. However, the execution
     * status of individual tasks is not guaranteed upon exceptional
     * return. The status of each task may be obtained using {@link
     * #getException()} and related methods to check if they have been
     * cancelled, completed normally or exceptionally, or left
     * unprocessed.
     * 分叉指定集合中的所有任务，当每个任务是{@code isDone}的或遇到（未检查的）异常时返回，在这种情况下，异常被重新抛出。
     * 如果一个以上的任务遇到一个异常，则此方法将引发这些异常中的任何一个。
     * 如果任何任务遇到异常，则其他任务可能会被取消。
     * 但是，不能保证在异常返回时单个任务的执行状态。
     * 可以使用{@link #getException（）}和相关方法来获取每个任务的状态，以检查它们是否已取消，是否正常或异常完成或未处理。
     *
     * @param tasks the collection of tasks
     * @param <T>   the type of the values returned from the tasks
     * @return the tasks argument, to simplify usage
     * @throws NullPointerException if tasks or any element are null
     */
    public static <T extends juc.ForkJoinTask<?>> Collection<T> invokeAll(Collection<T> tasks) {
        if (!(tasks instanceof RandomAccess) || !(tasks instanceof List<?>)) {
            invokeAll(tasks.toArray(new juc.ForkJoinTask<?>[tasks.size()]));
            return tasks;
        }
        @SuppressWarnings("unchecked")
        List<? extends juc.ForkJoinTask<?>> ts =
                (List<? extends juc.ForkJoinTask<?>>) tasks;
        Throwable ex = null;
        int last = ts.size() - 1;
        for (int i = last; i >= 0; --i) {
            juc.ForkJoinTask<?> t = ts.get(i);
            if (t == null) {
                if (ex == null)
                    ex = new NullPointerException();
            } else if (i != 0)
                t.fork();
            else if (t.doInvoke() < NORMAL && ex == null)
                ex = t.getException();
        }
        for (int i = 1; i <= last; ++i) {
            juc.ForkJoinTask<?> t = ts.get(i);
            if (t != null) {
                if (ex != null)
                    t.cancel(false);
                else if (t.doJoin() < NORMAL)
                    ex = t.getException();
            }
        }
        if (ex != null)
            rethrow(ex);
        return tasks;
    }

    /**
     * Attempts to cancel execution of this task. This attempt will
     * fail if the task has already completed or could not be
     * cancelled for some other reason. If successful, and this task
     * has not started when {@code cancel} is called, execution of
     * this task is suppressed. After this method returns
     * successfully, unless there is an intervening call to {@link
     * #reinitialize}, subsequent calls to {@link #isCancelled},
     * {@link #isDone}, and {@code cancel} will return {@code true}
     * and calls to {@link #join} and related methods will result in
     * {@code CancellationException}.
     * 尝试取消执行此任务。
     * 如果任务已经完成或由于其他原因无法取消，则此尝试将失败。
     * 如果成功，并且在调用{@code cancel}时此任务尚未开始，则将禁止执行此任务。
     * 此方法成功返回后，除非介入{@link #reinitialize}，
     * 否则随后对{@link #isCancelled}，{@link #isDone}和{@code cancel}的调用将返回{@code true}
     * 以及对{@link #join}和相关方法的调用将导致{@code CancellationException}。
     *
     *
     *
     * <p>This method may be overridden in subclasses, but if so, must
     * still ensure that these properties hold. In particular, the
     * {@code cancel} method itself must not throw exceptions.
     * <p>此方法可以在子类中重写，但是如果是这样，则必须仍然确保这些属性成立。
     * 特别是，{@code cancel}方法本身不得抛出异常。
     *
     * <p>This method is designed to be invoked by <em>other</em>
     * tasks. To terminate the current task, you can just return or
     * throw an unchecked exception from its computation method, or
     * invoke {@link #completeExceptionally(Throwable)}.
     * <p>此方法旨在由<em>其他</ em>任务调用。
     * 要终止当前任务，只需从其计算方法返回或抛出未经检查的异常，或调用{@link #completeExceptionally（Throwable）}。
     *
     * @param mayInterruptIfRunning this value has no effect in the
     *                              default implementation because interrupts are not used to
     *                              control cancellation.
     * @return {@code true} if this task is now cancelled
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        return (setCompletion(CANCELLED) & DONE_MASK) == CANCELLED;
    }

    public final boolean isDone() {
        return status < 0;
    }

    public final boolean isCancelled() {
        return (status & DONE_MASK) == CANCELLED;
    }

    /**
     * Returns {@code true} if this task threw an exception or was cancelled.
     * 如果这个任务抛出异常或是被取消，就返回true
     *
     * @return {@code true} if this task threw an exception or was cancelled
     */
    public final boolean isCompletedAbnormally() {
        return status < NORMAL;
    }

    /**
     * Returns {@code true} if this task completed without throwing an
     * exception and was not cancelled.
     * 如果任务完成时没有抛出异常并且没有被取消，则返回true
     *
     * @return {@code true} if this task completed without throwing an
     * exception and was not cancelled
     */
    public final boolean isCompletedNormally() {
        return (status & DONE_MASK) == NORMAL;
    }

    /**
     * Returns the exception thrown by the base computation, or a
     * {@code CancellationException} if cancelled, or {@code null} if
     * none or if the method has not yet completed.
     * 返回基础计算引发的异常；如果任务被取消，则返回{@code CancellationException}；
     * 如果没有异常或者该方法尚未完成，则返回{@code null}。
     *
     * @return the exception, or {@code null} if none
     */
    public final Throwable getException() {
        int s = status & DONE_MASK;
        return ((s >= NORMAL) ? null :
                (s == CANCELLED) ? new CancellationException() :
                        getThrowableException());
    }

    /**
     * Completes this task abnormally, and if not already aborted or
     * cancelled, causes it to throw the given exception upon
     * {@code join} and related operations. This method may be used
     * to induce exceptions in asynchronous tasks, or to force
     * completion of tasks that would not otherwise complete.  Its use
     * in other situations is discouraged.  This method is
     * overridable, but overridden versions must invoke {@code super}
     * implementation to maintain guarantees.
     * 异常完成此任务，并且如果尚未中止或取消，则会导致该任务在{@code join}和相关操作上引发给定的异常。
     * 此方法可用于在异步任务中引发异常，或强制完成原本不会完成的任务。
     * 不建议在其他情况下使用它。
     * 此方法是可重写的，但是被重写的版本必须调用{@code super}实现来维护保证。
     *
     * @param ex the exception to throw. If this exception is not a
     *           {@code RuntimeException} or {@code Error}, the actual exception
     *           thrown will be a {@code RuntimeException} with cause {@code ex}.
     */
    public void completeExceptionally(Throwable ex) {
        setExceptionalCompletion((ex instanceof RuntimeException) ||
                (ex instanceof Error) ? ex :
                new RuntimeException(ex));
    }

    /**
     * Completes this task, and if not already aborted or cancelled,
     * returning the given value as the result of subsequent
     * invocations of {@code join} and related operations. This method
     * may be used to provide results for asynchronous tasks, or to
     * provide alternative handling for tasks that would not otherwise
     * complete normally. Its use in other situations is
     * discouraged. This method is overridable, but overridden
     * versions must invoke {@code super} implementation to maintain
     * guarantees.
     * 完成此任务，如果尚未终止或取消，则返回该给定值作为随后调用{@code join}和相关操作的结果。
     * 此方法可用于为异步任务提供结果，或为否则无法正常完成的任务提供替代处理。
     * 不建议在其他情况下使用它。
     * 此方法是可重写的，但是被重写的版本必须调用{@code super}实现来维护保证。
     *
     *
     * @param value the result value for this task
     */
    public void complete(V value) {
        try {
            setRawResult(value);
        } catch (Throwable rex) {
            setExceptionalCompletion(rex);
            return;
        }
        setCompletion(NORMAL);
    }

    /**
     * Completes this task normally without setting a value. The most
     * recent value established by {@link #setRawResult} (or {@code
     * null} by default) will be returned as the result of subsequent
     * invocations of {@code join} and related operations.
     * 通常无需设置一个值即可完成此任务。
     * 由{@link #setRawResult}建立的最新值（默认为{@code null}）将作为后续调用{@code join}和相关操作的结果而返回。
     * @since 1.8
     */
    public final void quietlyComplete() {
        setCompletion(NORMAL);
    }

    /**
     * Waits if necessary for the computation to complete, and then
     * retrieves its result.
     * 等待必要的计算完成，然后检索其结果。
     * todo 重点
     *
     * @return the computed result
     * @throws CancellationException if the computation was cancelled
     * @throws ExecutionException    if the computation threw an
     *                               exception
     * @throws InterruptedException  if the current thread is not a
     *                               member of a ForkJoinPool and was interrupted while waiting
     */
    public final V get() throws InterruptedException, ExecutionException {
        int s = (Thread.currentThread() instanceof ForkJoinWorkerThread) ?
                doJoin() : externalInterruptibleAwaitDone();
        Throwable ex;
        if ((s &= DONE_MASK) == CANCELLED)
            throw new CancellationException();
        if (s == EXCEPTIONAL && (ex = getThrowableException()) != null)
            throw new ExecutionException(ex);
        return getRawResult();
    }

    /**
     * Waits if necessary for at most the given time for the computation
     * to complete, and then retrieves its result, if available.
     * 必要时最多等待给定时间以完成计算，然后检索其结果（如果有）。
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @return the computed result
     * @throws CancellationException if the computation was cancelled
     * @throws ExecutionException    if the computation threw an
     *                               exception
     * @throws InterruptedException  if the current thread is not a
     *                               member of a ForkJoinPool and was interrupted while waiting
     * @throws TimeoutException      if the wait timed out
     */
    public final V get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        int s;
        long nanos = unit.toNanos(timeout);
        if (Thread.interrupted())
            throw new InterruptedException();
        if ((s = status) >= 0 && nanos > 0L) {
            long d = System.nanoTime() + nanos;
            long deadline = (d == 0L) ? 1L : d; // avoid 0
            Thread t = Thread.currentThread();
            if (t instanceof ForkJoinWorkerThread) {
                ForkJoinWorkerThread wt = (ForkJoinWorkerThread) t;
                s = wt.pool.awaitJoin(wt.workQueue, this, deadline);
            } else if ((s = ((this instanceof CountedCompleter) ?
                    ForkJoinPool.common.externalHelpComplete(
                            (CountedCompleter<?>) this, 0) :
                    ForkJoinPool.common.tryExternalUnpush(this) ?
                            doExec() : 0)) >= 0) {
                long ns, ms; // measure in nanosecs, but wait in millisecs
                while ((s = status) >= 0 &&
                        (ns = deadline - System.nanoTime()) > 0L) {
                    if ((ms = TimeUnit.NANOSECONDS.toMillis(ns)) > 0L &&
                            U.compareAndSwapInt(this, STATUS, s, s | SIGNAL)) {
                        synchronized (this) {
                            if (status >= 0)
                                wait(ms); // OK to throw InterruptedException
                            else
                                notifyAll();
                        }
                    }
                }
            }
        }
        if (s >= 0)
            s = status;
        if ((s &= DONE_MASK) != NORMAL) {
            Throwable ex;
            if (s == CANCELLED)
                throw new CancellationException();
            if (s != EXCEPTIONAL)
                throw new TimeoutException();
            if ((ex = getThrowableException()) != null)
                throw new ExecutionException(ex);
        }
        return getRawResult();
    }

    /**
     * Joins this task, without returning its result or throwing its
     * exception. This method may be useful when processing
     * collections of tasks when some have been cancelled or otherwise
     * known to have aborted.
     * 合并此任务，而不返回其结果或引发其异常。
     * 当处理其中一些已被取消或已知中止的任务集合时，此方法可能很有用。
     */
    public final void quietlyJoin() {
        doJoin();
    }

    /**
     * Commences performing this task and awaits its completion if
     * necessary, without returning its result or throwing its
     * exception.
     * 开始执行此任务，并在必要时等待其完成，而不会返回结果或引发异常。
     */
    public final void quietlyInvoke() {
        doInvoke();
    }

    /**
     * Possibly executes tasks until the pool hosting the current task
     * {@link ForkJoinPool#isQuiescent is quiescent}. This method may
     * be of use in designs in which many tasks are forked, but none
     * are explicitly joined, instead executing them until all are
     * processed.
     * 可能会执行任务，直到托管当前任务的池{@link ForkJoinPool＃isQuiescent是静态的}为止。
     * 此方法可能在许多任务被分叉但没有明确地合并任何任务的设计中使用，而不是执行它们直到所有任务都被处理。
     */
    public static void helpQuiesce() {
        Thread t;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) {
            ForkJoinWorkerThread wt = (ForkJoinWorkerThread) t;
            wt.pool.helpQuiescePool(wt.workQueue);
        } else
            ForkJoinPool.quiesceCommonPool();
    }

    /**
     * Resets the internal bookkeeping state of this task, allowing a
     * subsequent {@code fork}. This method allows repeated reuse of
     * this task, but only if reuse occurs when this task has either
     * never been forked, or has been forked, then completed and all
     * outstanding joins of this task have also completed. Effects
     * under any other usage conditions are not guaranteed.
     * This method may be useful when executing
     * pre-constructed trees of subtasks in loops.
     * 重置此任务的内部记录状态，从而允许后续的{@code fork}调用。
     * 此方法允许重复使用此任务，但仅当此任务从未被分叉过，或已被分叉过，然后完成并且此任务的所有未完成合并也已完成时，才发生重用。
     * 不保证在任何其他使用条件下的效果。
     * 在循环中执行子任务的预构建树时，此方法可能很有用。
     *
     * <p>Upon completion of this method, {@code isDone()} reports
     * {@code false}, and {@code getException()} reports {@code
     * null}. However, the value returned by {@code getRawResult} is
     * unaffected. To clear this value, you can invoke {@code
     * setRawResult(null)}.
     * <p>此方法完成后，{@code isDone()}报告{@code false}，并且{@code getException()}报告{@code null}。
     * 但是，{@code getRawResult}返回的值不受影响。
     * 要清除此值，可以调用{@code setRawResult(null)}。
     */
    public void reinitialize() {
        if ((status & DONE_MASK) == EXCEPTIONAL)
            clearExceptionalCompletion();
        else
            status = 0;
    }

    /**
     * Returns the pool hosting the current task execution, or null
     * if this task is executing outside of any ForkJoinPool.
     * 返回托管当前任务执行的池，如果此任务在任何ForkJoinPool之外执行，则返回null。
     *
     * @return the pool, or {@code null} if none
     * @see #inForkJoinPool
     */
    public static ForkJoinPool getPool() {
        Thread t = Thread.currentThread();
        return (t instanceof ForkJoinWorkerThread) ?
                ((ForkJoinWorkerThread) t).pool : null;
    }

    /**
     * Returns {@code true} if the current thread is a {@link
     * ForkJoinWorkerThread} executing as a ForkJoinPool computation.
     * 如果当前线程是作为ForkJoinPool计算执行的{@link ForkJoinWorkerThread}，则返回{@code true}。
     *
     * @return {@code true} if the current thread is a {@link
     * ForkJoinWorkerThread} executing as a ForkJoinPool computation,
     * or {@code false} otherwise
     */
    public static boolean inForkJoinPool() {
        return Thread.currentThread() instanceof ForkJoinWorkerThread;
    }

    /**
     * Tries to unschedule this task for execution. This method will
     * typically (but is not guaranteed to) succeed if this task is
     * the most recently forked task by the current thread, and has
     * not commenced executing in another thread.  This method may be
     * useful when arranging alternative local processing of tasks
     * that could have been, but were not, stolen.
     * 尝试取消调度此任务的执行。
     * 如果此任务是当前线程最近分叉的任务，并且尚未开始在另一个线程中执行，则此方法通常（但不能保证）成功。
     * 当安排可能已经但没有被盗的任务的替代本地处理时，此方法可能很有用。
     *
     * @return {@code true} if unforked
     */
    public boolean tryUnfork() {
        Thread t;
        return (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
                ((ForkJoinWorkerThread) t).workQueue.tryUnpush(this) :
                ForkJoinPool.common.tryExternalUnpush(this));
    }

    /**
     * Returns an estimate of the number of tasks that have been
     * forked by the current worker thread but not yet executed. This
     * value may be useful for heuristic decisions about whether to
     * fork other tasks.
     * 返回当前工作线程已分叉但尚未执行的任务数量的估计值。
     * 对于有关是否分叉其他任务的启发式决策，此值可能有用。
     *
     * @return the number of tasks
     */
    public static int getQueuedTaskCount() {
        Thread t;
        ForkJoinPool.WorkQueue q;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread)
            q = ((ForkJoinWorkerThread) t).workQueue;
        else
            q = ForkJoinPool.commonSubmitterQueue();
        return (q == null) ? 0 : q.queueSize();
    }

    /**
     * Returns an estimate of how many more locally queued tasks are
     * held by the current worker thread than there are other worker
     * threads that might steal them, or zero if this thread is not
     * operating in a ForkJoinPool. This value may be useful for
     * heuristic decisions about whether to fork other tasks. In many
     * usages of ForkJoinTasks, at steady state, each worker should
     * aim to maintain a small constant surplus (for example, 3) of
     * tasks, and to process computations locally if this threshold is
     * exceeded.
     * 返回一个估计值，该估计值是当前工作线程所保存的本地排队任务比其他可能窃取它们的工作线程多多少；
     * 如果该线程不在ForkJoinPool中运行，则返回零。
     * 对于有关是否分叉其他任务的启发式决策，此值可能有用。
     * 在ForkJoinTasks的许多用法中，在稳定状态下，每个工作线程都应力争保持少量恒定的剩余任务（例如3个），
     * 并在超出此阈值的情况下本地处理计算。
     *
     * @return the surplus number of tasks, which may be negative
     */
    public static int getSurplusQueuedTaskCount() {
        return ForkJoinPool.getSurplusQueuedTaskCount();
    }

    // Extension methods

    /**
     * Returns the result that would be returned by {@link #join}, even
     * if this task completed abnormally, or {@code null} if this task
     * is not known to have been completed.  This method is designed
     * to aid debugging, as well as to support extensions. Its use in
     * any other context is discouraged.
     * 返回将由{@link #join}返回的结果（即使此任务异常完成），或者返回{@code null}（如果未知该任务尚未完成）。
     * 此方法旨在帮助调试并支持扩展。不建议在其他任何上下文中使用它。
     *
     * @return the result, or {@code null} if not completed
     */
    public abstract V getRawResult();

    /**
     * Forces the given value to be returned as a result.  This method
     * is designed to support extensions, and should not in general be
     * called otherwise.
     * 强制将给定值作为结果返回。 此方法旨在支持扩展，一般不应以其他方式调用。
     *
     * @param value the value
     */
    protected abstract void setRawResult(V value);

    /**
     * Immediately performs the base action of this task and returns
     * true if, upon return from this method, this task is guaranteed
     * to have completed normally. This method may return false
     * otherwise, to indicate that this task is not necessarily
     * complete (or is not known to be complete), for example in
     * asynchronous actions that require explicit invocations of
     * completion methods. This method may also throw an (unchecked)
     * exception to indicate abnormal exit. This method is designed to
     * support extensions, and should not in general be called
     * otherwise.
     * 立即执行此任务的基本操作，如果从该方法返回后，保证此任务已正常完成，则返回true。
     * 否则，此方法可能返回false，以指示此任务不一定是完成的（或不知道是完成的），例如在需要显式调用完成方法的异步操作中。
     * 此方法还可能引发（未经检查的）异常以指示异常退出。
     * 此方法旨在支持扩展，一般不应以其他方式调用。
     * @return {@code true} if this task is known to have completed normally
     */
    protected abstract boolean exec();

    /**
     * Returns, but does not unschedule or execute, a task queued by
     * the current thread but not yet executed, if one is immediately
     * available. There is no guarantee that this task will actually
     * be polled or executed next. Conversely, this method may return
     * null even if a task exists but cannot be accessed without
     * contention with other threads.  This method is designed
     * primarily to support extensions, and is unlikely to be useful
     * otherwise.
     * 如果一个任务立即可用，则返回该任务，但不取消调度或执行该任务，该任务由当前线程排队但尚未执行。
     * 无法保证此任务将在下一步实际被轮询或执行。
     * 相反，即使存在任务，但如果不与其他线程争用，则该方法可能返回null。
     * 此方法主要是为了支持扩展而设计的，否则不太可能有用。
     *
     * @return the next task, or {@code null} if none are available
     */
    protected static juc.ForkJoinTask<?> peekNextLocalTask() {
        Thread t;
        ForkJoinPool.WorkQueue q;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread)
            q = ((ForkJoinWorkerThread) t).workQueue;
        else
            q = ForkJoinPool.commonSubmitterQueue();
        return (q == null) ? null : q.peek();
    }

    /**
     * Unschedules and returns, without executing, the next task
     * queued by the current thread but not yet executed, if the
     * current thread is operating in a ForkJoinPool.  This method is
     * designed primarily to support extensions, and is unlikely to be
     * useful otherwise.
     * 如果当前线程在ForkJoinPool中运行，则取消调度并返回，而不执行当前线程中排队但尚未执行的下一个任务。
     * 此方法主要是为了支持扩展而设计的，否则不太可能有用。
     *
     * @return the next task, or {@code null} if none are available
     */
    protected static juc.ForkJoinTask<?> pollNextLocalTask() {
        Thread t;
        return ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
                ((ForkJoinWorkerThread) t).workQueue.nextLocalTask() :
                null;
    }

    /**
     * If the current thread is operating in a ForkJoinPool,
     * unschedules and returns, without executing, the next task
     * queued by the current thread but not yet executed, if one is
     * available, or if not available, a task that was forked by some
     * other thread, if available. Availability may be transient, so a
     * {@code null} result does not necessarily imply quiescence of
     * the pool this task is operating in.  This method is designed
     * primarily to support extensions, and is unlikely to be useful
     * otherwise.
     * 如果当前线程在ForkJoinPool中运行，则取消调度并返回，而不执行当前线程中排队但尚未执行的下一个任务，如果该任务可用；
     * 或者如果不可用，则返回是由其他线程分叉的任务，如果可供使用的话。
     * 可用性可能是暂时的，因此{@code null}结果不一定表示该任务在其中运行的池处于静止状态。
     * 此方法主要是为了支持扩展而设计的，否则不太可能有用。
     *
     * @return a task, or {@code null} if none are available
     */
    protected static juc.ForkJoinTask<?> pollTask() {
        Thread t;
        ForkJoinWorkerThread wt;
        return ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
                (wt = (ForkJoinWorkerThread) t).pool.nextTaskFor(wt.workQueue) :
                null;
    }

    // tag operations 标签操作

    /**
     * Returns the tag for this task.
     * 返回此任务的标签。
     *
     * @return the tag for this task
     * @since 1.8
     */
    public final short getForkJoinTaskTag() {
        return (short) status;
    }

    /**
     * Atomically sets the tag value for this task.
     * 以原子方式设置此任务的标签值
     *
     * @param tag the tag value
     * @return the previous value of the tag
     * @since 1.8
     */
    public final short setForkJoinTaskTag(short tag) {
        for (int s; ; ) {
            if (U.compareAndSwapInt(this, STATUS, s = status,
                    (s & ~SMASK) | (tag & SMASK)))
                //s & ~SMASK,将tag相关位置为0，其他位不变；tag & SMASK，将tag相关位设为tag值。
                return (short) s;
        }
    }

    /**
     * Atomically conditionally sets the tag value for this task.
     * Among other applications, tags can be used as visit markers
     * in tasks operating on graphs, as in methods that check: {@code
     * if (task.compareAndSetForkJoinTaskTag((short)0, (short)1))}
     * before processing, otherwise exiting because the node has
     * already been visited.
     * 以原子方式有条件地为此任务设置标签值。
     * 在其他应用程序中，标记可以用作对图形进行操作的任务中的访问标记，正如在检查方法中所作的：
     * 在处理之前{@code if (task.compareAndSetForkJoinTaskTag((short)0, (short)1))}成功，否则会退出，因为该节点已经被访问过。
     *
     * @param e   the expected tag value
     * @param tag the new tag value
     * @return {@code true} if successful; i.e., the current value was
     * equal to e and is now tag.
     * @since 1.8
     */
    public final boolean compareAndSetForkJoinTaskTag(short e, short tag) {
        for (int s; ; ) {
            if ((short) (s = status) != e)
                return false;
            if (U.compareAndSwapInt(this, STATUS, s,
                    (s & ~SMASK) | (tag & SMASK)))
                return true;
        }
    }

    /**
     * Adaptor for Runnables. This implements RunnableFuture
     * to be compliant with AbstractExecutorService constraints
     * when used in ForkJoinPool.
     * 可运行对象的适配器。
     * 当在ForkJoinPool中使用时，为了与AbstractExecutorService约束兼容，该适配器实现了RunnableFuture。
     */
    static final class AdaptedRunnable<T> extends juc.ForkJoinTask<T>
            implements RunnableFuture<T> {
        final Runnable runnable;
        T result;

        AdaptedRunnable(Runnable runnable, T result) {
            if (runnable == null) throw new NullPointerException();
            this.runnable = runnable;
            this.result = result; // OK to set this even before completion
        }

        public final T getRawResult() {
            return result;
        }

        public final void setRawResult(T v) {
            result = v;
        }

        public final boolean exec() {
            runnable.run();
            return true;
        }

        public final void run() {
            invoke();
        }

        private static final long serialVersionUID = 5232453952276885070L;
    }

    /**
     * Adaptor for Runnables without results
     * 不带结果的Runnable适配器
     */
    static final class AdaptedRunnableAction extends juc.ForkJoinTask<Void>
            implements RunnableFuture<Void> {
        final Runnable runnable;

        AdaptedRunnableAction(Runnable runnable) {
            if (runnable == null) throw new NullPointerException();
            this.runnable = runnable;
        }

        public final Void getRawResult() {
            return null;
        }

        public final void setRawResult(Void v) {
        }

        public final boolean exec() {
            runnable.run();
            return true;
        }

        public final void run() {
            invoke();
        }

        private static final long serialVersionUID = 5232453952276885070L;
    }

    /**
     * Adaptor for Runnables in which failure forces worker exception
     * 适用于Runnable的适配器，其中失败导致工作线程异常
     */
    static final class RunnableExecuteAction extends juc.ForkJoinTask<Void> {
        final Runnable runnable;

        RunnableExecuteAction(Runnable runnable) {
            if (runnable == null) throw new NullPointerException();
            this.runnable = runnable;
        }

        public final Void getRawResult() {
            return null;
        }

        public final void setRawResult(Void v) {
        }

        public final boolean exec() {
            runnable.run();
            return true;
        }

        void internalPropagateException(Throwable ex) {
            rethrow(ex); // rethrow outside exec() catches.
        }

        private static final long serialVersionUID = 5232453952276885070L;
    }

    /**
     * Adaptor for Callables
     * Callable适配器
     */
    static final class AdaptedCallable<T> extends juc.ForkJoinTask<T>
            implements RunnableFuture<T> {
        final Callable<? extends T> callable;
        T result;

        AdaptedCallable(Callable<? extends T> callable) {
            if (callable == null) throw new NullPointerException();
            this.callable = callable;
        }

        public final T getRawResult() {
            return result;
        }

        public final void setRawResult(T v) {
            result = v;
        }

        public final boolean exec() {
            try {
                result = callable.call();
                return true;
            } catch (Error err) {
                throw err;
            } catch (RuntimeException rex) {
                throw rex;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        public final void run() {
            invoke();
        }

        private static final long serialVersionUID = 2838392045355241008L;
    }

    /**
     * Returns a new {@code ForkJoinTask} that performs the {@code run}
     * method of the given {@code Runnable} as its action, and returns
     * a null result upon {@link #join}.
     *
     * @param runnable the runnable action
     * @return the task
     */
    public static juc.ForkJoinTask<?> adapt(Runnable runnable) {
        return new AdaptedRunnableAction(runnable);
    }

    /**
     * Returns a new {@code ForkJoinTask} that performs the {@code run}
     * method of the given {@code Runnable} as its action, and returns
     * the given result upon {@link #join}.
     * 返回一个新的{@code ForkJoinTask}，它执行给定{@code Runnable}的{@code run}方法作为其动作，
     * 并在{@link #join}上返回给定结果。
     *
     * @param runnable the runnable action
     * @param result   the result upon completion
     * @param <T>      the type of the result
     * @return the task
     */
    public static <T> juc.ForkJoinTask<T> adapt(Runnable runnable, T result) {
        return new AdaptedRunnable<T>(runnable, result);
    }

    /**
     * Returns a new {@code ForkJoinTask} that performs the {@code call}
     * method of the given {@code Callable} as its action, and returns
     * its result upon {@link #join}, translating any checked exceptions
     * encountered into {@code RuntimeException}.
     * 返回一个新的{@code ForkJoinTask}，该ForkJoinTask执行给定{@code Callable}的{@code call}方法作为其操作，
     * 并在{@link #join}上返回其结果，将遇到的所有检查到的异常转换为{@code RuntimeException}。
     *
     * @param callable the callable action
     * @param <T>      the type of the callable's result
     * @return the task
     */
    public static <T> juc.ForkJoinTask<T> adapt(Callable<? extends T> callable) {
        return new AdaptedCallable<T>(callable);
    }

    // Serialization support

    private static final long serialVersionUID = -7721805057305804111L;

    /**
     * Saves this task to a stream (that is, serializes it).
     * 将此任务保存到流（即序列化）。
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     * @serialData the current run status and the exception thrown
     * during execution, or {@code null} if none
     */
    private void writeObject(java.io.ObjectOutputStream s)
            throws java.io.IOException {
        s.defaultWriteObject();
        s.writeObject(getException());
    }

    /**
     * Reconstitutes this task from a stream (that is, deserializes it).
     * 从流中重构此任务（即反序列化它）。
     *
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *                                could not be found
     * @throws java.io.IOException    if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        Object ex = s.readObject();
        if (ex != null)
            setExceptionalCompletion((Throwable) ex);
    }

    // Unsafe mechanics
    private static final Unsafe U;
    private static final long STATUS;

    static {
        exceptionTableLock = new ReentrantLock();
        exceptionTableRefQueue = new ReferenceQueue<Object>();
        exceptionTable = new ExceptionNode[EXCEPTION_MAP_CAPACITY];
        try {
            U = GetUnsafeFromReflect.getUnsafe();
            Class<?> k = juc.ForkJoinTask.class;
            STATUS = U.objectFieldOffset
                    (k.getDeclaredField("status"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
