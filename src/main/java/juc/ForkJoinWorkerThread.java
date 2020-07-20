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

import java.security.AccessControlContext;
import java.security.ProtectionDomain;

/**
 * A thread managed by a {@link ForkJoinPool}, which executes
 * {@link ForkJoinTask}s.
 * This class is subclassable solely for the sake of adding
 * functionality -- there are no overridable methods dealing with
 * scheduling or execution.  However, you can override initialization
 * and termination methods surrounding the main task processing loop.
 * If you do create such a subclass, you will also need to supply a
 * custom {@link ForkJoinPool.ForkJoinWorkerThreadFactory} to
 * {@linkplain ForkJoinPool#ForkJoinPool use it} in a {@code ForkJoinPool}.
 * 由ForkJoinPool管理的线程，该线程执行ForkJoinTasks。
 * 此类仅可为增加功能而被子类化——没有用于调度或执行的可重写方法。
 * 但是，你可以覆盖主任务处理循环周围的初始化和终止方法。
 * 如果确实创建了这样的子类，则还需要提供一个自定义的ForkJoinPool.ForkJoinWorkerThreadFactory以便在ForkJoinPool中使用它。
 *
 * @author Doug Lea
 * @since 1.7
 */
public class ForkJoinWorkerThread extends Thread {
    /*
     * ForkJoinWorkerThreads are managed by ForkJoinPools and perform
     * ForkJoinTasks. For explanation, see the internal documentation
     * of class ForkJoinPool.
     * ForkJoinWorkerThreads由ForkJoinPools管理并执行ForkJoinTasks。 有关说明，请参阅类ForkJoinPool的内部文档。 
     *
     * This class just maintains links to its pool and WorkQueue.  The
     * pool field is set immediately upon construction, but the
     * workQueue field is not set until a call to registerWorker
     * completes. This leads to a visibility race, that is tolerated
     * by requiring that the workQueue field is only accessed by the
     * owning thread.
     * 此类仅维护指向其池和WorkQueue的链接。
     * 构造时会立即设置pool字段，但是直到对registerWorker的调用完成后才设置workQueue字段。
     * 这导致可见性竞争，可以通过要求workQueue字段仅由拥有线程访问来忍受。
     *
     * Support for (non-public) subclass InnocuousForkJoinWorkerThread
     * requires that we break quite a lot of encapsulation (via Unsafe)
     * both here and in the subclass to access and set Thread fields.
     *  对（非公共）子类InnocuousForkJoinWorkerThread的支持要求我们在此处和子类中破坏很多封装（通过Unsafe）以访问和设置Thread字段。
     */

    final ForkJoinPool pool;                // the pool this thread works in
    final ForkJoinPool.WorkQueue workQueue; // work-stealing mechanics

    /**
     * Creates a ForkJoinWorkerThread operating in the given pool.
     *
     * @param pool the pool this thread works in
     * @throws NullPointerException if pool is null
     */
    protected ForkJoinWorkerThread(ForkJoinPool pool) {
        // Use a placeholder until a useful name can be set in registerWorker
        super("aForkJoinWorkerThread");
        this.pool = pool;
        this.workQueue = pool.registerWorker(this);
    }

    /**
     * Version for InnocuousForkJoinWorkerThread
     */
    ForkJoinWorkerThread(ForkJoinPool pool, ThreadGroup threadGroup,
                         AccessControlContext acc) {
        super(threadGroup, null, "aForkJoinWorkerThread");
        U.putOrderedObject(this, INHERITEDACCESSCONTROLCONTEXT, acc);
        eraseThreadLocals(); // clear before registering
        this.pool = pool;
        this.workQueue = pool.registerWorker(this);
    }

    /**
     * Returns the pool hosting this thread.
     *
     * @return the pool
     */
    public ForkJoinPool getPool() {
        return pool;
    }

    /**
     * Returns the unique index number of this thread in its pool.
     * The returned value ranges from zero to the maximum number of
     * threads (minus one) that may exist in the pool, and does not
     * change during the lifetime of the thread.  This method may be
     * useful for applications that track status or collect results
     * per-worker-thread rather than per-task.
     * 返回该线程在其池中的唯一索引号。
     * 返回值的范围是从零到池中可能存在的最大线程数（减一），并且在线程的生存期内不会更改。
     * 此方法对于跟踪状态或按每工作线程（per-worker-thread）而不是按每任务（ per-task）收集结果的应用程序可能有用。
     *
     * @return the index number
     */
    public int getPoolIndex() {
        return workQueue.getPoolIndex();
    }

    /**
     * Initializes internal state after construction but before
     * processing any tasks. If you override this method, you must
     * invoke {@code super.onStart()} at the beginning of the method.
     * Initialization requires care: Most fields must have legal
     * default values, to ensure that attempted accesses from other
     * threads work correctly even before this thread starts
     * processing tasks.
     * 在构造之后但在处理任何任务之前初始化内部状态。
     * 如果覆盖此方法，则必须在该方法的开头调用super.onStart（）。
     * 初始化需要小心：大多数字段必须具有合法的默认值，以确保即使在此线程开始处理任务之前，
     * 尝试从其他线程进行的访问也可以正常工作。
     */
    protected void onStart() {
    }

    /**
     * Performs cleanup associated with termination of this worker
     * thread.  If you override this method, you must invoke
     * {@code super.onTermination} at the end of the overridden method.
     * 执行与此工作线程终止相关的清理。
     * 如果重写此方法，则必须在重写方法的末尾调用super.onTermination。
     *
     * @param exception the exception causing this thread to abort due
     *                  to an unrecoverable error, or {@code null} if completed normally
     */
    protected void onTermination(Throwable exception) {
    }

    /**
     * This method is required to be public, but should never be
     * called explicitly. It performs the main run loop to execute
     * {@link ForkJoinTask}s.
     */
    public void run() {
        if (workQueue.array == null) { // only run once
            Throwable exception = null;
            try {
                onStart();
                pool.runWorker(workQueue);
            } catch (Throwable ex) {
                exception = ex;
            } finally {
                try {
                    onTermination(exception);
                } catch (Throwable ex) {
                    if (exception == null)
                        exception = ex;
                } finally {
                    pool.deregisterWorker(this, exception);
                }
            }
        }
    }

    /**
     * Erases ThreadLocals by nulling out Thread maps.
     */
    final void eraseThreadLocals() {
        U.putObject(this, THREADLOCALS, null);
        U.putObject(this, INHERITABLETHREADLOCALS, null);
    }

    /**
     * Non-public hook method for InnocuousForkJoinWorkerThread
     */
    void afterTopLevelExec() {
    }

    // Set up to allow setting thread fields in constructor
    private static final sun.misc.Unsafe U;
    private static final long THREADLOCALS;
    private static final long INHERITABLETHREADLOCALS;
    private static final long INHERITEDACCESSCONTROLCONTEXT;

    static {
        try {
            U = sun.misc.Unsafe.getUnsafe();
            Class<?> tk = Thread.class;
            THREADLOCALS = U.objectFieldOffset
                    (tk.getDeclaredField("threadLocals"));
            INHERITABLETHREADLOCALS = U.objectFieldOffset
                    (tk.getDeclaredField("inheritableThreadLocals"));
            INHERITEDACCESSCONTROLCONTEXT = U.objectFieldOffset
                    (tk.getDeclaredField("inheritedAccessControlContext"));

        } catch (Exception e) {
            throw new Error(e);
        }
    }

    /**
     * A worker thread that has no permissions, is not a member of any
     * user-defined ThreadGroup, and erases all ThreadLocals after
     * running each top-level task.
     * 没有权限的工作线程，不是任何用户定义的ThreadGroup的成员，
     * 并且在运行每个顶级任务后会擦除所有ThreadLocals。
     */
    static final class InnocuousForkJoinWorkerThread extends juc.ForkJoinWorkerThread {
        /**
         * The ThreadGroup for all InnocuousForkJoinWorkerThreads
         */
        private static final ThreadGroup innocuousThreadGroup =
                createThreadGroup();

        /**
         * An AccessControlContext supporting no privileges
         */
        private static final AccessControlContext INNOCUOUS_ACC =
                new AccessControlContext(
                        new ProtectionDomain[]{
                                new ProtectionDomain(null, null)
                        });

        InnocuousForkJoinWorkerThread(ForkJoinPool pool) {
            super(pool, innocuousThreadGroup, INNOCUOUS_ACC);
        }

        @Override
            // to erase ThreadLocals
        void afterTopLevelExec() {
            eraseThreadLocals();
        }

        @Override // to always report system loader
        public ClassLoader getContextClassLoader() {
            return ClassLoader.getSystemClassLoader();
        }

        @Override // to silently fail
        public void setUncaughtExceptionHandler(UncaughtExceptionHandler x) {
        }

        @Override // paranoically
        public void setContextClassLoader(ClassLoader cl) {
            throw new SecurityException("setContextClassLoader");
        }

        /**
         * Returns a new group with the system ThreadGroup (the
         * topmost, parent-less group) as parent.  Uses Unsafe to
         * traverse Thread.group and ThreadGroup.parent fields.
         * 返回以系统ThreadGroup（最顶层的无父组）为父组的新组。
         * 使用Unsafe遍历Thread.group和ThreadGroup.parent字段。
         */
        private static ThreadGroup createThreadGroup() {
            try {
                sun.misc.Unsafe u = sun.misc.Unsafe.getUnsafe();
                Class<?> tk = Thread.class;
                Class<?> gk = ThreadGroup.class;
                long tg = u.objectFieldOffset(tk.getDeclaredField("group"));
                long gp = u.objectFieldOffset(gk.getDeclaredField("parent"));
                ThreadGroup group = (ThreadGroup)
                        u.getObject(Thread.currentThread(), tg);
                while (group != null) {
                    ThreadGroup parent = (ThreadGroup) u.getObject(group, gp);
                    if (parent == null)
                        return new ThreadGroup(group,
                                "InnocuousForkJoinWorkerThreadGroup");
                    group = parent;
                }
            } catch (Exception e) {
                throw new Error(e);
            }
            // fall through if null as cannot-happen safeguard
            throw new Error("Cannot create ThreadGroup");
        }
    }

}
