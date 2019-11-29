package juc.locks;
import java.util.concurrent.TimeUnit;
import java.util.Date;
 
/**
 * Condition（条件）将对象monitor方法(wait、notify和notifyAll)分解到不同的对象中，
 * 通过将它们与任意锁实现结合使用，实现每个对象具有多个等待集的效果。
 * 锁代替synchronized方法和synchronized语句的使用，Condition代替对象monitor方法的使用。
 
 
 * Conditons(也称为条件队列或条件变量)提供了一种方法，让一个线程暂停执行并等待，直到另一个线程通知某个状态条件现在可能为真。
 * 因为对共享状态信息的访问发生在不同的线程中，所以必须保护它，所以某种形式的锁与该condition相关联。
 * 等待条件提供的关键属性是它自动释放关联的锁并挂起当前线程，就像Object.wait一样。
 
 * 一个Condition实例本质上绑定到一个锁上。
 * 要获取特定锁实例的Condition实例，请使用其newCondition()方法。
 
 * 例如，假设我们有一个有界的缓冲区，它支持put和take方法。
 * 如果尝试在空缓冲区上执行take操作，则线程将阻塞，直到某项可用为止;
 * 如果在一个满了的缓冲区上尝试put，那么线程将阻塞，直到空间可用为止。
 * 我们希望将put线程和take线程放在不同的等待集中，这样我们就可以进行优化，即在缓冲区中的某项或空间可用时只通知单个线程。
 * 这可以通过使用两个条件实例来实现。
 * <pre>
 * class BoundedBuffer {
 *   <b>final Lock lock = new ReentrantLock();</b>
 *   final Condition notFull  = <b>lock.newCondition(); </b>
 *   final Condition notEmpty = <b>lock.newCondition(); </b>
 *
 *   final Object[] items = new Object[100];
 *   int putptr, takeptr, count;
 *
 *   public void put(Object x) throws InterruptedException {
 *     <b>lock.lock();
 *     try {</b>
 *       while (count == items.length)
 *         <b>notFull.await();</b>
 *       items[putptr] = x;
 *       if (++putptr == items.length) putptr = 0;
 *       ++count;
 *       <b>notEmpty.signal();</b>
 *     <b>} finally {
 *       lock.unlock();
 *     }</b>
 *   }
 *
 *   public Object take() throws InterruptedException {
 *     <b>lock.lock();
 *     try {</b>
 *       while (count == 0)
 *         <b>notEmpty.await();</b>
 *       Object x = items[takeptr];
 *       if (++takeptr == items.length) takeptr = 0;
 *       --count;
 *       <b>notFull.signal();</b>
 *       return x;
 *     <b>} finally {
 *       lock.unlock();
 *     }</b>
 *   }
 * }
 * </pre>
 *
 * （ArrayBlockingQueue类提供了此功能，因此没有理由实现此示例用法类。）
 
 
 *
 *<p> 一个Condition实现可以提供与Object monitor方法不同的行为和语义，
 * 例如，保证通知的顺序，或者在执行通知时不需要锁定。
 * 如果实现提供了这种特殊的语义，则实现必须记录这些语义。
 
 *<p> 请注意，Condition实例只是普通对象，它们本身可以用作同步语句中的目标，并且可以调用自己moitor的 wait和notify方法。
 * 获取Condition实例的monitor锁, 或使用其monitor方法, 它们与获取与该Condition相关联的锁或使用其wait和sinal方法没有指定的关系。
 * 为了避免混淆，除非可能在自己的实现中，否则建议不要以这种方式使用Condition实例。（也就是不要使用Condition对象的notify和wait方法）
 
 * 除非另有说明，否则为任何参数传递null值都会导致引发NullPointerException。
 *
 * 实现注意事项
 *
 * 当等待条件时，“虚假唤醒”一般会允许发生，作为对底层平台语义的让步。
 * 这对大多数应用程序几乎没有实际影响，因为应该始终在循环中等待一个Condition，测试正在等待的状态谓词。
 * 一个实现可以自由地消除虚假唤醒的可能性，但是建议应用程序程序员始终假定它们会发生，因此总是在循环中等待。
 *
 * 条件等待的三种形式（可中断，不可中断和定时）可能在某些平台上易于实现并且其性能特征也有所不同。
 * 特别是，可能很难提供这些功能并维护特定的语义，例如排序保证。
 * 此外，中断线程的实际挂起的能力可能并不总是在所有平台上都可行。
 *
 * 因此，不需要实现为所有三种等待形式定义完全相同的保证或语义，也不需要支持中断线程的实际挂起。
 *
 * 一个实现类需要清楚地记录每个等待方法提供的语义和保证，
 * 并且当一个实现类确实支持中断线程挂起时，则它必须服从此接口中定义的中断语义。
 
 * 由于中断通常意味着取消，并且通常不经常进行中断检查，
 * 因此与正常方法返回相比，实现类可能更倾向于对中断做出响应。
 * 即使可以证明中断是在另一个可能解除线程阻塞的操作之后发生的，也是如此。实现类应记录此行为。
 *
 * @since 1.5
 * @author Doug Lea
 */
public interface Condition {
    /**
     *使当前线程等待，直到发出信号或被中断为止。
     *
     * <p>与此条件相关联的锁被原子释放，并且出于线程调度目的，当前线程被禁用，
     * 并且处于休眠状态，直到发生以下四种情况之一：
     * <ul>
     * <li>其他一些线程调用此条件的signal（）方法，并且当前线程恰好被选择为要唤醒的线程；要么
     * <li>其他一些线程调用此条件的signalAll（）方法。要么
     * <li>其他一些线程使用Thread#interrupt中断了当前线程，并且线程挂起的中断是支持的；要么
     * <li>发生“虚假唤醒”。
     * </ul>
     *
     * <p>在所有情况下，在此方法可以返回之前，
     * 当前线程必须重新获取与此条件关联的锁。
     * 当线程返回时，可以保证持有此锁。
     *
     * <p>如果当前线程：
     * <ul>
     * <li> 在进入此方法时已设置其中断状态；要么
     * <li> 等待期间被中断（Thread#interrupt），并且线程挂起的中断是支持的
     * </ul>
     * 然后抛出InterruptedException并清除当前线程的中断状态。
     * 在第一种情况下，没有规定在释放锁之前是否进行了中断测试。
     *
     * <p><b>实现注意事项</b>
     *
     * 当调用此方法时，假定当前线程持有与此Condition关联的锁。
     * 由实现类来确定是否是这种情况，如果不是，该如何反应。
     * 典型的做法，一个异常（例如IllegalMonitorStateException）会被跑出来，并且实现类必须记录该事实。
     *
     * 与响应一个signal的通常方法返回相比，实现类可能更倾向于响应一个中断。
     * 在那种情况下，实现类必须确保将信号重定向到另一个等待线程（如果有一个的话）。
 
     * @throws：InterruptedException-如果当前线程被中断（并且支持线程挂起的中断）
     */
    void await() throws InterruptedException;
 
    /**
     * 使当前线程等待，直到发出信号或被中断为止。
     *
     * 与此条件相关联的锁被原子释放，并且出于线程调度目的，当前线程被禁用，
     * 并且处于休眠状态，直到发生以下三种情况之一：
     * <ul>
     * <li>其他一些线程调用此条件的signal（）方法，并且当前线程恰好被选择为要唤醒的线程；要么
     * <li>其他一些线程调用此条件的signalAll（）方法。要么
     * <li>发生“虚假唤醒”。
     * </ul>
     *
     * <p>在所有情况下，在此方法可以返回之前，
     * 当前线程必须重新获取与此条件关联的锁。
     * 当线程返回时，可以保证持有此锁。
     *
     * <p>如果当前线程：
     * <ul>
     * <li> 在进入此方法时已设置其中断状态；要么
     * <li> 等待期间被中断（Thread#interrupt），并且线程挂起的中断是支持的
     * </ul>
     * 然后抛出InterruptedException并清除当前线程的中断状态。
     * 在第一种情况下，没有规定在释放锁之前是否进行了中断测试。
     *
     * <p><b>实现注意事项</b>
     *
     * 当调用此方法时，假定当前线程持有与此Condition关联的锁。
     * 由实现类来确定是否是这种情况，如果不是，该如何反应。
     * 典型的做法，一个异常（例如IllegalMonitorStateException）会被跑出来，并且实现类必须记录该事实。
     *
     * 与响应一个signal的通常方法返回相比，实现类可能更倾向于响应一个中断。
     * 在那种情况下，实现类必须确保将信号重定向到另一个等待线程（如果有一个的话）。
 
     * @throws：InterruptedException-如果当前线程被中断（并且支持线程挂起的中断）
     */
    void awaitUninterruptibly();
 
    /**
     * 使当前线程等待，直到发出信号或中断它，或者经过指定的等待时间。
     *
     *<p>与此条件相关联的锁被原子释放，并且出于线程调度目的，当前线程被禁用，
     * 并且处于休眠状态，直到发生以下五种情况之一：
     * <ul>
     * <li>其他一些线程调用此条件的signal（）方法，并且当前线程恰好被选择为要唤醒的线程；要么
     * <li>其他一些线程调用此条件的signalAll（）方法。要么
     * <li>其他一些线程使用Thread#interrupt中断了当前线程，并且线程挂起的中断是支持的；要么
     * <li>指定的等待时间过去了；要么
     * <li>发生“虚假唤醒”。
     * </ul>
     *
     * <p>在所有情况下，在此方法可以返回之前，
     * 当前线程必须重新获取与此条件关联的锁。
     * 当线程返回时，可以保证持有此锁。
     *
     * <p>如果当前线程：
     * <ul>
     * <li> 在进入此方法时已设置其中断状态；要么
     * <li> 等待期间被中断（Thread#interrupt），并且线程挂起的中断是支持的
     * </ul>
     * 然后抛出InterruptedException并清除当前线程的中断状态。
     * 在第一种情况下，没有规定在释放锁之前是否进行了中断测试。
     *
     *
     * <p>基于提供的nanosTimeout参数，该方法将返回等待之后剩余纳秒数的估算值；
     * 如果超时，则返回小于或等于零的值，如果未超时，则返回正数。 
     * 在await方法返回但等待条件仍没有被满足的情况下，该值可用于确定是否重新等待以及等待多长时间。
     * 此方法的典型用法采用以下形式：
     *
     *  <pre> {@code
     * boolean aMethod(long timeout, TimeUnit unit) {
     *   long nanos = unit.toNanos(timeout);
     *   lock.lock();
     *   try {
     *     while (!conditionBeingWaitedFor()) {
     *       if (nanos <= 0L)
     *         return false;
     *       nanos = theCondition.awaitNanos(nanos);
     *     }
     *     // ...
     *   } finally {
     *     lock.unlock();
     *   }
     * }}</pre>
     *
     * <p>设计说明：此方法需要一个纳秒级的参数，以避免在报告剩余时间时出现截断错误。 
     * 当重新等待发生时，这样的精度损失将使程序员难以确保总的等待时间不会系统地短于指定的时间。
     *
     *
     * <p><b>实现注意事项</b>
     *
     * 当调用此方法时，假定当前线程持有与此Condition关联的锁。
     * 由实现类来确定是否是这种情况，如果不是，该如何反应。
     * 典型的做法，一个异常（例如IllegalMonitorStateException）会被跑出来，并且实现类必须记录该事实。
     *
     * 与响应一个signal的通常方法返回相比，实现类可能更倾向于响应一个中断。
     * 在那种情况下，实现类必须确保将信号重定向到另一个等待线程（如果有一个的话）。
     *
     * @param nanosTimeout 最大的等待时间，以纳秒为单位
     * @return 参数nanosTimeout值减去从此方法返回之前等待的时间的估计值。 
     *         如果返回值是一个正数的话，可以用作对该方法的后续调用的参数，以完成等待所需的时间。 
     *         如果返回值是一个小于或等于零的值，则表示没有时间剩余。
     * @throws：InterruptedException-如果当前线程被中断（并且支持线程挂起的中断）
     */
    long awaitNanos(long nanosTimeout) throws InterruptedException;
 
    /**
     * 使当前线程等待，直到发出信号或中断它，或者经过指定的等待时间。
     * 这个方法等价于:
     *  <pre> {@code awaitNanos(unit.toNanos(time)) > 0}</pre>
     *
     * @param time 最大的等待时间
     * @param unit 参数time的时间单位
     * @return 返回{@code false} 如果从方法返回之前检测等待时间已过； 否则返回{@code true}
     * @throws InterruptedException-如果当前线程被中断（并且支持线程挂起的中断）
     */
    boolean await(long time, TimeUnit unit) throws InterruptedException;
 
    /**
     *使当前线程等待，直到发出信号或中断它，或者经过截止日期。
     *
      *<p>与此条件相关联的锁被原子释放，并且出于线程调度目的，当前线程被禁用，
     * 并且处于休眠状态，直到发生以下五种情况之一：
     * <ul>
     * <li>其他一些线程调用此条件的signal（）方法，并且当前线程恰好被选择为要唤醒的线程；要么
     * <li>其他一些线程调用此条件的signalAll（）方法。要么
     * <li>其他一些线程使用Thread#interrupt中断了当前线程，并且线程挂起的中断是支持的；要么
     * <li>指定的等待时间过去了；要么
     * <li>发生“虚假唤醒”。
     * </ul>
     *
     * <p>在所有情况下，在此方法可以返回之前，
     * 当前线程必须重新获取与此条件关联的锁。
     * 当线程返回时，可以保证持有此锁。
     *
     * <p>如果当前线程：
     * <ul>
     * <li> 在进入此方法时已设置其中断状态；要么
     * <li> 等待期间被中断（Thread#interrupt），并且线程挂起的中断是支持的
     * </ul>
     * 然后抛出InterruptedException并清除当前线程的中断状态。
     * 在第一种情况下，没有规定在释放锁之前是否进行了中断测试。
     *
     *
     * 返回值指示截止日期是否已过，可以按以下方式使用：
     *  <pre> {@code
     * boolean aMethod(Date deadline) {
     *   boolean stillWaiting = true;
     *   lock.lock();
     *   try {
     *     while (!conditionBeingWaitedFor()) {
     *       if (!stillWaiting)
     *         return false;
     *       stillWaiting = theCondition.awaitUntil(deadline);
     *     }
     *     // ...
     *   } finally {
     *     lock.unlock();
     *   }
     * }}</pre>
     *
     * <p><b>实现注意事项</b>
     *
     * 当调用此方法时，假定当前线程持有与此Condition关联的锁。
     * 由实现类来确定是否是这种情况，如果不是，该如何反应。
     * 典型的做法，一个异常（例如IllegalMonitorStateException）会被跑出来，并且实现类必须记录该事实。
     *
     * 与响应一个signal的通常方法返回相比，实现类可能更倾向于响应一个中断。
     * 在那种情况下，实现类必须确保将信号重定向到另一个等待线程（如果有一个的话）。
     *
     * @param deadline 等待的绝对时间
     * @return {@code false} 如果返回时截止日期已过, 否则
     *         {@code true}
     * @throws InterruptedException-如果当前线程被中断（并且支持线程挂起的中断）
     */
    boolean awaitUntil(Date deadline) throws InterruptedException;
 
    /**
     * 唤醒一个等待线程。
     *
     * 如果有任何线程在这种情况下等待，则选择一个线程进行唤醒。 
     * 然后，该线程必须重新获取锁，然后才能从{@code await}返回。
     *
     * <p></b>实现注意事项</b>

     * 当调用此方法时，实现类可能（并且通常确实）要求当前线程持有与此Condition相关联的锁。
     * 实现必须记录此前提条件，以及如果未持有该锁则采取的任何措施。 
     * 通常，将抛出诸如IllegalMonitorStateException之类的异常。
     */
    void signal();
 
    /**
     * 唤醒所有等待的线程。
     *
     * <p>如果有任何线程在这种情况下等待，那么它们都将被唤醒。 
     * 每个线程必须重新获取锁，然后才能从{@code await}返回。
     *
     * <p><b>实现注意事项</b>
     *
     * <p>当调用此方法时，实现类可能（并且通常确实）要求当前线程持有与此Condition相关联的锁。
     * 实现必须记录此前提条件，以及如果未持有该锁则采取的任何措施。 
     * 通常，将抛出诸如IllegalMonitorStateException之类的异常。
     */
    void signalAll();
}


