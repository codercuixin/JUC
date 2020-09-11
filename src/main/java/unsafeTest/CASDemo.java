package unsafeTest;

import sun.misc2.Unsafe;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * * @Author: cuixin
 * * @Date: 2019/12/2 8:59
 */
public class CASDemo {
    public static void main(String[] args) throws NoSuchFieldException, InterruptedException {
        int NUM_OF_THREADS = 1000;
        int NUM_OF_INCREMENTS = 100000;
        ExecutorService service = Executors.newFixedThreadPool(NUM_OF_THREADS);
        //我的机器：Counter result: 97726475 Time passed in ms : 481
//        Counter counter = new StupidCounter();
        //我的机器：Counter result: 100000000 Time passed in ms : 3534
//        Counter counter = new SyncCounter();
        //我的机器：Counter result: 100000000 Time passed in ms : 3004
//        Counter counter = new LockCounter();
        //我的机器：Counter result: 100000000 Time passed in ms : 2337
//        Counter counter = new AtomicCounter();
        //我的机器：Counter result: 100000000 Time passed in ms : 6920
        CASCounter counter = new CASCounter();
        long before = System.currentTimeMillis();
        for(int i=0; i<NUM_OF_THREADS; i++){
//            service.submit(new CounterClient(counter, NUM_OF_INCREMENTS));
        }
        service.shutdown();
        service.awaitTermination(1, TimeUnit.MINUTES);
        long after = System.currentTimeMillis();
        System.out.println("Counter result: "+ counter.getCounter());
        System.out.println("Time passed in ms : "+ (after - before));
    }
    public static class CASCounter {
        private volatile long counter = 0;
        private Unsafe unsafe;
        private long offset;
        public CASCounter() throws NoSuchFieldException {
            unsafe = GetUnsafeFromReflect.getUnsafe();
            offset = unsafe.objectFieldOffset(CASCounter.class.getDeclaredField("counter"));
        }

        public void increment(){
            long before = counter;
            while (!unsafe.compareAndSwapLong(this, offset, before, before + 1)) {
                before = counter;
            }
        }
        public long getCounter() {
            return  counter;
        }
    }
}
