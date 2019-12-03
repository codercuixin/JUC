package locksupport;

import java.util.Date;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * * @Author: cuixin
 * * @Date: 2019/12/2 8:58
 */
public class LockSupportDemo {
    public static void main(String[] args) throws InterruptedException {
//        testJustOnePermit();
//       testUnparkParkOrder();
        testFIFOMutex();
    }

    public static void testJustOnePermit() {
        Thread mainThread = Thread.currentThread();
        LockSupport.unpark(mainThread);
        System.out.println(mainThread.getName() + " unpark the first time");
        LockSupport.unpark(mainThread);
        System.out.println(mainThread.getName() + " unpark the second time");
        //因为LockSupport这个类与使用它的每个线程关联一个许可证，并且只是一个许可证，所以第二次调用unpark，实际上什么也没有发生，许可证值仍为1.
        //因此，下面第一次调用park方法，就会消耗掉那个关联的许可，许可值变为0，因而第二次调用park时，就没有许可可供消费了，所以就会休眠在这里，
        // 也就是永远不会执行到"park the second time" 那一行。
        LockSupport.park();
        System.out.println(mainThread.getName() + " park the first time");
        LockSupport.park();
        System.out.println(mainThread.getName() + " park the second time");
    }

    public static void testUnparkParkOrder() {
        //测试unpark，park的执行顺序，初始时，关联的那个许可为0，所以unpark要想继续执行，就必须park释放那个可证。
        final int parkNum = 3;
        Runnable r1 = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < parkNum; i++) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println(Thread.currentThread() + " park time: " + new Date());
                    LockSupport.park();
                    System.out.println(Thread.currentThread() + " park continue, round  " + i);
                }
            }
        };
        final Thread t1 = new Thread(r1);
        Runnable r2 = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < parkNum; i++) {
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println(Thread.currentThread() + " unpark time: " + new Date());
                    LockSupport.unpark(t1);
                    System.out.println(Thread.currentThread() + "unpark continue, round " + i);
                }
            }
        };
        Thread t2 = new Thread(r2);
        t1.start();
        t2.start();
    }

    public static void testFIFOMutex(){
        //测试先进先出的互斥锁。
        final FIFOMutex  fifoMutex = new FIFOMutex();
        Runnable r = new Runnable() {
            @Override
            public void run() {
                fifoMutex.lock();
                try {
                    System.out.println(Thread.currentThread().getName()+" gets the lock ");
                    //睡眠1s，模拟干活
                    Thread.sleep(1000);
                    System.out.println(Thread.currentThread().getName()+" has done the job and now release the lock");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                 fifoMutex.unlock();
                }
            }
        };
        for(int i=0; i< 3; i++){
            Thread t = new Thread(r);
            t.start();
        }
    }

   static class FIFOMutex {
        private final AtomicBoolean locked = new AtomicBoolean(false);
        private final Queue<Thread> waiters
                = new ConcurrentLinkedQueue<Thread>();

        public void lock() {
            boolean wasInterrupted = false;
            Thread current = Thread.currentThread();
            waiters.add(current);

            // 当前线程不是队列的头节点时，或者不能获取锁成功（即AtomicBoolean CAS成功），就阻塞。
            while (waiters.peek() != current ||
                    !locked.compareAndSet(false, true)) {
                //阻塞当前线程，直到许可证重新变为1，并且设置blocker为FIFOMutex对象
                LockSupport.park(this);
                if (Thread.interrupted()) // ignore interrupts while waiting
                    wasInterrupted = true;
            }

            waiters.remove();
            if (wasInterrupted)          // reassert interrupt status on exit
                current.interrupt();
        }

        public void unlock() {
            locked.set(false);
            //将首节点对应线程的许可边为1，即取消阻塞该线程。
            LockSupport.unpark(waiters.peek());
        }
    }

}
