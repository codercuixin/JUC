package synchronizedTest;

import juc.atomic.AtomicInteger;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.System.out;

/**
 * https://stackoverflow.com/questions/46312817/does-java-ever-rebias-an-individual-lock
 * In order to reproduce my results please use the following JVM arguments:
 * <p>
 * -XX:+UseBiasedLocking - is not required is used by default
 * -XX:BiasedLockingStartupDelay=0 - by default there is a delay 4s
 * -XX:+PrintSafepointStatistics -XX:PrintSafepointStatisticsCount=1 - to enable safepoint log
 * -XX:+TraceBiasedLocking - very useful log
 * -XX:BiasedLockingBulkRebiasThreshold=1 - to reduce amount of iterations in my example
 * java  -XX:+UseBiasedLocking -XX:BiasedLockingStartupDelay=0  -XX:+PrintSafepointStatistics -XX:PrintSafepointStatisticsCount=1  -XX:+TraceBiasedLocking -XX:BiasedLockingBulkRebiasThreshold=1 synchron
 * izedTest/BiasLocking
 */
public class BiasLocking {

    private static final Unsafe U;
    private static final long OFFSET = 0L;

    static {

        try {
            Field unsafe = Unsafe.class.getDeclaredField("theUnsafe");
            unsafe.setAccessible(true);
            U = (Unsafe) unsafe.get(null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) throws Exception {

        ExecutorService thread = Executors.newSingleThreadExecutor();

        for (int i = 0; i < 15; i++) {
            final Monitor a = new Monitor();
            synchronized (a) {
                out.println("Main thread \t\t" + printHeader(a));
            }

            thread.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    synchronized (a) {
                        out.println("Work thread \t\t" + printHeader(a));
                    }
                    return null;
                }
            }).get();
        }

        thread.shutdown();
    }

    private static String printHeader(Object a) {
        int word = U.getInt(a, OFFSET);
        return Integer.toHexString(word);
    }

    private static class Monitor {
        // mutex object
    }

}