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


import unsafeTest.GetUnsafeFromReflect;

/**
 * A {@link ForkJoinTask} with a completion action performed when
 * triggered and there are no remaining pending actions.
 * CountedCompleters are in general more robust in the
 * presence of subtask stalls and blockage than are other forms of
 * ForkJoinTasks, but are less intuitive to program.  Uses of
 * CountedCompleter are similar to those of other completion based
 * components (such as {@link java.nio.channels.CompletionHandler})
 * except that multiple <em>pending</em> completions may be necessary
 * to trigger the completion action {@link #onCompletion(CountedCompleter)},
 * not just one.
 * Unless initialized otherwise, the {@linkplain #getPendingCount pending
 * count} starts at zero, but may be (atomically) changed using
 * methods {@link #setPendingCount}, {@link #addToPendingCount}, and
 * {@link #compareAndSetPendingCount}. Upon invocation of {@link
 * #tryComplete}, if the pending action count is nonzero, it is
 * decremented; otherwise, the completion action is performed, and if
 * this completer itself has a completer, the process is continued
 * with its completer.  As is the case with related synchronization
 * components such as {@link Phaser Phaser} and
 * {@link Semaphore Semaphore}, these methods
 * affect only internal counts; they do not establish any further
 * internal bookkeeping. In particular, the identities of pending
 * tasks are not maintained. As illustrated below, you can create
 * subclasses that do record some or all pending tasks or their
 * results when needed.  As illustrated below, utility methods
 * supporting customization of completion traversals are also
 * provided. However, because CountedCompleters provide only basic
 * synchronization mechanisms, it may be useful to create further
 * abstract subclasses that maintain linkages, fields, and additional
 * support methods appropriate for a set of related usages.
 * 一个ForkJoinTask，在被触发并且没有剩余的未决动作时，执行完成动作。
 * 通常，在存在子任务停滞和阻塞的情况下，CountedCompleters比其他形式的ForkJoinTasks更健壮，但编程起来不那么直观。
 * CountedCompleter的用法与其他基于完成的组件（例如CompletionHandler）相似，不同的是可能需要多个挂起的完成(completions )来触发onCompletion（CountedCompleter）上的完成动作，而不仅仅是一个。
 * 除非以其他方式初始化，否则未决（pending）计数从零开始，但是可以使用setPendingCount(int), addToPendingCount(int), 和compareAndSetPendingCount(int, int) 方法进行（原子上）更改。
 * 调用tryComplete（）时，如果未决的操作计数为非零，则将其递减；否则，执行完成操作，并且如果此完成程序本身具有完成程序，则使用其完成程序继续该过程。
 * 与相关的同步组件（如“Phaser ”和“Semaphore”）一样，这些方法仅影响内部计数。
 * 他们没有建立任何进一步的内部记录。特别是，未维护待处理任务的身份。
 * 如下所示，你可以创建子类，在需要时确实记录一些或所有待执行的任务或其结果。
 * 如下所示，还提供了支持特定完成遍历的工具方法。
 * 但是，由于CountedCompleters仅提供基本的同步机制，因此创建进一步的抽象子类（维护链接，字段和适用于一组相关用法的其他支持方法）可能是有用的。
 *
 * <p>A concrete CountedCompleter class must define method {@link
 * #compute}, that should in most cases (as illustrated below), invoke
 * {@code tryComplete()} once before returning. The class may also
 * optionally override method {@link #onCompletion(CountedCompleter)}
 * to perform an action upon normal completion, and method
 * {@link #onExceptionalCompletion(Throwable, CountedCompleter)} to
 * perform an action upon any exception.
 * 一个具体的CountedCompleter类必须定义方法compute（），该方法在大多数情况下（如下所示）应在返回之前调用一次tryComplete（）。
 * 该类还可以选择重写onCompletion（CountedCompleter）方法以在正常完成时执行操作，并重写onExceptionalCompletion（Throwable，CountedCompleter）方法以在任何异常时执行操作。
 *
 * <p>CountedCompleters most often do not bear results, in which case
 * they are normally declared as {@code CountedCompleter<Void>}, and
 * will always return {@code null} as a result value.  In other cases,
 * you should override method {@link #getRawResult} to provide a
 * result from {@code join(), invoke()}, and related methods.  In
 * general, this method should return the value of a field (or a
 * function of one or more fields) of the CountedCompleter object that
 * holds the result upon completion. Method {@link #setRawResult} by
 * default plays no role in CountedCompleters.  It is possible, but
 * rarely applicable, to override this method to maintain other
 * objects or fields holding result data.
 * CountedCompleter通常不携带结果，在这种情况下，它们通常被声明为CountedCompleter<Void>，并且将始终返回null作为结果值。
 * 在其他情况下，应重写getRawResult（）方法以提供join（），invoke（）和相关方法的结果。
 * 通常，此方法应返回CountedCompleter对象的字段值（或一个或多个字段的函数），该值在完成时将保存结果。
 * 默认情况下，方法setRawResult（T）在CountedCompleters中不起作用。
 * 有可能但很少适用，可以重写此方法来维护其他保存结果数据的对象或字段。
 *
 * <p>A CountedCompleter that does not itself have a completer (i.e.,
 * one for which {@link #getCompleter} returns {@code null}) can be
 * used as a regular ForkJoinTask with this added functionality.
 * However, any completer that in turn has another completer serves
 * only as an internal helper for other computations, so its own task
 * status (as reported in methods such as {@link ForkJoinTask#isDone})
 * is arbitrary; this status changes only upon explicit invocations of
 * {@link #complete}, {@link ForkJoinTask#cancel},
 * {@link ForkJoinTask#completeExceptionally(Throwable)} or upon
 * exceptional completion of method {@code compute}. Upon any
 * exceptional completion, the exception may be relayed to a task's
 * completer (and its completer, and so on), if one exists and it has
 * not otherwise already completed. Similarly, cancelling an internal
 * CountedCompleter has only a local effect on that completer, so is
 * not often useful.
 * 自己没有completer 的CountedCompleter（即getCompleter（）返回null的completer ）可以用作具有此添加功能的常规ForkJoinTask。
 * 但是，任何具有另一个completer 的completer 仅充当其他计算的内部帮助者，因此它自己的任务状态（如ForkJoinTask.isDone（）之类的方法中所述）是任意的；
 * 仅当显式调用 complete(T), ForkJoinTask.cancel(boolean), ForkJoinTask.completeExceptionally(Throwable) 或方法compute异常完成时，此状态才会更改。
 * 一旦有任何出现异常的completion，如果存在completer 并且该completer 尚未完成，则可以将异常中继给任务的completer （及其completer ，依此类推）。
 * 同样，取消内部CountedCompleter对该completer 仅具有局部影响，因此通常没有用。
 *
 *
 * <p><b>Sample Usages.</b>
 *
 * <p><b>Parallel recursive decomposition.</b> CountedCompleters may
 * be arranged in trees similar to those often used with {@link
 * RecursiveAction}s, although the constructions involved in setting
 * them up typically vary. Here, the completer of each task is its
 * parent in the computation tree. Even though they entail a bit more
 * bookkeeping, CountedCompleters may be better choices when applying
 * a possibly time-consuming operation (that cannot be further
 * subdivided) to each element of an array or collection; especially
 * when the operation takes a significantly different amount of time
 * to complete for some elements than others, either because of
 * intrinsic variation (for example I/O) or auxiliary effects such as
 * garbage collection.  Because CountedCompleters provide their own
 * continuations, other threads need not block waiting to perform
 * them.
 * 并行递归分解。
 * CountedCompleters可以被树状排队，该树类似于经常与RecursiveActions一起使用的树，尽管设置它们所涉及的构造通常会有所不同。
 * 在这里，每个任务的completer 是其在计算树中的父项。
 * 即使需要更多记录，但在将可能耗时的操作（无法进一步细分）应用于数组或集合的每个元素时，CountedCompleters可能是更好的选择；
 * 特别是当某些元素与其他元素完成操作所需的时间明显不同时，这可能是由于固有的变化（例如I / O）或诸如垃圾回收之类的辅助作用所致。
 * 因为CountedCompleters提供了自己的延续，所以其他线程不需要因等待执行它们而阻塞。
 *
 * <p>For example, here is an initial version of a class that uses
 * divide-by-two recursive decomposition to divide work into single
 * pieces (leaf tasks). Even when work is split into individual calls,
 * tree-based techniques are usually preferable to directly forking
 * leaf tasks, because they reduce inter-thread communication and
 * improve load balancing. In the recursive case, the second of each
 * pair of subtasks to finish triggers completion of its parent
 * (because no result combination is performed, the default no-op
 * implementation of method {@code onCompletion} is not overridden).
 * A static utility method sets up the base task and invokes it
 * (here, implicitly using the {@link ForkJoinPool#commonPool()}).
 * 例如，这儿是该类的初始版本，该类使用“二分法”递归分解将工作划分为多个部分（叶任务）。
 * 即使将工作分成多个单独的调用，基于树的技术通常也比直接分支叶任务更可取，因为它们减少了线程间的通信并改善了负载平衡。
 * 在递归的情况下，每对子任务中的第二个要完成的任务触发其父对象的完成（因为未执行任何结果组合，因此不会覆盖方法onCompletion的默认无操作实现）。
 * 静态实用程序方法设置并调用基本任务（这里 隐式地使用了ForkJoinPool.commonPool（））
 *
 * <pre> {@code
 * class MyOperation<E> { void apply(E e) { ... }  }
 *
 * class ForEach<E> extends CountedCompleter<Void> {
 *
 *   public static <E> void forEach(E[] array, MyOperation<E> op) {
 *     new ForEach<E>(null, array, op, 0, array.length).invoke();
 *   }
 *
 *   final E[] array; final MyOperation<E> op; final int lo, hi;
 *   ForEach(CountedCompleter<?> p, E[] array, MyOperation<E> op, int lo, int hi) {
 *     super(p);
 *     this.array = array; this.op = op; this.lo = lo; this.hi = hi;
 *   }
 *
 *   public void compute() { // version 1
 *     if (hi - lo >= 2) {
 *       int mid = (lo + hi) >>> 1;
 *       setPendingCount(2); // must set pending count before fork
 *       new ForEach(this, array, op, mid, hi).fork(); // right child
 *       new ForEach(this, array, op, lo, mid).fork(); // left child
 *     }
 *     else if (hi > lo)
 *       op.apply(array[lo]);
 *     tryComplete();
 *   }
 * }}</pre>
 *
 * This design can be improved by noticing that in the recursive case,
 * the task has nothing to do after forking its right task, so can
 * directly invoke its left task before returning. (This is an analog
 * of tail recursion removal.)  Also, because the task returns upon
 * executing its left task (rather than falling through to invoke
 * {@code tryComplete}) the pending count is set to one:
 * 可以注意到在递归情况下，该任务在派生其右任务后无需执行任何操作，因此可以在返回之前直接调用其左任务，从而改进此设计。
 * （这类似于尾部递归移除。）
 * 而且，由于任务是在执行其左任务时返回的（而不是失败地调用tryComplete），因此未决计数设置为1：
 *
 * <pre> {@code
 * class ForEach<E> ...
 *   public void compute() { // version 2
 *     if (hi - lo >= 2) {
 *       int mid = (lo + hi) >>> 1;
 *       setPendingCount(1); // only one pending
 *       new ForEach(this, array, op, mid, hi).fork(); // right child
 *       new ForEach(this, array, op, lo, mid).compute(); // direct invoke
 *     }
 *     else {
 *       if (hi > lo)
 *         op.apply(array[lo]);
 *       tryComplete();
 *     }
 *   }
 * }</pre>
 *
 * As a further improvement, notice that the left task need not even exist.
 * Instead of creating a new one, we can iterate using the original task,
 * and add a pending count for each fork.  Additionally, because no task
 * in this tree implements an {@link #onCompletion(CountedCompleter)} method,
 * {@code tryComplete()} can be replaced with {@link #propagateCompletion}.
 * 作为进一步的改进，注意到左任务甚至不需要存在。
 * 我们还可以使用原始任务进行迭代，并为每个fork添加一个未决计数，而不是创建一个新任务。
 * 此外，由于该树中没有任务实现onCompletion（CountedCompleter）方法，
 * 因此tryComplete（）可以替换为propertyCompletion（）。
 *
 * <pre> {@code
 * class ForEach<E> ...
 *   public void compute() { // version 3
 *     int l = lo,  h = hi;
 *     while (h - l >= 2) {
 *       int mid = (l + h) >>> 1;
 *       addToPendingCount(1);
 *       new ForEach(this, array, op, mid, h).fork(); // right child
 *       h = mid;
 *     }
 *     if (h > l)
 *       op.apply(array[l]);
 *     propagateCompletion();
 *   }
 * }</pre>
 *
 * Additional improvements of such classes might entail precomputing
 * pending counts so that they can be established in constructors,
 * specializing classes for leaf steps, subdividing by say, four,
 * instead of two per iteration, and using an adaptive threshold
 * instead of always subdividing down to single elements.
 * 此类的其他改进可能需要预先计算待处理的计数，以便可以在构造函数中建立它们，
 * 对叶子步骤进行专门化的类，每次迭代由分为4个而不是两个，并且使用自适应阈值而不是始终划分为单个元素 。
 *
 * <p><b>Searching.</b> A tree of CountedCompleters can search for a
 * value or property in different parts of a data structure, and
 * report a result in an {@link
 * juc.atomic.AtomicReference AtomicReference} as
 * soon as one is found. The others can poll the result to avoid
 * unnecessary work. (You could additionally {@linkplain #cancel
 * cancel} other tasks, but it is usually simpler and more efficient
 * to just let them notice that the result is set and if so skip
 * further processing.)  Illustrating again with an array using full
 * partitioning (again, in practice, leaf tasks will almost always
 * process more than one element):
 * <p><b>Searching.</b>
 * 一棵CountedCompleters树可以在数据结构的不同部分中搜索值或属性，并一旦找到后，可以在AtomicReference中报告结果。
 * 其他人可以轮询结果以避免不必要的工作。
 * （你可以另外取消其他任务，但是让他们注意到结果已经被设置了，然后跳过这一步，进行进一步的处理，这样通常更简单，更高效。）
 * 再次使用数组使用完整分区进行说明（实际上，叶任务也是几乎总是处理多个元素）
 *
 *
 * <pre> {@code
 * class Searcher<E> extends CountedCompleter<E> {
 *   final E[] array; final AtomicReference<E> result; final int lo, hi;
 *   Searcher(CountedCompleter<?> p, E[] array, AtomicReference<E> result, int lo, int hi) {
 *     super(p);
 *     this.array = array; this.result = result; this.lo = lo; this.hi = hi;
 *   }
 *   public E getRawResult() { return result.get(); }
 *   public void compute() { // similar to ForEach version 3
 *     int l = lo,  h = hi;
 *     while (result.get() == null && h >= l) {
 *       if (h - l >= 2) {
 *         int mid = (l + h) >>> 1;
 *         addToPendingCount(1);
 *         new Searcher(this, array, result, mid, h).fork();
 *         h = mid;
 *       }
 *       else {
 *         E x = array[l];
 *         if (matches(x) && result.compareAndSet(null, x))
 *           quietlyCompleteRoot(); // root task is now joinable
 *         break;
 *       }
 *     }
 *     tryComplete(); // normally complete whether or not found
 *   }
 *   boolean matches(E e) { ... } // return true if found
 *
 *   public static <E> E search(E[] array) {
 *       return new Searcher<E>(null, array, new AtomicReference<E>(), 0, array.length).invoke();
 *   }
 * }}</pre>
 *
 * In this example, as well as others in which tasks have no other
 * effects except to compareAndSet a common result, the trailing
 * unconditional invocation of {@code tryComplete} could be made
 * conditional ({@code if (result.get() == null) tryComplete();})
 * because no further bookkeeping is required to manage completions
 * once the root task completes.
 * 在此示例中，以及在其他任务中（这些任务除了compareAndSet共同的结果之外，没有其他效果）
 * 可以将{@code tryComplete}的尾随无条件调用设为有条件的 ({@code if (result.get() == null) tryComplete();})，因为根任务完成后，无需再进行记录来管理完成情况。
 *
 * <p><b>Recording subtasks.</b> CountedCompleter tasks that combine
 * results of multiple subtasks usually need to access these results
 * in method {@link #onCompletion(CountedCompleter)}. As illustrated in the following
 * class (that performs a simplified form of map-reduce where mappings
 * and reductions are all of type {@code E}), one way to do this in
 * divide and conquer designs is to have each subtask record its
 * sibling, so that it can be accessed in method {@code onCompletion}.
 * This technique applies to reductions in which the order of
 * combining left and right results does not matter; ordered
 * reductions require explicit left/right designations.  Variants of
 * other streamlinings seen in the above examples may also apply.
 * <p><b>Recording subtasks.</b> 。结合了多个子任务的结果的CountedCompleter任务通常需要在方法onCompletion（CountedCompleter）中访问这些结果。
 * 如下面的类所示（执行简化的map-reduce形式，其中映射和归约均为E型），
 * 在分而治之设计中实现此目的的一种方法是让每个子任务记录其兄弟，以便可以通过onCompletion方法进行访问。
 * 这项技术适用于左右合并结果的顺序无关紧要的归约。
 * 有序归约需要明确的左/右指定。
 * 在以上示例中看到的其他精简版本也可能适用。
 *
 * <pre> {@code
 * class MyMapper<E> { E apply(E v) {  ...  } }
 * class MyReducer<E> { E apply(E x, E y) {  ...  } }
 * class MapReducer<E> extends CountedCompleter<E> {
 *   final E[] array; final MyMapper<E> mapper;
 *   final MyReducer<E> reducer; final int lo, hi;
 *   MapReducer<E> sibling;
 *   E result;
 *   MapReducer(CountedCompleter<?> p, E[] array, MyMapper<E> mapper,
 *              MyReducer<E> reducer, int lo, int hi) {
 *     super(p);
 *     this.array = array; this.mapper = mapper;
 *     this.reducer = reducer; this.lo = lo; this.hi = hi;
 *   }
 *   public void compute() {
 *     if (hi - lo >= 2) {
 *       int mid = (lo + hi) >>> 1;
 *       MapReducer<E> left = new MapReducer(this, array, mapper, reducer, lo, mid);
 *       MapReducer<E> right = new MapReducer(this, array, mapper, reducer, mid, hi);
 *       left.sibling = right;
 *       right.sibling = left;
 *       setPendingCount(1); // only right is pending
 *       right.fork();
 *       left.compute();     // directly execute left
 *     }
 *     else {
 *       if (hi > lo)
 *           result = mapper.apply(array[lo]);
 *       tryComplete();
 *     }
 *   }
 *   public void onCompletion(CountedCompleter<?> caller) {
 *     if (caller != this) {
 *       MapReducer<E> child = (MapReducer<E>)caller;
 *       MapReducer<E> sib = child.sibling;
 *       if (sib == null || sib.result == null)
 *         result = child.result;
 *       else
 *         result = reducer.apply(child.result, sib.result);
 *     }
 *   }
 *   public E getRawResult() { return result; }
 *
 *   public static <E> E mapReduce(E[] array, MyMapper<E> mapper, MyReducer<E> reducer) {
 *     return new MapReducer<E>(null, array, mapper, reducer,
 *                              0, array.length).invoke();
 *   }
 * }}</pre>
 *
 * Here, method {@code onCompletion} takes a form common to many
 * completion designs that combine results. This callback-style method
 * is triggered once per task, in either of the two different contexts
 * in which the pending count is, or becomes, zero: (1) by a task
 * itself, if its pending count is zero upon invocation of {@code
 * tryComplete}, or (2) by any of its subtasks when they complete and
 * decrement the pending count to zero. The {@code caller} argument
 * distinguishes cases.  Most often, when the caller is {@code this},
 * no action is necessary. Otherwise the caller argument can be used
 * (usually via a cast) to supply a value (and/or links to other
 * values) to be combined.  Assuming proper use of pending counts, the
 * actions inside {@code onCompletion} occur (once) upon completion of
 * a task and its subtasks. No additional synchronization is required
 * within this method to ensure thread safety of accesses to fields of
 * this task or other completed tasks.
 * 在这里，onCompletion方法采用许多合并结果的完成设计所共有的形式。
 * 在两个不同的上下文的一个里，其中未决计数为零或变为零， 此回调样式方法都会被每个任务触发一次：
 * （1）由任务本身触发（如果在调用tryComplete时其未决计数为零），或者
 * （2）由他的任何一个子任务触发，当这些子任务完成并将未决计数减为零时。
 *      调用方参数区分大小写。通常，当调用者是{@code this}时，无需采取任何措施。
 *      否则，调用者参数被用（通常通过强制转换）来提供要组合的值（和/或指向其他值的链接）。
 *      假设正确使用未决计数，则onCompletion内部的操作会在任务及其子任务完成时发生（一次）。
 *      在此方法内，不需要其他同步来确保对该任务或其他已完成任务的字段进行线程安全访问。
 *
 * <p><b>Completion Traversals</b>. If using {@code onCompletion} to
 * process completions is inapplicable or inconvenient, you can use
 * methods {@link #firstComplete} and {@link #nextComplete} to create
 * custom traversals.  For example, to define a MapReducer that only
 * splits out right-hand tasks in the form of the third ForEach
 * example, the completions must cooperatively reduce along
 * unexhausted subtask links, which can be done as follows:
 * 完成遍历。
 * 如果使用onCompletion来处理完成是不适用的或不便的话，则可以使用firstComplete（）和nextComplete（）方法创建自定义遍历。
 * 例如，要定义仅以第三个ForEach示例的形式拆分right-hand任务的MapReducer，completions必须沿着未耗尽的子任务链接协作地归约，这可以按照以下步骤进行：
 *
 *
 * <pre> {@code
 * class MapReducer<E> extends CountedCompleter<E> { // version 2
 *   final E[] array; final MyMapper<E> mapper;
 *   final MyReducer<E> reducer; final int lo, hi;
 *   MapReducer<E> forks, next; // record subtask forks in list
 *   E result;
 *   MapReducer(CountedCompleter<?> p, E[] array, MyMapper<E> mapper,
 *              MyReducer<E> reducer, int lo, int hi, MapReducer<E> next) {
 *     super(p);
 *     this.array = array; this.mapper = mapper;
 *     this.reducer = reducer; this.lo = lo; this.hi = hi;
 *     this.next = next;
 *   }
 *   public void compute() {
 *     int l = lo,  h = hi;
 *     while (h - l >= 2) {
 *       int mid = (l + h) >>> 1;
 *       addToPendingCount(1);
 *       (forks = new MapReducer(this, array, mapper, reducer, mid, h, forks)).fork();
 *       h = mid;
 *     }
 *     if (h > l)
 *       result = mapper.apply(array[l]);
 *     // process completions by reducing along and advancing subtask links
 *     for (CountedCompleter<?> c = firstComplete(); c != null; c = c.nextComplete()) {
 *       for (MapReducer t = (MapReducer)c, s = t.forks;  s != null; s = t.forks = s.next)
 *         t.result = reducer.apply(t.result, s.result);
 *     }
 *   }
 *   public E getRawResult() { return result; }
 *
 *   public static <E> E mapReduce(E[] array, MyMapper<E> mapper, MyReducer<E> reducer) {
 *     return new MapReducer<E>(null, array, mapper, reducer,
 *                              0, array.length, null).invoke();
 *   }
 * }}</pre>
 *
 * <p><b>Triggers.</b> Some CountedCompleters are themselves never
 * forked, but instead serve as bits of plumbing in other designs;
 * including those in which the completion of one or more async tasks
 * triggers another async task. For example:
 * 触发器。 有些CountedCompleters本身从不分派（forked），而是在其他设计中充当管道的一部分: 包括那些一个或多个异步任务会触发另一个异步任务的completion。
 * 例如：
 *
 * <pre> {@code
 * class HeaderBuilder extends CountedCompleter<...> { ... }
 * class BodyBuilder extends CountedCompleter<...> { ... }
 * class PacketSender extends CountedCompleter<...> {
 *   PacketSender(...) { super(null, 1); ... } // trigger on second completion
 *   public void compute() { } // never called
 *   public void onCompletion(CountedCompleter<?> caller) { sendPacket(); }
 * }
 * // sample use:
 * PacketSender p = new PacketSender();
 * new HeaderBuilder(p, ...).fork();
 * new BodyBuilder(p, ...).fork();
 * }</pre>
 *
 * @since 1.8
 * @author Doug Lea
 */
public abstract class CountedCompleter<T> extends ForkJoinTask<T> {
    private static final long serialVersionUID = 5232453752276485070L;

    /**
     * This task's completer, or null if none
     * 指向父节点Completer，形成的是一棵树，先完成子节点，才能完成父节点。
     * */
    final CountedCompleter<?> completer;
    /** The number of pending tasks until completion */
    volatile int pending;

    /**
     * Creates a new CountedCompleter with the given completer
     * and initial pending count.
     *
     * @param completer this task's completer, or {@code null} if none
     * @param initialPendingCount the initial pending count
     */
    protected CountedCompleter(CountedCompleter<?> completer,
                               int initialPendingCount) {
        this.completer = completer;
        this.pending = initialPendingCount;
    }

    /**
     * Creates a new CountedCompleter with the given completer
     * and an initial pending count of zero.
     *
     * @param completer this task's completer, or {@code null} if none
     */
    protected CountedCompleter(CountedCompleter<?> completer) {
        this.completer = completer;
    }

    /**
     * Creates a new CountedCompleter with no completer
     * and an initial pending count of zero.
     */
    protected CountedCompleter() {
        this.completer = null;
    }

    /**
     * The main computation performed by this task.
     */
    public abstract void compute();

    /**
     * Performs an action when method {@link #tryComplete} is invoked
     * and the pending count is zero, or when the unconditional
     * method {@link #complete} is invoked.  By default, this method
     * does nothing. You can distinguish cases by checking the
     * identity of the given caller argument. If not equal to {@code
     * this}, then it is typically a subtask that may contain results
     * (and/or links to other results) to combine.
     * 在调用方法{@link #tryComplete} 且未决计数为零时，或在调用无条件方法{@link #complete}时，执行一个操作。
     * 默认情况下，此方法不执行任何操作。
     * 你可以通过检查给定调用方参数的身份来区分情况。
     * 如果不相等{@code this}，则通常是一个子任务，该子任务可能包含要组合的结果（和/或指向其他结果的链接）。
     *
     * @param caller the task invoking this method (which may
     * be this task itself)
     */
    public void onCompletion(CountedCompleter<?> caller) {
    }

    /**
     * Performs an action when method {@link
     * #completeExceptionally(Throwable)} is invoked or method {@link
     * #compute} throws an exception, and this task has not already
     * otherwise completed normally. On entry to this method, this task
     * {@link ForkJoinTask#isCompletedAbnormally}.  The return value
     * of this method controls further propagation: If {@code true}
     * and this task has a completer that has not completed, then that
     * completer is also completed exceptionally, with the same
     * exception as this completer.  The default implementation of
     * this method does nothing except return {@code true}.
     * 当调用方法ForkJoinTask.completeExceptionally（Throwable）或方法compute（）引发异常，并且此任务尚未正常完成时，执行一个操作。
     * 在进入此方法时，此任务ForkJoinTask.isCompletedAbnormally（）。
     * 此方法的返回值控制进一步的传播：如果为true且此任务有一个尚未完成的completer，则该completer也会异常完成，带有与此completer相同的异常。
     * 此方法的默认实现除了返回true以外不执行任何操作。
     *
     * @param ex the exception
     * @param caller the task invoking this method (which may
     * be this task itself)
     * @return {@code true} if this exception should be propagated to this
     * task's completer, if one exists
     */
    public boolean onExceptionalCompletion(Throwable ex, CountedCompleter<?> caller) {
        return true;
    }

    /**
     * Returns the completer established in this task's constructor,
     * or {@code null} if none.
     * 返回在此任务的构造函数中建立的completer；如果没有，则返回null。
     *
     * @return the completer
     */
    public final CountedCompleter<?> getCompleter() {
        return completer;
    }

    /**
     * Returns the current pending count.
     * 返回当前未决计数。
     * @return the current pending count
     */
    public final int getPendingCount() {
        return pending;
    }

    /**
     * Sets the pending count to the given value.
     *
     * @param count the count
     */
    public final void setPendingCount(int count) {
        pending = count;
    }

    /**
     * Adds (atomically) the given value to the pending count.
     *
     * @param delta the value to add
     */
    public final void addToPendingCount(int delta) {
        U.getAndAddInt(this, PENDING, delta);
    }

    /**
     * Sets (atomically) the pending count to the given count only if
     * it currently holds the given expected value.
     *
     * @param expected the expected value
     * @param count the new value
     * @return {@code true} if successful
     */
    public final boolean compareAndSetPendingCount(int expected, int count) {
        return U.compareAndSwapInt(this, PENDING, expected, count);
    }

    /**
     * If the pending count is nonzero, (atomically) decrements it.
     *
     * @return the initial (undecremented) pending count holding on entry
     * to this method
     */
    public final int decrementPendingCountUnlessZero() {
        int c;
        do {} while ((c = pending) != 0 &&
                     !U.compareAndSwapInt(this, PENDING, c, c - 1));
        return c;
    }

    /**
     * Returns the root of the current computation; i.e., this
     * task if it has no completer, else its completer's root.
     *
     * @return the root of the current computation
     */
    public final CountedCompleter<?> getRoot() {
        CountedCompleter<?> a = this, p;
        while ((p = a.completer) != null)
            a = p;
        return a;
    }

    /**
     * If the pending count is nonzero, decrements the count;
     * otherwise invokes {@link #onCompletion(CountedCompleter)}
     * and then similarly tries to complete this task's completer,
     * if one exists, else marks this task as complete.
     * 如果未决计数为非零，则减少计数；
     * 否则（计数为0），调用{@link #onCompletion(CountedCompleter)}，
     * 然后类似地尝试完成此任务的完成者（如果存在），否则将其标记为完成。
     */
    public final void tryComplete() {
        CountedCompleter<?> a = this, s = a;
        for (int c;;) {
            if ((c = a.pending) == 0) {
                a.onCompletion(s);
                if ((a = (s = a).completer) == null) {
                    s.quietlyComplete();
                    return;
                }
            }
            else if (U.compareAndSwapInt(a, PENDING, c, c - 1))
                return;
        }
    }

    /**
     * Equivalent to {@link #tryComplete} but does not invoke {@link
     * #onCompletion(CountedCompleter)} along the completion path:
     * If the pending count is nonzero, decrements the count;
     * otherwise, similarly tries to complete this task's completer, if
     * one exists, else marks this task as complete. This method may be
     * useful in cases where {@code onCompletion} should not, or need
     * not, be invoked for each completer in a computation.
     * 与 {@link #tryComplete}等效，但不会沿完成路径调用{@link #onCompletion(CountedCompleter)}：
     * 如果未决计数为非零，则减少该计数；
     * 否则（未决计数为0），类似地尝试完成此任务的父completer（如果存在），否则将此任务标记为完成。
     * 在不应或不需要为计算中的每个完成程序调用onCompletion的情况下，此方法可能很有用。
     */
    public final void propagateCompletion() {
        CountedCompleter<?> a = this, s = a;
        for (int c;;) {
            if ((c = a.pending) == 0) {
//                a.onCompletion(s); 相比tryComplete，只少了这一句
                if ((a = (s = a).completer) == null) {
                    s.quietlyComplete();
                    return;
                }
            }
            else if (U.compareAndSwapInt(a, PENDING, c, c - 1))
                return;
        }
    }

    /**
     * Regardless of pending count, invokes
     * {@link #onCompletion(CountedCompleter)}, marks this task as
     * complete and further triggers {@link #tryComplete} on this
     * task's completer, if one exists.  The given rawResult is
     * used as an argument to {@link #setRawResult} before invoking
     * {@link #onCompletion(CountedCompleter)} or marking this task
     * as complete; its value is meaningful only for classes
     * overriding {@code setRawResult}.  This method does not modify
     * the pending count.
     * 不管未决计数如何，都调用 {@link #onCompletion(CountedCompleter)}，将此任务标记为已完成，
     * 然后在此任务的父Completer（如果存在）上进一步触发{@link #tryComplete}。
     * 在调用{@link #onCompletion(CountedCompleter)} 或将此任务标记为完成之前， 给定的rawResult被用作{@link #setRawResult}的参数：
     * 它的值仅对覆盖setRawResult的类有意义。
     * 此方法不会修改未决计数。
     *
     * <p>This method may be useful when forcing completion as soon as
     * any one (versus all) of several subtask results are obtained.
     * However, in the common (and recommended) case in which {@code
     * setRawResult} is not overridden, this effect can be obtained
     * more simply using {@code quietlyCompleteRoot();}.
     * 一旦获得多个子任务结果中的任何一个（相对于全部），就强制完成时，这种情况下此方法可能会很有用。
     * 但是，在不重写setRawResult的常见（推荐）情况下，此效果可以使用{@code quietlyCompleteRoot();}更简单地获得。
     *
     * @param rawResult the raw result
     */
    public void complete(T rawResult) {
        CountedCompleter<?> p;
        setRawResult(rawResult);
        onCompletion(this);
        quietlyComplete();
        if ((p = completer) != null)
            p.tryComplete();
    }

    /**
     * If this task's pending count is zero, returns this task;
     * otherwise decrements its pending count and returns {@code
     * null}. This method is designed to be used with {@link
     * #nextComplete} in completion traversal loops.
     * 如果此任务的未决计数为零，则返回此任务；否则，减少其暂挂计数并返回null。
     * 此方法设计为在完成遍历循环中与nextComplete（）一起使用。
     *
     * @return this task, if pending count was zero, else {@code null}
     */
    public final CountedCompleter<?> firstComplete() {
        for (int c;;) {
            if ((c = pending) == 0)
                return this;
            else if (U.compareAndSwapInt(this, PENDING, c, c - 1))
                return null;
        }
    }

    /**
     * If this task does not have a completer, invokes {@link
     * ForkJoinTask#quietlyComplete} and returns {@code null}.  Or, if
     * the completer's pending count is non-zero, decrements that
     * pending count and returns {@code null}.  Otherwise, returns the
     * completer.  This method can be used as part of a completion
     * traversal loop for homogeneous task hierarchies:
     * 如果此任务没有一个完成者，则调用{@link ForkJoinTask#quietlyComplete}并返回null。
     * 或者，如果完成者的未决计数为非零，则减少该未决计数并返回null。
     * 否则，返回完成者。
     * 此方法可用作同类任务层次结构的完成遍历循环的一部分：
     *
     * <pre> {@code
     * for (CountedCompleter<?> c = firstComplete();
     *      c != null;
     *      c = c.nextComplete()) {
     *   // ... process c ...
     * }}</pre>
     *
     * @return the completer, or {@code null} if none
     */
    public final CountedCompleter<?> nextComplete() {
        CountedCompleter<?> p;
        if ((p = completer) != null)
            return p.firstComplete();
        else {
            quietlyComplete();
            return null;
        }
    }

    /**
     * Equivalent to {@code getRoot().quietlyComplete()}.
     */
    public final void quietlyCompleteRoot() {
        for (CountedCompleter<?> a = this, p;;) {
            if ((p = a.completer) == null) {
                a.quietlyComplete();
                return;
            }
            a = p;
        }
    }

    /**
     * If this task has not completed, attempts to process at most the
     * given number of other unprocessed tasks for which this task is
     * on the completion path, if any are known to exist.
     * 如果此任务尚未完成，则尝试最多处理给定数量的此任务在完成路径上的其他未处理任务（如果已知存在）。
     *
     * @param maxTasks the maximum number of tasks to process.  If
     *                 less than or equal to zero, then no tasks are
     *                 processed.
     */
    public final void helpComplete(int maxTasks) {
        Thread t; ForkJoinWorkerThread wt;
        if (maxTasks > 0 && status >= 0) {
            //todo 重要
            if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread)
                (wt = (ForkJoinWorkerThread)t).pool.
                    helpComplete(wt.workQueue, this, maxTasks);
            else
                ForkJoinPool.common.externalHelpComplete(this, maxTasks);
        }
    }

    /**
     * Supports ForkJoinTask exception propagation.
     */
    void internalPropagateException(Throwable ex) {
        CountedCompleter<?> a = this, s = a;
        while (a.onExceptionalCompletion(ex, s) &&
               (a = (s = a).completer) != null && a.status >= 0 &&
               a.recordExceptionalCompletion(ex) == EXCEPTIONAL)
            ;
    }

    /**
     * Implements execution conventions for CountedCompleters.
     * 实现CountedCompleters的执行约定。
     */
    protected final boolean exec() {
        compute();
        return false;
    }

    /**
     * Returns the result of the computation. By default
     * returns {@code null}, which is appropriate for {@code Void}
     * actions, but in other cases should be overridden, almost
     * always to return a field or function of a field that
     * holds the result upon completion.
     * 返回计算结果。
     * 默认情况下，返回null，这适用于Void动作，但是在其他情况下，应该覆盖此方法，几乎总是返回一个字段或字段的函数，该字段或函数在完成时保存结果。
     *
     * @return the result of the computation
     */
    public T getRawResult() { return null; }

    /**
     * A method that result-bearing CountedCompleters may optionally
     * use to help maintain result data.  By default, does nothing.
     * Overrides are not recommended. However, if this method is
     * overridden to update existing objects or fields, then it must
     * in general be defined to be thread-safe.
     * 带有结果的CountedCompleters的方法可以选择用来帮助维护结果数据。
     * 默认情况下，不执行任何操作。
     * 不建议覆盖。 但是，如果重写此方法以更新现有的对象或字段，则通常必须将其定义为线程安全的。
     */
    protected void setRawResult(T t) { }

    // Unsafe mechanics
    private static final sun.misc.Unsafe U;
    private static final long PENDING;
    static {
        try {
            U = GetUnsafeFromReflect.getUnsafe();
            PENDING = U.objectFieldOffset
                (CountedCompleter.class.getDeclaredField("pending"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
