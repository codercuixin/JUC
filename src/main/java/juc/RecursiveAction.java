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

/**
 * A recursive resultless {@link ForkJoinTask}.  This class
 * establishes conventions to parameterize resultless actions as
 * {@code Void} {@code ForkJoinTask}s. Because {@code null} is the
 * only valid value of type {@code Void}, methods such as {@code join}
 * always return {@code null} upon completion.
 * 递归的无结果ForkJoinTask。 此类建立约定以将无结果的动作参数化为Void ForkJoinTasks。
 * 因为null是Void类型的唯一有效值，所以诸如join之类的方法在完成时总是返回null。
 *
 * <p><b>Sample Usages.</b> Here is a simple but complete ForkJoin
 * sort that sorts a given {@code long[]} array:
 * 用法示例。 这是一个简单但完整的ForkJoin排序，它对给定的long []数组进行排序：
 *
 * <pre> {@code
 * static class SortTask extends RecursiveAction {
 *   final long[] array; final int lo, hi;
 *   SortTask(long[] array, int lo, int hi) {
 *     this.array = array; this.lo = lo; this.hi = hi;
 *   }
 *   SortTask(long[] array) { this(array, 0, array.length); }
 *   protected void compute() {
 *     if (hi - lo < THRESHOLD)
 *       sortSequentially(lo, hi);
 *     else {
 *       int mid = (lo + hi) >>> 1;
 *       invokeAll(new SortTask(array, lo, mid),
 *                 new SortTask(array, mid, hi));
 *       merge(lo, mid, hi);
 *     }
 *   }
 *   // implementation details follow:
 *   static final int THRESHOLD = 1000;
 *   void sortSequentially(int lo, int hi) {
 *     Arrays.sort(array, lo, hi);
 *   }
 *   void merge(int lo, int mid, int hi) {
 *     long[] buf = Arrays.copyOfRange(array, lo, mid);
 *     for (int i = 0, j = lo, k = mid; i < buf.length; j++)
 *       array[j] = (k == hi || buf[i] < array[k]) ?
 *         buf[i++] : array[k++];
 *   }
 * }}</pre>
 * <p>
 * You could then sort {@code anArray} by creating {@code new
 * SortTask(anArray)} and invoking it in a ForkJoinPool.  As a more
 * concrete simple example, the following task increments each element
 * of an array:
 * 然后，你可以通过创建新的SortTask（anArray）并在ForkJoinPool中调用它来对anArray进行排序。
 * 作为一个更具体的简单示例，以下任务将增加数组中的每个元素：
 * <pre> {@code
 * class IncrementTask extends RecursiveAction {
 *   final long[] array; final int lo, hi;
 *   IncrementTask(long[] array, int lo, int hi) {
 *     this.array = array; this.lo = lo; this.hi = hi;
 *   }
 *   protected void compute() {
 *     if (hi - lo < THRESHOLD) {
 *       for (int i = lo; i < hi; ++i)
 *         array[i]++;
 *     }
 *     else {
 *       int mid = (lo + hi) >>> 1;
 *       invokeAll(new IncrementTask(array, lo, mid),
 *                 new IncrementTask(array, mid, hi));
 *     }
 *   }
 * }}</pre>
 *
 * <p>The following example illustrates some refinements and idioms
 * that may lead to better performance: RecursiveActions need not be
 * fully recursive, so long as they maintain the basic
 * divide-and-conquer approach. Here is a class that sums the squares
 * of each element of a double array, by subdividing out only the
 * right-hand-sides of repeated divisions by two, and keeping track of
 * them with a chain of {@code next} references. It uses a dynamic
 * threshold based on method {@code getSurplusQueuedTaskCount}, but
 * counterbalances potential excess partitioning by directly
 * performing leaf actions on unstolen tasks rather than further
 * subdividing.
 * 下面的示例说明了一些改进和惯用语，它们可能会带来更好的性能：
 * RecursiveActions不需要完全递归，只要它们保持基本的分而治之方法即可。
 * 下面是一个类，通过仅将重复除法的右侧除以2，并用一系列下一个引用跟踪它们，对双精度数组每个元素的平方求和。
 * 它使用基于方法getSurplusQueuedTaskCount的动态阈值，但通过直接对未窃取的任务执行叶操作而不是进一步细分来抵消潜在的多余分区。
 *
 * <pre> {@code
 * double sumOfSquares(ForkJoinPool pool, double[] array) {
 *   int n = array.length;
 *   Applyer a = new Applyer(array, 0, n, null);
 *   pool.invoke(a);
 *   return a.result;
 * }
 *
 * class Applyer extends RecursiveAction {
 *   final double[] array;
 *   final int lo, hi;
 *   double result;
 *   Applyer next; // keeps track of right-hand-side tasks
 *   Applyer(double[] array, int lo, int hi, Applyer next) {
 *     this.array = array; this.lo = lo; this.hi = hi;
 *     this.next = next;
 *   }
 *
 *   double atLeaf(int l, int h) {
 *     double sum = 0;
 *     for (int i = l; i < h; ++i) // perform leftmost base step
 *       sum += array[i] * array[i];
 *     return sum;
 *   }
 *
 *   protected void compute() {
 *     int l = lo;
 *     int h = hi;
 *     Applyer right = null;
 *     while (h - l > 1 && getSurplusQueuedTaskCount() <= 3) {
 *       int mid = (l + h) >>> 1;
 *       right = new Applyer(array, mid, h, right);
 *       right.fork();
 *       h = mid;
 *     }
 *     double sum = atLeaf(l, h);
 *     while (right != null) {
 *       if (right.tryUnfork()) // directly calculate if not stolen
 *         sum += right.atLeaf(right.lo, right.hi);
 *       else {
 *         right.join();
 *         sum += right.result;
 *       }
 *       right = right.next;
 *     }
 *     result = sum;
 *   }
 * }}</pre>
 *
 * @author Doug Lea
 * @since 1.7
 */
public abstract class RecursiveAction extends ForkJoinTask<Void> {
    private static final long serialVersionUID = 5232453952276485070L;

    /**
     * The main computation performed by this task.
     */
    protected abstract void compute();

    /**
     * Always returns {@code null}.
     *
     * @return {@code null} always
     */
    public final Void getRawResult() {
        return null;
    }

    /**
     * Requires null completion value.
     */
    protected final void setRawResult(Void mustBeNull) {
    }

    /**
     * Implements execution conventions for RecursiveActions.
     */
    protected final boolean exec() {
        compute();
        return true;
    }

}
