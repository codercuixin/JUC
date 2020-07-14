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
//todo 内部实现
import juc.locks.LockSupport;

import java.util.function.*;
/**
 *一个Future，可以是明确完成的（设置其值和状态），并可以用作CompletionStage，还支持在该Future完成时触发的依赖的功能和操作。
 *
 * 当两个或多个线程尝试complete，completeExceptionally或cancel 一个CompletableFuture时，只有其中一个线程成功。
 *
 * 除了直接操作状态和结果的这些方法和相关方法之外，CompletableFuture还使用以下策略实现接口CompletionStage：
 *       1.为非异步方法的相关完成方法提供的动作可以由完成当前CompletableFuture的线程执行，也可以由完成方法的任何其他调用者执行。
 *       2.所有没有显式Executor参数的异步方法都是使用ForkJoinPool.commonPool（）执行的（除非它不支持至少为2的并行度，
 *       在这种情况下，将创建一个新的Thread来运行每个任务）。
 *       为了简化监视，调试和跟踪，所有生成的异步任务都是标记接口CompletableFuture.AsynchronousCompletionTask的实例。
 *      3.所有CompletionStage方法都是独立于其他公共方法实现的，因此一个方法的行为不受子类中其他方法的覆盖影响。
 *
 * CompletableFuture还使用以下策略实现接口Future：
 *     1.由于（与FutureTask不同）此类无法直接控制导致其完成的计算，因此取消cancellation 被视为异常完成的另一种形式。
 *     方法cancel与completeExceptionally（new CancellationException（)）具有相同的效果。
 *     方法isCompletedExceptionally（）可用于确定CompletableFuture是否以任何异常方式完成。
 *      2.如果使用CompletionException异常完成，则get（）和get（long，TimeUnit）方法会抛出ExecutionException，
 *      其原因与相应的CompletionException中保存的原因相同。
 *      为了简化大多数上下文的使用，此类还定义了方法join（）和getNow（T），这些方法在这些情况下直接抛出CompletionException。
 *
 * @author Doug Lea
 * @since 1.8
 */
public class CompletableFuture<T> implements Future<T>, CompletionStage<T> {

    /*
     * 概览:
     *
     * CompletableFuture可能具有从属的完成操作，这些操作收集在链接栈中。
     * 它通过CAS一个result字段来自动完成，然后弹出并运行这些操作。
     * 这适用于正常与异常结果，同步与异步操作，二进制触发器以及各种形式的完成操作。
     *
     * 字段result为非空（通过CAS设置）表示已完成done。 AltResult用于将结果装箱为空，并用于保存异常。
     * 使用单个字段使完成易于检测和触发。
     * 编码和解码很简单，但是却增加了将异常与目标联系起来的麻烦。
     * 较小的简化依赖于（静态）NIL（将空结果装箱）是唯一具有空异常字段的AltResult，因此我们通常不需要显式比较。
     * 即使某些泛型转换未检查（请参见SuppressWarnings注解），它们也被放置为适当，即使检查了。
     *
     * 从属动作由完成对象表示，这些完成对象是Treiber堆栈，该堆栈以字段“stack”为头节点。
     * 每种操作都有Completion 类，分为单输入（UniCompletion），两输入（BiCompletion），投影（BiCompletions使用两个输入中的一个（不是两个）），共享（CoCompletion，被两个输入中的第二个使用）的完成类源），零输入源操作和unblock waiters的Signallers。
     * Completion 类扩展了ForkJoinTask以启用异步执行（不增加空间开销，因为我们利用其“tag”方法来维护声明）。
     * 它也被声明为Runnable以允许与任意Executor一起使用。
     *
     * 对每种CompletionStage的支持都依赖于一个单独的类以及两个CompletableFuture方法：
     *
     *     1.一个名称X对应于函数的Completion类，其前缀为“ Uni”，“ Bi”或“ Or”。
     * 每个类都包含源，操作和依赖项的字段。它们无聊地相似，仅在基本功能形式方面不同。我们这样做是为了使用户在常见用法中不会遇到适配器层。
     * 我们还包括与用户方法不对应的“Relay”类/方法。他们将结果从一个阶段复制到另一个阶段。
     *     2.布尔值CompletableFuture方法x（...）（例如uniApply）获取所需的所有参数来检查一个动作是否可触发的，然后运行该动作或通过执行其Completion参数（如果存在）来安排其异步执行。
     * 如果已知complete，则该方法返回true。
     *
     *     3.完成方法tryFire（int mode）会调用与其保留参数相关联的x方法，并在成功时清除。
     * 参数mode允许tryFire被调用两次（先是SYNC，然后是ASYNC）。
     * 第一次在安排执行时筛选和捕获异常，第二次在任务被调用时。 （一些类未被异步使用，因此形式略有不同。）
     * 如果另一个线程已要求了某个函数调用，则claim() 回调则会抑制该函数调用。
     *
     *     4.从CompletableFuture x的公共阶段方法调用CompletableFuture方法xStage（...）。
     * 它筛选用户参数并调用和/或创建stage对象。
     * 如果不是异步的并且x已经完成，则操作将立即运行。
     * 否则，将创建Completion c，将其压入x的堆栈（除非完成），然后通过c.tryFire启动或触发。
     * 如果x在入栈时完成，则这也包括可能的竞争。具有两个输入的类（例如BiApply）在推动动作时处理两者之间的竞争。第二个完成是指向第一个完成的CoCompletion，以便最多由一个执行该操作。多种方法的全部
     * 具有两个输入的类（例如BiApply）在入栈（pushing)动作时处理两者之间的竞争。
     * 第二个Completion 是指向第一个完成的CoCompletion，以便最多由一个执行该操作。
     * 多元方法allOf和anyOf进行此配对以形成Completion 树。
     *
     *
     * 请注意，方法的通用类型参数根据“ this”是源，依赖的还是completion而有所不同。
     *
     * 除非保证目标是不可观察的（即尚未返回或链接），否则将在完成时调用postComplete方法。
     * 多个线程可以调用postComplete，这样会原子自动弹出每个依赖动作，并尝试在NESTED模式下通过tryFire方法触发它。
     * 触发可以递归传播，因此NESTED模式返回其完成的依赖项（如果存在），以供其调用方进行进一步处理（请参见方法postFire）。
     *
     * 阻塞方法get（）和join（）依赖于唤醒等待线程的Signaller Completions。其机制类似于FutureTask，Phaser和SynchronousQueue中使用的Treiber堆栈等待节点。
     * 有关算法的详细信息，请参见其内部文档。
     *
     * 如果没有预防措施，随着Completions链的建立，CompletableFutures将可能产生垃圾堆积，每条指向其源头。
     * 因此，我们会尽快使字段无效（尤其是方法Completion.detach）。
     *
     * 无论如何都需要进行的筛选检查，无害地忽略了空参数，该空参数可能在争用多个线程使字段无效的期间被获取。
     * 我们还尝试从可能永远不会弹出的堆栈中取消已触发的Completions链接（请参见方法postFire）。
     * Completion 字段不必声明为final或volatile，因为只有安全发布时其他publication线程才能看到它们
     *
     */

    volatile Object result;       // Either the result or boxed AltResult
    volatile Completion stack;    // Top of Treiber stack of dependent actions

    final boolean internalComplete(Object r) { // CAS from null to r
        return UNSAFE.compareAndSwapObject(this, RESULT, null, r);
    }

    final boolean casStack(Completion cmp, Completion val) {
        return UNSAFE.compareAndSwapObject(this, STACK, cmp, val);
    }

    /**
     * Returns true if successfully pushed c onto stack.
     */
    final boolean tryPushStack(Completion c) {
        Completion h = stack;
        lazySetNext(c, h);
        return UNSAFE.compareAndSwapObject(this, STACK, h, c);
    }

    /**
     * Unconditionally pushes c onto stack, retrying if necessary.
     */
    final void pushStack(Completion c) {
        do {
        } while (!tryPushStack(c));
    }

    /* ------------- Encoding and decoding outcomes -------------- */

    static final class AltResult { // See above
        final Throwable ex;        // null only for NIL

        AltResult(Throwable x) {
            this.ex = x;
        }
    }

    /**
     * The encoding of the null value.
     */
    static final AltResult NIL = new AltResult(null);

    /**
     * Completes with the null value, unless already completed.
     */
    final boolean completeNull() {
        return UNSAFE.compareAndSwapObject(this, RESULT, null,
                NIL);
    }

    /**
     * Returns the encoding of the given non-exceptional value.
     */
    final Object encodeValue(T t) {
        return (t == null) ? NIL : t;
    }

    /**
     * Completes with a non-exceptional result, unless already completed.
     */
    final boolean completeValue(T t) {
        return UNSAFE.compareAndSwapObject(this, RESULT, null,
                (t == null) ? NIL : t);
    }

    /**
     * Returns the encoding of the given (non-null) exception as a
     * wrapped CompletionException unless it is one already.
     */
    static AltResult encodeThrowable(Throwable x) {
        return new AltResult((x instanceof CompletionException) ? x :
                new CompletionException(x));
    }

    /**
     * Completes with an exceptional result, unless already completed.
     */
    final boolean completeThrowable(Throwable x) {
        return UNSAFE.compareAndSwapObject(this, RESULT, null,
                encodeThrowable(x));
    }

    /**
     * Returns the encoding of the given (non-null) exception as a
     * wrapped CompletionException unless it is one already.  May
     * return the given Object r (which must have been the result of a
     * source future) if it is equivalent, i.e. if this is a simple
     * relay of an existing CompletionException.
     */
    static Object encodeThrowable(Throwable x, Object r) {
        if (!(x instanceof CompletionException))
            x = new CompletionException(x);
        else if (r instanceof AltResult && x == ((AltResult) r).ex)
            return r;
        return new AltResult(x);
    }

    /**
     * Completes with the given (non-null) exceptional result as a
     * wrapped CompletionException unless it is one already, unless
     * already completed.  May complete with the given Object r
     * (which must have been the result of a source future) if it is
     * equivalent, i.e. if this is a simple propagation of an
     * existing CompletionException.
     */
    final boolean completeThrowable(Throwable x, Object r) {
        return UNSAFE.compareAndSwapObject(this, RESULT, null,
                encodeThrowable(x, r));
    }

    /**
     * Returns the encoding of the given arguments: if the exception
     * is non-null, encodes as AltResult.  Otherwise uses the given
     * value, boxed as NIL if null.
     */
    Object encodeOutcome(T t, Throwable x) {
        return (x == null) ? (t == null) ? NIL : t : encodeThrowable(x);
    }

    /**
     * Returns the encoding of a copied outcome; if exceptional,
     * rewraps as a CompletionException, else returns argument.
     */
    static Object encodeRelay(Object r) {
        Throwable x;
        return (((r instanceof AltResult) &&
                (x = ((AltResult) r).ex) != null &&
                !(x instanceof CompletionException)) ?
                new AltResult(new CompletionException(x)) : r);
    }

    /**
     * Completes with r or a copy of r, unless already completed.
     * If exceptional, r is first coerced to a CompletionException.
     */
    final boolean completeRelay(Object r) {
        return UNSAFE.compareAndSwapObject(this, RESULT, null,
                encodeRelay(r));
    }

    /**
     * Reports result using Future.get conventions.
     */
    private static <T> T reportGet(Object r)
            throws InterruptedException, ExecutionException {
        if (r == null) // by convention below, null means interrupted
            throw new InterruptedException();
        if (r instanceof AltResult) {
            Throwable x, cause;
            if ((x = ((AltResult) r).ex) == null)
                return null;
            if (x instanceof CancellationException)
                throw (CancellationException) x;
            if ((x instanceof CompletionException) &&
                    (cause = x.getCause()) != null)
                x = cause;
            throw new ExecutionException(x);
        }
        @SuppressWarnings("unchecked") T t = (T) r;
        return t;
    }

    /**
     * Decodes outcome to return result or throw unchecked exception.
     */
    private static <T> T reportJoin(Object r) {
        if (r instanceof AltResult) {
            Throwable x;
            if ((x = ((AltResult) r).ex) == null)
                return null;
            if (x instanceof CancellationException)
                throw (CancellationException) x;
            if (x instanceof CompletionException)
                throw (CompletionException) x;
            throw new CompletionException(x);
        }
        @SuppressWarnings("unchecked") T t = (T) r;
        return t;
    }

    /* ------------- Async task preliminaries -------------- */

    /**
     * A marker interface identifying asynchronous tasks produced by
     * {@code async} methods. This may be useful for monitoring,
     * debugging, and tracking asynchronous activities.
     *
     * @since 1.8
     */
    public interface AsynchronousCompletionTask {
    }

    private static final boolean useCommonPool =
            (ForkJoinPool.getCommonPoolParallelism() > 1);

    /**
     * Default executor -- ForkJoinPool.commonPool() unless it cannot
     * support parallelism.
     */
    private static final Executor asyncPool = useCommonPool ?
            ForkJoinPool.commonPool() : new ThreadPerTaskExecutor();

    /**
     * Fallback if ForkJoinPool.commonPool() cannot support parallelism
     */
    static final class ThreadPerTaskExecutor implements Executor {
        public void execute(Runnable r) {
            new Thread(r).start();
        }
    }

    /**
     * Null-checks user executor argument, and translates uses of
     * commonPool to asyncPool in case parallelism disabled.
     */
    static Executor screenExecutor(Executor e) {
        if (!useCommonPool && e == ForkJoinPool.commonPool())
            return asyncPool;
        if (e == null) throw new NullPointerException();
        return e;
    }

    // Modes for Completion.tryFire. Signedness matters.
    static final int SYNC = 0;
    static final int ASYNC = 1;
    static final int NESTED = -1;

    /* ------------- Base Completion classes and operations -------------- */

    @SuppressWarnings("serial")
    abstract static class Completion extends ForkJoinTask<Void>
            implements Runnable, AsynchronousCompletionTask {
        volatile Completion next;      // Treiber stack link

        /**
         * Performs completion action if triggered, returning a
         * dependent that may need propagation, if one exists.
         *
         * @param mode SYNC, ASYNC, or NESTED
         */
        abstract juc.CompletableFuture<?> tryFire(int mode);

        /**
         * Returns true if possibly still triggerable. Used by cleanStack.
         */
        abstract boolean isLive();

        public final void run() {
            tryFire(ASYNC);
        }

        public final boolean exec() {
            tryFire(ASYNC);
            return true;
        }

        public final Void getRawResult() {
            return null;
        }

        public final void setRawResult(Void v) {
        }
    }

    static void lazySetNext(Completion c, Completion next) {
        UNSAFE.putOrderedObject(c, NEXT, next);
    }

    /**
     * Pops and tries to trigger all reachable dependents.  Call only
     * when known to be done.
     */
    final void postComplete() {
        /*
         * On each step, variable f holds current dependents to pop
         * and run.  It is extended along only one path at a time,
         * pushing others to avoid unbounded recursion.
         */
        juc.CompletableFuture<?> f = this;
        Completion h;
        while ((h = f.stack) != null ||
                (f != this && (h = (f = this).stack) != null)) {
            juc.CompletableFuture<?> d;
            Completion t;
            if (f.casStack(h, t = h.next)) {
                if (t != null) {
                    if (f != this) {
                        pushStack(h);
                        continue;
                    }
                    h.next = null;    // detach
                }
                f = (d = h.tryFire(NESTED)) == null ? this : d;
            }
        }
    }

    /**
     * Traverses stack and unlinks dead Completions.
     */
    final void cleanStack() {
        for (Completion p = null, q = stack; q != null; ) {
            Completion s = q.next;
            if (q.isLive()) {
                p = q;
                q = s;
            } else if (p == null) {
                casStack(q, s);
                q = stack;
            } else {
                p.next = s;
                if (p.isLive())
                    q = s;
                else {
                    p = null;  // restart
                    q = stack;
                }
            }
        }
    }

    /* ------------- One-input Completions -------------- */

    /**
     * A Completion with a source, dependent, and executor.
     */
    @SuppressWarnings("serial")
    abstract static class UniCompletion<T, V> extends Completion {
        Executor executor;                 // executor to use (null if none)
        juc.CompletableFuture<V> dep;          // the dependent to complete
        juc.CompletableFuture<T> src;          // source for action

        UniCompletion(Executor executor, juc.CompletableFuture<V> dep,
                      juc.CompletableFuture<T> src) {
            this.executor = executor;
            this.dep = dep;
            this.src = src;
        }

        /**
         * Returns true if action can be run. Call only when known to
         * be triggerable. Uses FJ tag bit to ensure that only one
         * thread claims ownership.  If async, starts as task -- a
         * later call to tryFire will run action.
         */
        final boolean claim() {
            Executor e = executor;
            if (compareAndSetForkJoinTaskTag((short) 0, (short) 1)) {
                if (e == null)
                    return true;
                executor = null; // disable
                e.execute(this);
            }
            return false;
        }

        final boolean isLive() {
            return dep != null;
        }
    }

    /**
     * Pushes the given completion (if it exists) unless done.
     */
    final void push(UniCompletion<?, ?> c) {
        if (c != null) {
            while (result == null && !tryPushStack(c))
                lazySetNext(c, null); // clear on failure
        }
    }

    /**
     * Post-processing by dependent after successful UniCompletion
     * tryFire.  Tries to clean stack of source a, and then either runs
     * postComplete or returns this to caller, depending on mode.
     */
    final juc.CompletableFuture<T> postFire(juc.CompletableFuture<?> a, int mode) {
        if (a != null && a.stack != null) {
            if (mode < 0 || a.result == null)
                a.cleanStack();
            else
                a.postComplete();
        }
        if (result != null && stack != null) {
            if (mode < 0)
                return this;
            else
                postComplete();
        }
        return null;
    }

    @SuppressWarnings("serial")
    static final class UniApply<T, V> extends UniCompletion<T, V> {
        Function<? super T, ? extends V> fn;

        UniApply(Executor executor, juc.CompletableFuture<V> dep,
                 juc.CompletableFuture<T> src,
                 Function<? super T, ? extends V> fn) {
            super(executor, dep, src);
            this.fn = fn;
        }

        final juc.CompletableFuture<V> tryFire(int mode) {
            juc.CompletableFuture<V> d;
            juc.CompletableFuture<T> a;
            if ((d = dep) == null ||
                    !d.uniApply(a = src, fn, mode > 0 ? null : this))
                return null;
            dep = null;
            src = null;
            fn = null;
            return d.postFire(a, mode);
        }
    }

    final <S> boolean uniApply(juc.CompletableFuture<S> a,
                               Function<? super S, ? extends T> f,
                               UniApply<S, T> c) {
        Object r;
        Throwable x;
        if (a == null || (r = a.result) == null || f == null)
            return false;
        tryComplete:
        if (result == null) {
            if (r instanceof AltResult) {
                if ((x = ((AltResult) r).ex) != null) {
                    completeThrowable(x, r);
                    break tryComplete;
                }
                r = null;
            }
            try {
                if (c != null && !c.claim())
                    return false;
                @SuppressWarnings("unchecked") S s = (S) r;
                completeValue(f.apply(s));
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    private <V> juc.CompletableFuture<V> uniApplyStage(
            Executor e, Function<? super T, ? extends V> f) {
        if (f == null) throw new NullPointerException();
        juc.CompletableFuture<V> d = new juc.CompletableFuture<V>();
        if (e != null || !d.uniApply(this, f, null)) {
            UniApply<T, V> c = new UniApply<T, V>(e, d, this, f);
            push(c);
            c.tryFire(SYNC);
        }
        return d;
    }

    @SuppressWarnings("serial")
    static final class UniAccept<T> extends UniCompletion<T, Void> {
        Consumer<? super T> fn;

        UniAccept(Executor executor, juc.CompletableFuture<Void> dep,
                  juc.CompletableFuture<T> src, Consumer<? super T> fn) {
            super(executor, dep, src);
            this.fn = fn;
        }

        final juc.CompletableFuture<Void> tryFire(int mode) {
            juc.CompletableFuture<Void> d;
            juc.CompletableFuture<T> a;
            if ((d = dep) == null ||
                    !d.uniAccept(a = src, fn, mode > 0 ? null : this))
                return null;
            dep = null;
            src = null;
            fn = null;
            return d.postFire(a, mode);
        }
    }

    final <S> boolean uniAccept(juc.CompletableFuture<S> a,
                                Consumer<? super S> f, UniAccept<S> c) {
        Object r;
        Throwable x;
        if (a == null || (r = a.result) == null || f == null)
            return false;
        tryComplete:
        if (result == null) {
            if (r instanceof AltResult) {
                if ((x = ((AltResult) r).ex) != null) {
                    completeThrowable(x, r);
                    break tryComplete;
                }
                r = null;
            }
            try {
                if (c != null && !c.claim())
                    return false;
                @SuppressWarnings("unchecked") S s = (S) r;
                f.accept(s);
                completeNull();
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    private juc.CompletableFuture<Void> uniAcceptStage(Executor e,
                                                       Consumer<? super T> f) {
        if (f == null) throw new NullPointerException();
        juc.CompletableFuture<Void> d = new juc.CompletableFuture<Void>();
        if (e != null || !d.uniAccept(this, f, null)) {
            UniAccept<T> c = new UniAccept<T>(e, d, this, f);
            push(c);
            c.tryFire(SYNC);
        }
        return d;
    }

    @SuppressWarnings("serial")
    static final class UniRun<T> extends UniCompletion<T, Void> {
        Runnable fn;

        UniRun(Executor executor, juc.CompletableFuture<Void> dep,
               juc.CompletableFuture<T> src, Runnable fn) {
            super(executor, dep, src);
            this.fn = fn;
        }

        final juc.CompletableFuture<Void> tryFire(int mode) {
            juc.CompletableFuture<Void> d;
            juc.CompletableFuture<T> a;
            if ((d = dep) == null ||
                    !d.uniRun(a = src, fn, mode > 0 ? null : this))
                return null;
            dep = null;
            src = null;
            fn = null;
            return d.postFire(a, mode);
        }
    }

    final boolean uniRun(juc.CompletableFuture<?> a, Runnable f, UniRun<?> c) {
        Object r;
        Throwable x;
        if (a == null || (r = a.result) == null || f == null)
            return false;
        if (result == null) {
            if (r instanceof AltResult && (x = ((AltResult) r).ex) != null)
                completeThrowable(x, r);
            else
                try {
                    if (c != null && !c.claim())
                        return false;
                    f.run();
                    completeNull();
                } catch (Throwable ex) {
                    completeThrowable(ex);
                }
        }
        return true;
    }

    private juc.CompletableFuture<Void> uniRunStage(Executor e, Runnable f) {
        if (f == null) throw new NullPointerException();
        juc.CompletableFuture<Void> d = new juc.CompletableFuture<Void>();
        if (e != null || !d.uniRun(this, f, null)) {
            UniRun<T> c = new UniRun<T>(e, d, this, f);
            push(c);
            c.tryFire(SYNC);
        }
        return d;
    }

    @SuppressWarnings("serial")
    static final class UniWhenComplete<T> extends UniCompletion<T, T> {
        BiConsumer<? super T, ? super Throwable> fn;

        UniWhenComplete(Executor executor, juc.CompletableFuture<T> dep,
                        juc.CompletableFuture<T> src,
                        BiConsumer<? super T, ? super Throwable> fn) {
            super(executor, dep, src);
            this.fn = fn;
        }

        final juc.CompletableFuture<T> tryFire(int mode) {
            juc.CompletableFuture<T> d;
            juc.CompletableFuture<T> a;
            if ((d = dep) == null ||
                    !d.uniWhenComplete(a = src, fn, mode > 0 ? null : this))
                return null;
            dep = null;
            src = null;
            fn = null;
            return d.postFire(a, mode);
        }
    }

    final boolean uniWhenComplete(juc.CompletableFuture<T> a,
                                  BiConsumer<? super T, ? super Throwable> f,
                                  UniWhenComplete<T> c) {
        Object r;
        T t;
        Throwable x = null;
        if (a == null || (r = a.result) == null || f == null)
            return false;
        if (result == null) {
            try {
                if (c != null && !c.claim())
                    return false;
                if (r instanceof AltResult) {
                    x = ((AltResult) r).ex;
                    t = null;
                } else {
                    @SuppressWarnings("unchecked") T tr = (T) r;
                    t = tr;
                }
                f.accept(t, x);
                if (x == null) {
                    internalComplete(r);
                    return true;
                }
            } catch (Throwable ex) {
                if (x == null)
                    x = ex;
            }
            completeThrowable(x, r);
        }
        return true;
    }

    private juc.CompletableFuture<T> uniWhenCompleteStage(
            Executor e, BiConsumer<? super T, ? super Throwable> f) {
        if (f == null) throw new NullPointerException();
        juc.CompletableFuture<T> d = new juc.CompletableFuture<T>();
        if (e != null || !d.uniWhenComplete(this, f, null)) {
            UniWhenComplete<T> c = new UniWhenComplete<T>(e, d, this, f);
            push(c);
            c.tryFire(SYNC);
        }
        return d;
    }

    @SuppressWarnings("serial")
    static final class UniHandle<T, V> extends UniCompletion<T, V> {
        BiFunction<? super T, Throwable, ? extends V> fn;

        UniHandle(Executor executor, juc.CompletableFuture<V> dep,
                  juc.CompletableFuture<T> src,
                  BiFunction<? super T, Throwable, ? extends V> fn) {
            super(executor, dep, src);
            this.fn = fn;
        }

        final juc.CompletableFuture<V> tryFire(int mode) {
            juc.CompletableFuture<V> d;
            juc.CompletableFuture<T> a;
            if ((d = dep) == null ||
                    !d.uniHandle(a = src, fn, mode > 0 ? null : this))
                return null;
            dep = null;
            src = null;
            fn = null;
            return d.postFire(a, mode);
        }
    }

    final <S> boolean uniHandle(juc.CompletableFuture<S> a,
                                BiFunction<? super S, Throwable, ? extends T> f,
                                UniHandle<S, T> c) {
        Object r;
        S s;
        Throwable x;
        if (a == null || (r = a.result) == null || f == null)
            return false;
        if (result == null) {
            try {
                if (c != null && !c.claim())
                    return false;
                if (r instanceof AltResult) {
                    x = ((AltResult) r).ex;
                    s = null;
                } else {
                    x = null;
                    @SuppressWarnings("unchecked") S ss = (S) r;
                    s = ss;
                }
                completeValue(f.apply(s, x));
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    private <V> juc.CompletableFuture<V> uniHandleStage(
            Executor e, BiFunction<? super T, Throwable, ? extends V> f) {
        if (f == null) throw new NullPointerException();
        juc.CompletableFuture<V> d = new juc.CompletableFuture<V>();
        if (e != null || !d.uniHandle(this, f, null)) {
            UniHandle<T, V> c = new UniHandle<T, V>(e, d, this, f);
            push(c);
            c.tryFire(SYNC);
        }
        return d;
    }

    @SuppressWarnings("serial")
    static final class UniExceptionally<T> extends UniCompletion<T, T> {
        Function<? super Throwable, ? extends T> fn;

        UniExceptionally(juc.CompletableFuture<T> dep, juc.CompletableFuture<T> src,
                         Function<? super Throwable, ? extends T> fn) {
            super(null, dep, src);
            this.fn = fn;
        }

        final juc.CompletableFuture<T> tryFire(int mode) { // never ASYNC
            // assert mode != ASYNC;
            juc.CompletableFuture<T> d;
            juc.CompletableFuture<T> a;
            if ((d = dep) == null || !d.uniExceptionally(a = src, fn, this))
                return null;
            dep = null;
            src = null;
            fn = null;
            return d.postFire(a, mode);
        }
    }

    final boolean uniExceptionally(juc.CompletableFuture<T> a,
                                   Function<? super Throwable, ? extends T> f,
                                   UniExceptionally<T> c) {
        Object r;
        Throwable x;
        if (a == null || (r = a.result) == null || f == null)
            return false;
        if (result == null) {
            try {
                if (r instanceof AltResult && (x = ((AltResult) r).ex) != null) {
                    if (c != null && !c.claim())
                        return false;
                    completeValue(f.apply(x));
                } else
                    internalComplete(r);
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    private juc.CompletableFuture<T> uniExceptionallyStage(
            Function<Throwable, ? extends T> f) {
        if (f == null) throw new NullPointerException();
        juc.CompletableFuture<T> d = new juc.CompletableFuture<T>();
        if (!d.uniExceptionally(this, f, null)) {
            UniExceptionally<T> c = new UniExceptionally<T>(d, this, f);
            push(c);
            c.tryFire(SYNC);
        }
        return d;
    }

    @SuppressWarnings("serial")
    static final class UniRelay<T> extends UniCompletion<T, T> { // for Compose
        UniRelay(juc.CompletableFuture<T> dep, juc.CompletableFuture<T> src) {
            super(null, dep, src);
        }

        final juc.CompletableFuture<T> tryFire(int mode) {
            juc.CompletableFuture<T> d;
            juc.CompletableFuture<T> a;
            if ((d = dep) == null || !d.uniRelay(a = src))
                return null;
            src = null;
            dep = null;
            return d.postFire(a, mode);
        }
    }

    final boolean uniRelay(juc.CompletableFuture<T> a) {
        Object r;
        if (a == null || (r = a.result) == null)
            return false;
        if (result == null) // no need to claim
            completeRelay(r);
        return true;
    }

    @SuppressWarnings("serial")
    static final class UniCompose<T, V> extends UniCompletion<T, V> {
        Function<? super T, ? extends CompletionStage<V>> fn;

        UniCompose(Executor executor, juc.CompletableFuture<V> dep,
                   juc.CompletableFuture<T> src,
                   Function<? super T, ? extends CompletionStage<V>> fn) {
            super(executor, dep, src);
            this.fn = fn;
        }

        final juc.CompletableFuture<V> tryFire(int mode) {
            juc.CompletableFuture<V> d;
            juc.CompletableFuture<T> a;
            if ((d = dep) == null ||
                    !d.uniCompose(a = src, fn, mode > 0 ? null : this))
                return null;
            dep = null;
            src = null;
            fn = null;
            return d.postFire(a, mode);
        }
    }

    final <S> boolean uniCompose(
            juc.CompletableFuture<S> a,
            Function<? super S, ? extends CompletionStage<T>> f,
            UniCompose<S, T> c) {
        Object r;
        Throwable x;
        if (a == null || (r = a.result) == null || f == null)
            return false;
        tryComplete:
        if (result == null) {
            if (r instanceof AltResult) {
                if ((x = ((AltResult) r).ex) != null) {
                    completeThrowable(x, r);
                    break tryComplete;
                }
                r = null;
            }
            try {
                if (c != null && !c.claim())
                    return false;
                @SuppressWarnings("unchecked") S s = (S) r;
                juc.CompletableFuture<T> g = f.apply(s).toCompletableFuture();
                if (g.result == null || !uniRelay(g)) {
                    UniRelay<T> copy = new UniRelay<T>(this, g);
                    g.push(copy);
                    copy.tryFire(SYNC);
                    if (result == null)
                        return false;
                }
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    private <V> juc.CompletableFuture<V> uniComposeStage(
            Executor e, Function<? super T, ? extends CompletionStage<V>> f) {
        if (f == null) throw new NullPointerException();
        Object r;
        Throwable x;
        if (e == null && (r = result) != null) {
            // try to return function result directly
            if (r instanceof AltResult) {
                if ((x = ((AltResult) r).ex) != null) {
                    return new juc.CompletableFuture<V>(encodeThrowable(x, r));
                }
                r = null;
            }
            try {
                @SuppressWarnings("unchecked") T t = (T) r;
                juc.CompletableFuture<V> g = f.apply(t).toCompletableFuture();
                Object s = g.result;
                if (s != null)
                    return new juc.CompletableFuture<V>(encodeRelay(s));
                juc.CompletableFuture<V> d = new juc.CompletableFuture<V>();
                UniRelay<V> copy = new UniRelay<V>(d, g);
                g.push(copy);
                copy.tryFire(SYNC);
                return d;
            } catch (Throwable ex) {
                return new juc.CompletableFuture<V>(encodeThrowable(ex));
            }
        }
        juc.CompletableFuture<V> d = new juc.CompletableFuture<V>();
        UniCompose<T, V> c = new UniCompose<T, V>(e, d, this, f);
        push(c);
        c.tryFire(SYNC);
        return d;
    }

    /* ------------- Two-input Completions -------------- */

    /**
     * A Completion for an action with two sources
     */
    @SuppressWarnings("serial")
    abstract static class BiCompletion<T, U, V> extends UniCompletion<T, V> {
        juc.CompletableFuture<U> snd; // second source for action

        BiCompletion(Executor executor, juc.CompletableFuture<V> dep,
                     juc.CompletableFuture<T> src, juc.CompletableFuture<U> snd) {
            super(executor, dep, src);
            this.snd = snd;
        }
    }

    /**
     * A Completion delegating to a BiCompletion
     */
    @SuppressWarnings("serial")
    static final class CoCompletion extends Completion {
        BiCompletion<?, ?, ?> base;

        CoCompletion(BiCompletion<?, ?, ?> base) {
            this.base = base;
        }

        final juc.CompletableFuture<?> tryFire(int mode) {
            BiCompletion<?, ?, ?> c;
            juc.CompletableFuture<?> d;
            if ((c = base) == null || (d = c.tryFire(mode)) == null)
                return null;
            base = null; // detach
            return d;
        }

        final boolean isLive() {
            BiCompletion<?, ?, ?> c;
            return (c = base) != null && c.dep != null;
        }
    }

    /**
     * Pushes completion to this and b unless both done.
     */
    final void bipush(juc.CompletableFuture<?> b, BiCompletion<?, ?, ?> c) {
        if (c != null) {
            Object r;
            while ((r = result) == null && !tryPushStack(c))
                lazySetNext(c, null); // clear on failure
            if (b != null && b != this && b.result == null) {
                Completion q = (r != null) ? c : new CoCompletion(c);
                while (b.result == null && !b.tryPushStack(q))
                    lazySetNext(q, null); // clear on failure
            }
        }
    }

    /**
     * Post-processing after successful BiCompletion tryFire.
     */
    final juc.CompletableFuture<T> postFire(juc.CompletableFuture<?> a,
                                            juc.CompletableFuture<?> b, int mode) {
        if (b != null && b.stack != null) { // clean second source
            if (mode < 0 || b.result == null)
                b.cleanStack();
            else
                b.postComplete();
        }
        return postFire(a, mode);
    }

    @SuppressWarnings("serial")
    static final class BiApply<T, U, V> extends BiCompletion<T, U, V> {
        BiFunction<? super T, ? super U, ? extends V> fn;

        BiApply(Executor executor, juc.CompletableFuture<V> dep,
                juc.CompletableFuture<T> src, juc.CompletableFuture<U> snd,
                BiFunction<? super T, ? super U, ? extends V> fn) {
            super(executor, dep, src, snd);
            this.fn = fn;
        }

        final juc.CompletableFuture<V> tryFire(int mode) {
            juc.CompletableFuture<V> d;
            juc.CompletableFuture<T> a;
            juc.CompletableFuture<U> b;
            if ((d = dep) == null ||
                    !d.biApply(a = src, b = snd, fn, mode > 0 ? null : this))
                return null;
            dep = null;
            src = null;
            snd = null;
            fn = null;
            return d.postFire(a, b, mode);
        }
    }

    final <R, S> boolean biApply(juc.CompletableFuture<R> a,
                                 juc.CompletableFuture<S> b,
                                 BiFunction<? super R, ? super S, ? extends T> f,
                                 BiApply<R, S, T> c) {
        Object r, s;
        Throwable x;
        if (a == null || (r = a.result) == null ||
                b == null || (s = b.result) == null || f == null)
            return false;
        tryComplete:
        if (result == null) {
            if (r instanceof AltResult) {
                if ((x = ((AltResult) r).ex) != null) {
                    completeThrowable(x, r);
                    break tryComplete;
                }
                r = null;
            }
            if (s instanceof AltResult) {
                if ((x = ((AltResult) s).ex) != null) {
                    completeThrowable(x, s);
                    break tryComplete;
                }
                s = null;
            }
            try {
                if (c != null && !c.claim())
                    return false;
                @SuppressWarnings("unchecked") R rr = (R) r;
                @SuppressWarnings("unchecked") S ss = (S) s;
                completeValue(f.apply(rr, ss));
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    private <U, V> juc.CompletableFuture<V> biApplyStage(
            Executor e, CompletionStage<U> o,
            BiFunction<? super T, ? super U, ? extends V> f) {
        juc.CompletableFuture<U> b;
        if (f == null || (b = o.toCompletableFuture()) == null)
            throw new NullPointerException();
        juc.CompletableFuture<V> d = new juc.CompletableFuture<V>();
        if (e != null || !d.biApply(this, b, f, null)) {
            BiApply<T, U, V> c = new BiApply<T, U, V>(e, d, this, b, f);
            bipush(b, c);
            c.tryFire(SYNC);
        }
        return d;
    }

    @SuppressWarnings("serial")
    static final class BiAccept<T, U> extends BiCompletion<T, U, Void> {
        BiConsumer<? super T, ? super U> fn;

        BiAccept(Executor executor, juc.CompletableFuture<Void> dep,
                 juc.CompletableFuture<T> src, juc.CompletableFuture<U> snd,
                 BiConsumer<? super T, ? super U> fn) {
            super(executor, dep, src, snd);
            this.fn = fn;
        }

        final juc.CompletableFuture<Void> tryFire(int mode) {
            juc.CompletableFuture<Void> d;
            juc.CompletableFuture<T> a;
            juc.CompletableFuture<U> b;
            if ((d = dep) == null ||
                    !d.biAccept(a = src, b = snd, fn, mode > 0 ? null : this))
                return null;
            dep = null;
            src = null;
            snd = null;
            fn = null;
            return d.postFire(a, b, mode);
        }
    }

    final <R, S> boolean biAccept(juc.CompletableFuture<R> a,
                                  juc.CompletableFuture<S> b,
                                  BiConsumer<? super R, ? super S> f,
                                  BiAccept<R, S> c) {
        Object r, s;
        Throwable x;
        if (a == null || (r = a.result) == null ||
                b == null || (s = b.result) == null || f == null)
            return false;
        tryComplete:
        if (result == null) {
            if (r instanceof AltResult) {
                if ((x = ((AltResult) r).ex) != null) {
                    completeThrowable(x, r);
                    break tryComplete;
                }
                r = null;
            }
            if (s instanceof AltResult) {
                if ((x = ((AltResult) s).ex) != null) {
                    completeThrowable(x, s);
                    break tryComplete;
                }
                s = null;
            }
            try {
                if (c != null && !c.claim())
                    return false;
                @SuppressWarnings("unchecked") R rr = (R) r;
                @SuppressWarnings("unchecked") S ss = (S) s;
                f.accept(rr, ss);
                completeNull();
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    private <U> juc.CompletableFuture<Void> biAcceptStage(
            Executor e, CompletionStage<U> o,
            BiConsumer<? super T, ? super U> f) {
        juc.CompletableFuture<U> b;
        if (f == null || (b = o.toCompletableFuture()) == null)
            throw new NullPointerException();
        juc.CompletableFuture<Void> d = new juc.CompletableFuture<Void>();
        if (e != null || !d.biAccept(this, b, f, null)) {
            BiAccept<T, U> c = new BiAccept<T, U>(e, d, this, b, f);
            bipush(b, c);
            c.tryFire(SYNC);
        }
        return d;
    }

    @SuppressWarnings("serial")
    static final class BiRun<T, U> extends BiCompletion<T, U, Void> {
        Runnable fn;

        BiRun(Executor executor, juc.CompletableFuture<Void> dep,
              juc.CompletableFuture<T> src,
              juc.CompletableFuture<U> snd,
              Runnable fn) {
            super(executor, dep, src, snd);
            this.fn = fn;
        }

        final juc.CompletableFuture<Void> tryFire(int mode) {
            juc.CompletableFuture<Void> d;
            juc.CompletableFuture<T> a;
            juc.CompletableFuture<U> b;
            if ((d = dep) == null ||
                    !d.biRun(a = src, b = snd, fn, mode > 0 ? null : this))
                return null;
            dep = null;
            src = null;
            snd = null;
            fn = null;
            return d.postFire(a, b, mode);
        }
    }

    final boolean biRun(juc.CompletableFuture<?> a, juc.CompletableFuture<?> b,
                        Runnable f, BiRun<?, ?> c) {
        Object r, s;
        Throwable x;
        if (a == null || (r = a.result) == null ||
                b == null || (s = b.result) == null || f == null)
            return false;
        if (result == null) {
            if (r instanceof AltResult && (x = ((AltResult) r).ex) != null)
                completeThrowable(x, r);
            else if (s instanceof AltResult && (x = ((AltResult) s).ex) != null)
                completeThrowable(x, s);
            else
                try {
                    if (c != null && !c.claim())
                        return false;
                    f.run();
                    completeNull();
                } catch (Throwable ex) {
                    completeThrowable(ex);
                }
        }
        return true;
    }

    private juc.CompletableFuture<Void> biRunStage(Executor e, CompletionStage<?> o,
                                                   Runnable f) {
        juc.CompletableFuture<?> b;
        if (f == null || (b = o.toCompletableFuture()) == null)
            throw new NullPointerException();
        juc.CompletableFuture<Void> d = new juc.CompletableFuture<Void>();
        if (e != null || !d.biRun(this, b, f, null)) {
            BiRun<T, ?> c = new BiRun<>(e, d, this, b, f);
            bipush(b, c);
            c.tryFire(SYNC);
        }
        return d;
    }

    @SuppressWarnings("serial")
    static final class BiRelay<T, U> extends BiCompletion<T, U, Void> { // for And
        BiRelay(juc.CompletableFuture<Void> dep,
                juc.CompletableFuture<T> src,
                juc.CompletableFuture<U> snd) {
            super(null, dep, src, snd);
        }

        final juc.CompletableFuture<Void> tryFire(int mode) {
            juc.CompletableFuture<Void> d;
            juc.CompletableFuture<T> a;
            juc.CompletableFuture<U> b;
            if ((d = dep) == null || !d.biRelay(a = src, b = snd))
                return null;
            src = null;
            snd = null;
            dep = null;
            return d.postFire(a, b, mode);
        }
    }

    boolean biRelay(juc.CompletableFuture<?> a, juc.CompletableFuture<?> b) {
        Object r, s;
        Throwable x;
        if (a == null || (r = a.result) == null ||
                b == null || (s = b.result) == null)
            return false;
        if (result == null) {
            if (r instanceof AltResult && (x = ((AltResult) r).ex) != null)
                completeThrowable(x, r);
            else if (s instanceof AltResult && (x = ((AltResult) s).ex) != null)
                completeThrowable(x, s);
            else
                completeNull();
        }
        return true;
    }

    /**
     * Recursively constructs a tree of completions.
     */
    static juc.CompletableFuture<Void> andTree(juc.CompletableFuture<?>[] cfs,
                                               int lo, int hi) {
        juc.CompletableFuture<Void> d = new juc.CompletableFuture<Void>();
        if (lo > hi) // empty
            d.result = NIL;
        else {
            juc.CompletableFuture<?> a, b;
            int mid = (lo + hi) >>> 1;
            if ((a = (lo == mid ? cfs[lo] :
                    andTree(cfs, lo, mid))) == null ||
                    (b = (lo == hi ? a : (hi == mid + 1) ? cfs[hi] :
                            andTree(cfs, mid + 1, hi))) == null)
                throw new NullPointerException();
            if (!d.biRelay(a, b)) {
                BiRelay<?, ?> c = new BiRelay<>(d, a, b);
                a.bipush(b, c);
                c.tryFire(SYNC);
            }
        }
        return d;
    }

    /* ------------- Projected (Ored) BiCompletions -------------- */

    /**
     * Pushes completion to this and b unless either done.
     */
    final void orpush(juc.CompletableFuture<?> b, BiCompletion<?, ?, ?> c) {
        if (c != null) {
            while ((b == null || b.result == null) && result == null) {
                if (tryPushStack(c)) {
                    if (b != null && b != this && b.result == null) {
                        Completion q = new CoCompletion(c);
                        while (result == null && b.result == null &&
                                !b.tryPushStack(q))
                            lazySetNext(q, null); // clear on failure
                    }
                    break;
                }
                lazySetNext(c, null); // clear on failure
            }
        }
    }

    @SuppressWarnings("serial")
    static final class OrApply<T, U extends T, V> extends BiCompletion<T, U, V> {
        Function<? super T, ? extends V> fn;

        OrApply(Executor executor, juc.CompletableFuture<V> dep,
                juc.CompletableFuture<T> src,
                juc.CompletableFuture<U> snd,
                Function<? super T, ? extends V> fn) {
            super(executor, dep, src, snd);
            this.fn = fn;
        }

        final juc.CompletableFuture<V> tryFire(int mode) {
            juc.CompletableFuture<V> d;
            juc.CompletableFuture<T> a;
            juc.CompletableFuture<U> b;
            if ((d = dep) == null ||
                    !d.orApply(a = src, b = snd, fn, mode > 0 ? null : this))
                return null;
            dep = null;
            src = null;
            snd = null;
            fn = null;
            return d.postFire(a, b, mode);
        }
    }

    final <R, S extends R> boolean orApply(juc.CompletableFuture<R> a,
                                           juc.CompletableFuture<S> b,
                                           Function<? super R, ? extends T> f,
                                           OrApply<R, S, T> c) {
        Object r;
        Throwable x;
        if (a == null || b == null ||
                ((r = a.result) == null && (r = b.result) == null) || f == null)
            return false;
        tryComplete:
        if (result == null) {
            try {
                if (c != null && !c.claim())
                    return false;
                if (r instanceof AltResult) {
                    if ((x = ((AltResult) r).ex) != null) {
                        completeThrowable(x, r);
                        break tryComplete;
                    }
                    r = null;
                }
                @SuppressWarnings("unchecked") R rr = (R) r;
                completeValue(f.apply(rr));
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    private <U extends T, V> juc.CompletableFuture<V> orApplyStage(
            Executor e, CompletionStage<U> o,
            Function<? super T, ? extends V> f) {
        juc.CompletableFuture<U> b;
        if (f == null || (b = o.toCompletableFuture()) == null)
            throw new NullPointerException();
        juc.CompletableFuture<V> d = new juc.CompletableFuture<V>();
        if (e != null || !d.orApply(this, b, f, null)) {
            OrApply<T, U, V> c = new OrApply<T, U, V>(e, d, this, b, f);
            orpush(b, c);
            c.tryFire(SYNC);
        }
        return d;
    }

    @SuppressWarnings("serial")
    static final class OrAccept<T, U extends T> extends BiCompletion<T, U, Void> {
        Consumer<? super T> fn;

        OrAccept(Executor executor, juc.CompletableFuture<Void> dep,
                 juc.CompletableFuture<T> src,
                 juc.CompletableFuture<U> snd,
                 Consumer<? super T> fn) {
            super(executor, dep, src, snd);
            this.fn = fn;
        }

        final juc.CompletableFuture<Void> tryFire(int mode) {
            juc.CompletableFuture<Void> d;
            juc.CompletableFuture<T> a;
            juc.CompletableFuture<U> b;
            if ((d = dep) == null ||
                    !d.orAccept(a = src, b = snd, fn, mode > 0 ? null : this))
                return null;
            dep = null;
            src = null;
            snd = null;
            fn = null;
            return d.postFire(a, b, mode);
        }
    }

    final <R, S extends R> boolean orAccept(juc.CompletableFuture<R> a,
                                            juc.CompletableFuture<S> b,
                                            Consumer<? super R> f,
                                            OrAccept<R, S> c) {
        Object r;
        Throwable x;
        if (a == null || b == null ||
                ((r = a.result) == null && (r = b.result) == null) || f == null)
            return false;
        tryComplete:
        if (result == null) {
            try {
                if (c != null && !c.claim())
                    return false;
                if (r instanceof AltResult) {
                    if ((x = ((AltResult) r).ex) != null) {
                        completeThrowable(x, r);
                        break tryComplete;
                    }
                    r = null;
                }
                @SuppressWarnings("unchecked") R rr = (R) r;
                f.accept(rr);
                completeNull();
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    private <U extends T> juc.CompletableFuture<Void> orAcceptStage(
            Executor e, CompletionStage<U> o, Consumer<? super T> f) {
        juc.CompletableFuture<U> b;
        if (f == null || (b = o.toCompletableFuture()) == null)
            throw new NullPointerException();
        juc.CompletableFuture<Void> d = new juc.CompletableFuture<Void>();
        if (e != null || !d.orAccept(this, b, f, null)) {
            OrAccept<T, U> c = new OrAccept<T, U>(e, d, this, b, f);
            orpush(b, c);
            c.tryFire(SYNC);
        }
        return d;
    }

    @SuppressWarnings("serial")
    static final class OrRun<T, U> extends BiCompletion<T, U, Void> {
        Runnable fn;

        OrRun(Executor executor, juc.CompletableFuture<Void> dep,
              juc.CompletableFuture<T> src,
              juc.CompletableFuture<U> snd,
              Runnable fn) {
            super(executor, dep, src, snd);
            this.fn = fn;
        }

        final juc.CompletableFuture<Void> tryFire(int mode) {
            juc.CompletableFuture<Void> d;
            juc.CompletableFuture<T> a;
            juc.CompletableFuture<U> b;
            if ((d = dep) == null ||
                    !d.orRun(a = src, b = snd, fn, mode > 0 ? null : this))
                return null;
            dep = null;
            src = null;
            snd = null;
            fn = null;
            return d.postFire(a, b, mode);
        }
    }

    final boolean orRun(juc.CompletableFuture<?> a, juc.CompletableFuture<?> b,
                        Runnable f, OrRun<?, ?> c) {
        Object r;
        Throwable x;
        if (a == null || b == null ||
                ((r = a.result) == null && (r = b.result) == null) || f == null)
            return false;
        if (result == null) {
            try {
                if (c != null && !c.claim())
                    return false;
                if (r instanceof AltResult && (x = ((AltResult) r).ex) != null)
                    completeThrowable(x, r);
                else {
                    f.run();
                    completeNull();
                }
            } catch (Throwable ex) {
                completeThrowable(ex);
            }
        }
        return true;
    }

    private juc.CompletableFuture<Void> orRunStage(Executor e, CompletionStage<?> o,
                                                   Runnable f) {
        juc.CompletableFuture<?> b;
        if (f == null || (b = o.toCompletableFuture()) == null)
            throw new NullPointerException();
        juc.CompletableFuture<Void> d = new juc.CompletableFuture<Void>();
        if (e != null || !d.orRun(this, b, f, null)) {
            OrRun<T, ?> c = new OrRun<>(e, d, this, b, f);
            orpush(b, c);
            c.tryFire(SYNC);
        }
        return d;
    }

    @SuppressWarnings("serial")
    static final class OrRelay<T, U> extends BiCompletion<T, U, Object> { // for Or
        OrRelay(juc.CompletableFuture<Object> dep, juc.CompletableFuture<T> src,
                juc.CompletableFuture<U> snd) {
            super(null, dep, src, snd);
        }

        final juc.CompletableFuture<Object> tryFire(int mode) {
            juc.CompletableFuture<Object> d;
            juc.CompletableFuture<T> a;
            juc.CompletableFuture<U> b;
            if ((d = dep) == null || !d.orRelay(a = src, b = snd))
                return null;
            src = null;
            snd = null;
            dep = null;
            return d.postFire(a, b, mode);
        }
    }

    final boolean orRelay(juc.CompletableFuture<?> a, juc.CompletableFuture<?> b) {
        Object r;
        if (a == null || b == null ||
                ((r = a.result) == null && (r = b.result) == null))
            return false;
        if (result == null)
            completeRelay(r);
        return true;
    }

    /**
     * Recursively constructs a tree of completions.
     */
    static juc.CompletableFuture<Object> orTree(juc.CompletableFuture<?>[] cfs,
                                                int lo, int hi) {
        juc.CompletableFuture<Object> d = new juc.CompletableFuture<Object>();
        if (lo <= hi) {
            juc.CompletableFuture<?> a, b;
            int mid = (lo + hi) >>> 1;
            if ((a = (lo == mid ? cfs[lo] :
                    orTree(cfs, lo, mid))) == null ||
                    (b = (lo == hi ? a : (hi == mid + 1) ? cfs[hi] :
                            orTree(cfs, mid + 1, hi))) == null)
                throw new NullPointerException();
            if (!d.orRelay(a, b)) {
                OrRelay<?, ?> c = new OrRelay<>(d, a, b);
                a.orpush(b, c);
                c.tryFire(SYNC);
            }
        }
        return d;
    }

    /* ------------- Zero-input Async forms -------------- */

    @SuppressWarnings("serial")
    static final class AsyncSupply<T> extends ForkJoinTask<Void>
            implements Runnable, AsynchronousCompletionTask {
        juc.CompletableFuture<T> dep;
        Supplier<T> fn;

        AsyncSupply(juc.CompletableFuture<T> dep, Supplier<T> fn) {
            this.dep = dep;
            this.fn = fn;
        }

        public final Void getRawResult() {
            return null;
        }

        public final void setRawResult(Void v) {
        }

        public final boolean exec() {
            run();
            return true;
        }

        public void run() {
            juc.CompletableFuture<T> d;
            Supplier<T> f;
            if ((d = dep) != null && (f = fn) != null) {
                dep = null;
                fn = null;
                if (d.result == null) {
                    try {
                        d.completeValue(f.get());
                    } catch (Throwable ex) {
                        d.completeThrowable(ex);
                    }
                }
                d.postComplete();
            }
        }
    }

    static <U> juc.CompletableFuture<U> asyncSupplyStage(Executor e,
                                                         Supplier<U> f) {
        if (f == null) throw new NullPointerException();
        juc.CompletableFuture<U> d = new juc.CompletableFuture<U>();
        e.execute(new AsyncSupply<U>(d, f));
        return d;
    }

    @SuppressWarnings("serial")
    static final class AsyncRun extends ForkJoinTask<Void>
            implements Runnable, AsynchronousCompletionTask {
        juc.CompletableFuture<Void> dep;
        Runnable fn;

        AsyncRun(juc.CompletableFuture<Void> dep, Runnable fn) {
            this.dep = dep;
            this.fn = fn;
        }

        public final Void getRawResult() {
            return null;
        }

        public final void setRawResult(Void v) {
        }

        public final boolean exec() {
            run();
            return true;
        }

        public void run() {
            juc.CompletableFuture<Void> d;
            Runnable f;
            if ((d = dep) != null && (f = fn) != null) {
                dep = null;
                fn = null;
                if (d.result == null) {
                    try {
                        f.run();
                        d.completeNull();
                    } catch (Throwable ex) {
                        d.completeThrowable(ex);
                    }
                }
                d.postComplete();
            }
        }
    }

    static juc.CompletableFuture<Void> asyncRunStage(Executor e, Runnable f) {
        if (f == null) throw new NullPointerException();
        juc.CompletableFuture<Void> d = new juc.CompletableFuture<Void>();
        e.execute(new AsyncRun(d, f));
        return d;
    }

    /* ------------- Signallers -------------- */

    /**
     * Completion for recording and releasing a waiting thread.  This
     * class implements ManagedBlocker to avoid starvation when
     * blocking actions pile up in ForkJoinPools.
     */
    @SuppressWarnings("serial")
    static final class Signaller extends Completion
            implements ForkJoinPool.ManagedBlocker {
        long nanos;                    // wait time if timed
        final long deadline;           // non-zero if timed
        volatile int interruptControl; // > 0: interruptible, < 0: interrupted
        volatile Thread thread;

        Signaller(boolean interruptible, long nanos, long deadline) {
            this.thread = Thread.currentThread();
            this.interruptControl = interruptible ? 1 : 0;
            this.nanos = nanos;
            this.deadline = deadline;
        }

        final juc.CompletableFuture<?> tryFire(int ignore) {
            Thread w; // no need to atomically claim
            if ((w = thread) != null) {
                thread = null;
                LockSupport.unpark(w);
            }
            return null;
        }

        public boolean isReleasable() {
            if (thread == null)
                return true;
            if (Thread.interrupted()) {
                int i = interruptControl;
                interruptControl = -1;
                if (i > 0)
                    return true;
            }
            if (deadline != 0L &&
                    (nanos <= 0L || (nanos = deadline - System.nanoTime()) <= 0L)) {
                thread = null;
                return true;
            }
            return false;
        }

        public boolean block() {
            if (isReleasable())
                return true;
            else if (deadline == 0L)
                LockSupport.park(this);
            else if (nanos > 0L)
                LockSupport.parkNanos(this, nanos);
            return isReleasable();
        }

        final boolean isLive() {
            return thread != null;
        }
    }

    /**
     * Returns raw result after waiting, or null if interruptible and
     * interrupted.
     */
    private Object waitingGet(boolean interruptible) {
        Signaller q = null;
        boolean queued = false;
        int spins = -1;
        Object r;
        while ((r = result) == null) {
            if (spins < 0)
                spins = (Runtime.getRuntime().availableProcessors() > 1) ?
                        1 << 8 : 0; // Use brief spin-wait on multiprocessors
            else if (spins > 0) {
                if (ThreadLocalRandom.nextSecondarySeed() >= 0)
                    --spins;
            } else if (q == null)
                q = new Signaller(interruptible, 0L, 0L);
            else if (!queued)
                queued = tryPushStack(q);
            else if (interruptible && q.interruptControl < 0) {
                q.thread = null;
                cleanStack();
                return null;
            } else if (q.thread != null && result == null) {
                try {
                    ForkJoinPool.managedBlock(q);
                } catch (InterruptedException ie) {
                    q.interruptControl = -1;
                }
            }
        }
        if (q != null) {
            q.thread = null;
            if (q.interruptControl < 0) {
                if (interruptible)
                    r = null; // report interruption
                else
                    Thread.currentThread().interrupt();
            }
        }
        postComplete();
        return r;
    }

    /**
     * Returns raw result after waiting, or null if interrupted, or
     * throws TimeoutException on timeout.
     */
    private Object timedGet(long nanos) throws TimeoutException {
        if (Thread.interrupted())
            return null;
        if (nanos <= 0L)
            throw new TimeoutException();
        long d = System.nanoTime() + nanos;
        Signaller q = new Signaller(true, nanos, d == 0L ? 1L : d); // avoid 0
        boolean queued = false;
        Object r;
        // We intentionally don't spin here (as waitingGet does) because
        // the call to nanoTime() above acts much like a spin.
        while ((r = result) == null) {
            if (!queued)
                queued = tryPushStack(q);
            else if (q.interruptControl < 0 || q.nanos <= 0L) {
                q.thread = null;
                cleanStack();
                if (q.interruptControl < 0)
                    return null;
                throw new TimeoutException();
            } else if (q.thread != null && result == null) {
                try {
                    ForkJoinPool.managedBlock(q);
                } catch (InterruptedException ie) {
                    q.interruptControl = -1;
                }
            }
        }
        if (q.interruptControl < 0)
            r = null;
        q.thread = null;
        postComplete();
        return r;
    }

    /* ------------- public methods -------------- */

    /**
     * Creates a new incomplete CompletableFuture.
     */
    public CompletableFuture() {
    }

    /**
     * Creates a new complete CompletableFuture with given encoded result.
     */
    private CompletableFuture(Object r) {
        this.result = r;
    }

    /**
     * Returns a new CompletableFuture that is asynchronously completed
     * by a task running in the {@link ForkJoinPool#commonPool()} with
     * the value obtained by calling the given Supplier.
     *
     * @param supplier a function returning the value to be used
     *                 to complete the returned CompletableFuture
     * @param <U>      the function's return type
     * @return the new CompletableFuture
     */
    public static <U> juc.CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        return asyncSupplyStage(asyncPool, supplier);
    }

    /**
     * Returns a new CompletableFuture that is asynchronously completed
     * by a task running in the given executor with the value obtained
     * by calling the given Supplier.
     *
     * @param supplier a function returning the value to be used
     *                 to complete the returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @param <U>      the function's return type
     * @return the new CompletableFuture
     */
    public static <U> juc.CompletableFuture<U> supplyAsync(Supplier<U> supplier,
                                                           Executor executor) {
        return asyncSupplyStage(screenExecutor(executor), supplier);
    }

    /**
     * Returns a new CompletableFuture that is asynchronously completed
     * by a task running in the {@link ForkJoinPool#commonPool()} after
     * it runs the given action.
     *
     * @param runnable the action to run before completing the
     *                 returned CompletableFuture
     * @return the new CompletableFuture
     */
    public static juc.CompletableFuture<Void> runAsync(Runnable runnable) {
        return asyncRunStage(asyncPool, runnable);
    }

    /**
     * Returns a new CompletableFuture that is asynchronously completed
     * by a task running in the given executor after it runs the given
     * action.
     *
     * @param runnable the action to run before completing the
     *                 returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the new CompletableFuture
     */
    public static juc.CompletableFuture<Void> runAsync(Runnable runnable,
                                                       Executor executor) {
        return asyncRunStage(screenExecutor(executor), runnable);
    }

    /**
     * Returns a new CompletableFuture that is already completed with
     * the given value.
     *
     * @param value the value
     * @param <U>   the type of the value
     * @return the completed CompletableFuture
     */
    public static <U> juc.CompletableFuture<U> completedFuture(U value) {
        return new juc.CompletableFuture<U>((value == null) ? NIL : value);
    }

    /**
     * Returns {@code true} if completed in any fashion: normally,
     * exceptionally, or via cancellation.
     *
     * @return {@code true} if completed
     */
    public boolean isDone() {
        return result != null;
    }

    /**
     * Waits if necessary for this future to complete, and then
     * returns its result.
     *
     * @return the result value
     * @throws CancellationException if this future was cancelled
     * @throws ExecutionException    if this future completed exceptionally
     * @throws InterruptedException  if the current thread was interrupted
     *                               while waiting
     */
    public T get() throws InterruptedException, ExecutionException {
        Object r;
        return reportGet((r = result) == null ? waitingGet(true) : r);
    }

    /**
     * Waits if necessary for at most the given time for this future
     * to complete, and then returns its result, if available.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout argument
     * @return the result value
     * @throws CancellationException if this future was cancelled
     * @throws ExecutionException    if this future completed exceptionally
     * @throws InterruptedException  if the current thread was interrupted
     *                               while waiting
     * @throws TimeoutException      if the wait timed out
     */
    public T get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        Object r;
        long nanos = unit.toNanos(timeout);
        return reportGet((r = result) == null ? timedGet(nanos) : r);
    }

    /**
     * Returns the result value when complete, or throws an
     * (unchecked) exception if completed exceptionally. To better
     * conform with the use of common functional forms, if a
     * computation involved in the completion of this
     * CompletableFuture threw an exception, this method throws an
     * (unchecked) {@link CompletionException} with the underlying
     * exception as its cause.
     *
     * @return the result value
     * @throws CancellationException if the computation was cancelled
     * @throws CompletionException   if this future completed
     *                               exceptionally or a completion computation threw an exception
     */
    public T join() {
        Object r;
        return reportJoin((r = result) == null ? waitingGet(false) : r);
    }

    /**
     * Returns the result value (or throws any encountered exception)
     * if completed, else returns the given valueIfAbsent.
     *
     * @param valueIfAbsent the value to return if not completed
     * @return the result value, if completed, else the given valueIfAbsent
     * @throws CancellationException if the computation was cancelled
     * @throws CompletionException   if this future completed
     *                               exceptionally or a completion computation threw an exception
     */
    public T getNow(T valueIfAbsent) {
        Object r;
        return ((r = result) == null) ? valueIfAbsent : reportJoin(r);
    }

    /**
     * If not already completed, sets the value returned by {@link
     * #get()} and related methods to the given value.
     *
     * @param value the result value
     * @return {@code true} if this invocation caused this CompletableFuture
     * to transition to a completed state, else {@code false}
     */
    public boolean complete(T value) {
        boolean triggered = completeValue(value);
        postComplete();
        return triggered;
    }

    /**
     * If not already completed, causes invocations of {@link #get()}
     * and related methods to throw the given exception.
     *
     * @param ex the exception
     * @return {@code true} if this invocation caused this CompletableFuture
     * to transition to a completed state, else {@code false}
     */
    public boolean completeExceptionally(Throwable ex) {
        if (ex == null) throw new NullPointerException();
        boolean triggered = internalComplete(new AltResult(ex));
        postComplete();
        return triggered;
    }

    public <U> juc.CompletableFuture<U> thenApply(
            Function<? super T, ? extends U> fn) {
        return uniApplyStage(null, fn);
    }

    public <U> juc.CompletableFuture<U> thenApplyAsync(
            Function<? super T, ? extends U> fn) {
        return uniApplyStage(asyncPool, fn);
    }

    public <U> juc.CompletableFuture<U> thenApplyAsync(
            Function<? super T, ? extends U> fn, Executor executor) {
        return uniApplyStage(screenExecutor(executor), fn);
    }

    public juc.CompletableFuture<Void> thenAccept(Consumer<? super T> action) {
        return uniAcceptStage(null, action);
    }

    public juc.CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action) {
        return uniAcceptStage(asyncPool, action);
    }

    public juc.CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action,
                                                       Executor executor) {
        return uniAcceptStage(screenExecutor(executor), action);
    }

    public juc.CompletableFuture<Void> thenRun(Runnable action) {
        return uniRunStage(null, action);
    }

    public juc.CompletableFuture<Void> thenRunAsync(Runnable action) {
        return uniRunStage(asyncPool, action);
    }

    public juc.CompletableFuture<Void> thenRunAsync(Runnable action,
                                                    Executor executor) {
        return uniRunStage(screenExecutor(executor), action);
    }

    public <U, V> juc.CompletableFuture<V> thenCombine(
            CompletionStage<? extends U> other,
            BiFunction<? super T, ? super U, ? extends V> fn) {
        return biApplyStage(null, other, fn);
    }

    public <U, V> juc.CompletableFuture<V> thenCombineAsync(
            CompletionStage<? extends U> other,
            BiFunction<? super T, ? super U, ? extends V> fn) {
        return biApplyStage(asyncPool, other, fn);
    }

    public <U, V> juc.CompletableFuture<V> thenCombineAsync(
            CompletionStage<? extends U> other,
            BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
        return biApplyStage(screenExecutor(executor), other, fn);
    }

    public <U> juc.CompletableFuture<Void> thenAcceptBoth(
            CompletionStage<? extends U> other,
            BiConsumer<? super T, ? super U> action) {
        return biAcceptStage(null, other, action);
    }

    public <U> juc.CompletableFuture<Void> thenAcceptBothAsync(
            CompletionStage<? extends U> other,
            BiConsumer<? super T, ? super U> action) {
        return biAcceptStage(asyncPool, other, action);
    }

    public <U> juc.CompletableFuture<Void> thenAcceptBothAsync(
            CompletionStage<? extends U> other,
            BiConsumer<? super T, ? super U> action, Executor executor) {
        return biAcceptStage(screenExecutor(executor), other, action);
    }

    public juc.CompletableFuture<Void> runAfterBoth(CompletionStage<?> other,
                                                    Runnable action) {
        return biRunStage(null, other, action);
    }

    public juc.CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other,
                                                         Runnable action) {
        return biRunStage(asyncPool, other, action);
    }

    public juc.CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other,
                                                         Runnable action,
                                                         Executor executor) {
        return biRunStage(screenExecutor(executor), other, action);
    }

    public <U> juc.CompletableFuture<U> applyToEither(
            CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return orApplyStage(null, other, fn);
    }

    public <U> juc.CompletableFuture<U> applyToEitherAsync(
            CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return orApplyStage(asyncPool, other, fn);
    }

    public <U> juc.CompletableFuture<U> applyToEitherAsync(
            CompletionStage<? extends T> other, Function<? super T, U> fn,
            Executor executor) {
        return orApplyStage(screenExecutor(executor), other, fn);
    }

    public juc.CompletableFuture<Void> acceptEither(
            CompletionStage<? extends T> other, Consumer<? super T> action) {
        return orAcceptStage(null, other, action);
    }

    public juc.CompletableFuture<Void> acceptEitherAsync(
            CompletionStage<? extends T> other, Consumer<? super T> action) {
        return orAcceptStage(asyncPool, other, action);
    }

    public juc.CompletableFuture<Void> acceptEitherAsync(
            CompletionStage<? extends T> other, Consumer<? super T> action,
            Executor executor) {
        return orAcceptStage(screenExecutor(executor), other, action);
    }

    public juc.CompletableFuture<Void> runAfterEither(CompletionStage<?> other,
                                                      Runnable action) {
        return orRunStage(null, other, action);
    }

    public juc.CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other,
                                                           Runnable action) {
        return orRunStage(asyncPool, other, action);
    }

    public juc.CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other,
                                                           Runnable action,
                                                           Executor executor) {
        return orRunStage(screenExecutor(executor), other, action);
    }

    public <U> juc.CompletableFuture<U> thenCompose(
            Function<? super T, ? extends CompletionStage<U>> fn) {
        return uniComposeStage(null, fn);
    }

    public <U> juc.CompletableFuture<U> thenComposeAsync(
            Function<? super T, ? extends CompletionStage<U>> fn) {
        return uniComposeStage(asyncPool, fn);
    }

    public <U> juc.CompletableFuture<U> thenComposeAsync(
            Function<? super T, ? extends CompletionStage<U>> fn,
            Executor executor) {
        return uniComposeStage(screenExecutor(executor), fn);
    }

    public juc.CompletableFuture<T> whenComplete(
            BiConsumer<? super T, ? super Throwable> action) {
        return uniWhenCompleteStage(null, action);
    }

    public juc.CompletableFuture<T> whenCompleteAsync(
            BiConsumer<? super T, ? super Throwable> action) {
        return uniWhenCompleteStage(asyncPool, action);
    }

    public juc.CompletableFuture<T> whenCompleteAsync(
            BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return uniWhenCompleteStage(screenExecutor(executor), action);
    }

    public <U> juc.CompletableFuture<U> handle(
            BiFunction<? super T, Throwable, ? extends U> fn) {
        return uniHandleStage(null, fn);
    }

    public <U> juc.CompletableFuture<U> handleAsync(
            BiFunction<? super T, Throwable, ? extends U> fn) {
        return uniHandleStage(asyncPool, fn);
    }

    public <U> juc.CompletableFuture<U> handleAsync(
            BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return uniHandleStage(screenExecutor(executor), fn);
    }

    /**
     * Returns this CompletableFuture.
     *
     * @return this CompletableFuture
     */
    public juc.CompletableFuture<T> toCompletableFuture() {
        return this;
    }

    // not in interface CompletionStage

    /**
     * Returns a new CompletableFuture that is completed when this
     * CompletableFuture completes, with the result of the given
     * function of the exception triggering this CompletableFuture's
     * completion when it completes exceptionally; otherwise, if this
     * CompletableFuture completes normally, then the returned
     * CompletableFuture also completes normally with the same value.
     * Note: More flexible versions of this functionality are
     * available using methods {@code whenComplete} and {@code handle}.
     *
     * @param fn the function to use to compute the value of the
     *           returned CompletableFuture if this CompletableFuture completed
     *           exceptionally
     * @return the new CompletableFuture
     */
    public juc.CompletableFuture<T> exceptionally(
            Function<Throwable, ? extends T> fn) {
        return uniExceptionallyStage(fn);
    }

    /* ------------- Arbitrary-arity constructions -------------- */

    /**
     * Returns a new CompletableFuture that is completed when all of
     * the given CompletableFutures complete.  If any of the given
     * CompletableFutures complete exceptionally, then the returned
     * CompletableFuture also does so, with a CompletionException
     * holding this exception as its cause.  Otherwise, the results,
     * if any, of the given CompletableFutures are not reflected in
     * the returned CompletableFuture, but may be obtained by
     * inspecting them individually. If no CompletableFutures are
     * provided, returns a CompletableFuture completed with the value
     * {@code null}.
     *
     * <p>Among the applications of this method is to await completion
     * of a set of independent CompletableFutures before continuing a
     * program, as in: {@code CompletableFuture.allOf(c1, c2,
     * c3).join();}.
     *
     * @param cfs the CompletableFutures
     * @return a new CompletableFuture that is completed when all of the
     * given CompletableFutures complete
     * @throws NullPointerException if the array or any of its elements are
     *                              {@code null}
     */
    public static juc.CompletableFuture<Void> allOf(juc.CompletableFuture<?>... cfs) {
        return andTree(cfs, 0, cfs.length - 1);
    }

    /**
     * Returns a new CompletableFuture that is completed when any of
     * the given CompletableFutures complete, with the same result.
     * Otherwise, if it completed exceptionally, the returned
     * CompletableFuture also does so, with a CompletionException
     * holding this exception as its cause.  If no CompletableFutures
     * are provided, returns an incomplete CompletableFuture.
     *
     * @param cfs the CompletableFutures
     * @return a new CompletableFuture that is completed with the
     * result or exception of any of the given CompletableFutures when
     * one completes
     * @throws NullPointerException if the array or any of its elements are
     *                              {@code null}
     */
    public static juc.CompletableFuture<Object> anyOf(juc.CompletableFuture<?>... cfs) {
        return orTree(cfs, 0, cfs.length - 1);
    }

    /* ------------- Control and status methods -------------- */

    /**
     * If not already completed, completes this CompletableFuture with
     * a {@link CancellationException}. Dependent CompletableFutures
     * that have not already completed will also complete
     * exceptionally, with a {@link CompletionException} caused by
     * this {@code CancellationException}.
     *
     * @param mayInterruptIfRunning this value has no effect in this
     *                              implementation because interrupts are not used to control
     *                              processing.
     * @return {@code true} if this task is now cancelled
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = (result == null) &&
                internalComplete(new AltResult(new CancellationException()));
        postComplete();
        return cancelled || isCancelled();
    }

    /**
     * Returns {@code true} if this CompletableFuture was cancelled
     * before it completed normally.
     *
     * @return {@code true} if this CompletableFuture was cancelled
     * before it completed normally
     */
    public boolean isCancelled() {
        Object r;
        return ((r = result) instanceof AltResult) &&
                (((AltResult) r).ex instanceof CancellationException);
    }

    /**
     * Returns {@code true} if this CompletableFuture completed
     * exceptionally, in any way. Possible causes include
     * cancellation, explicit invocation of {@code
     * completeExceptionally}, and abrupt termination of a
     * CompletionStage action.
     *
     * @return {@code true} if this CompletableFuture completed
     * exceptionally
     */
    public boolean isCompletedExceptionally() {
        Object r;
        return ((r = result) instanceof AltResult) && r != NIL;
    }

    /**
     * Forcibly sets or resets the value subsequently returned by
     * method {@link #get()} and related methods, whether or not
     * already completed. This method is designed for use only in
     * error recovery actions, and even in such situations may result
     * in ongoing dependent completions using established versus
     * overwritten outcomes.
     *
     * @param value the completion value
     */
    public void obtrudeValue(T value) {
        result = (value == null) ? NIL : value;
        postComplete();
    }

    /**
     * Forcibly causes subsequent invocations of method {@link #get()}
     * and related methods to throw the given exception, whether or
     * not already completed. This method is designed for use only in
     * error recovery actions, and even in such situations may result
     * in ongoing dependent completions using established versus
     * overwritten outcomes.
     *
     * @param ex the exception
     * @throws NullPointerException if the exception is null
     */
    public void obtrudeException(Throwable ex) {
        if (ex == null) throw new NullPointerException();
        result = new AltResult(ex);
        postComplete();
    }

    /**
     * Returns the estimated number of CompletableFutures whose
     * completions are awaiting completion of this CompletableFuture.
     * This method is designed for use in monitoring system state, not
     * for synchronization control.
     *
     * @return the number of dependent CompletableFutures
     */
    public int getNumberOfDependents() {
        int count = 0;
        for (Completion p = stack; p != null; p = p.next)
            ++count;
        return count;
    }

    /**
     * Returns a string identifying this CompletableFuture, as well as
     * its completion state.  The state, in brackets, contains the
     * String {@code "Completed Normally"} or the String {@code
     * "Completed Exceptionally"}, or the String {@code "Not
     * completed"} followed by the number of CompletableFutures
     * dependent upon its completion, if any.
     *
     * @return a string identifying this CompletableFuture, as well as its state
     */
    public String toString() {
        Object r = result;
        int count;
        return super.toString() +
                ((r == null) ?
                        (((count = getNumberOfDependents()) == 0) ?
                                "[Not completed]" :
                                "[Not completed, " + count + " dependents]") :
                        (((r instanceof AltResult) && ((AltResult) r).ex != null) ?
                                "[Completed exceptionally]" :
                                "[Completed normally]"));
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long RESULT;
    private static final long STACK;
    private static final long NEXT;

    static {
        try {
            final sun.misc.Unsafe u;
            UNSAFE = u = sun.misc.Unsafe.getUnsafe();
            Class<?> k = juc.CompletableFuture.class;
            RESULT = u.objectFieldOffset(k.getDeclaredField("result"));
            STACK = u.objectFieldOffset(k.getDeclaredField("stack"));
            NEXT = u.objectFieldOffset
                    (Completion.class.getDeclaredField("next"));
        } catch (Exception x) {
            throw new Error(x);
        }
    }
}
