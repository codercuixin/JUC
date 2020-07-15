

package juc;



/**
 * 一种服务，该服务将新异步任务的生产与已完成任务的结果的消耗分开。
 * 生产者提交任务以执行。消费者获取已完成的任务，并按完成顺序处理结果。
 * 例如，CompletionService可以用于管理异步I / O，在该异步I / O中，执行读取的任务在程序或系统的一部分中提交，
 * 然后在读取完成时在程序的不同部分中执行操作，可能是在与提交的顺序不同。
 * 通常，CompletionService依靠单独的Executor实际执行任务，在这种情况下，CompletionService仅管理内部完成队列。
 * ExecutorCompletionService类提供了此方法的实现。
 *
 * 内存一致性影响：在将任务提交给CompletionService之前的线程中操作，happens-before于在该任务执行的操作，
 * 而该任务执行的操作又happens-before于相应的take（）成功返回之后的操作。
 * todo 3
 */
public interface CompletionService<V> {
    /**
     * Submits a value-returning task for execution and returns a Future
     * representing the pending results of the task.  Upon completion,
     * this task may be taken or polled.
     *
     * @param task the task to submit
     * @return a Future representing pending completion of the task
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     * @throws NullPointerException if the task is null
     */
    Future<V> submit(Callable<V> task);

    /**
     * Submits a Runnable task for execution and returns a Future
     * representing that task.  Upon completion, this task may be
     * taken or polled.
     *
     * @param task the task to submit
     * @param result the result to return upon successful completion
     * @return a Future representing pending completion of the task,
     *         and whose {@code get()} method will return the given
     *         result value upon completion
     * @throws RejectedExecutionException if the task cannot be
     *         scheduled for execution
     * @throws NullPointerException if the task is null
     */
    Future<V> submit(Runnable task, V result);

    /**
     * Retrieves and removes the Future representing the next
     * completed task, waiting if none are yet present.
     *
     * @return the Future representing the next completed task
     * @throws InterruptedException if interrupted while waiting
     */
    Future<V> take() throws InterruptedException;

    /**
     * Retrieves and removes the Future representing the next
     * completed task, or {@code null} if none are present.
     *
     * @return the Future representing the next completed task, or
     *         {@code null} if none are present
     */
    Future<V> poll();

    /**
     * Retrieves and removes the Future representing the next
     * completed task, waiting if necessary up to the specified wait
     * time if none are yet present.
     *
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return the Future representing the next completed task or
     *         {@code null} if the specified waiting time elapses
     *         before one is present
     * @throws InterruptedException if interrupted while waiting
     */
    Future<V> poll(long timeout, TimeUnit unit) throws InterruptedException;
}
