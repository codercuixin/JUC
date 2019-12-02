package unsafeTest;

import juc.locks.LockSupport;

/**
 * * @Author: cuixin
 * * @Date: 2019/12/2 8:58
 */
public class LockSupportDemo {
    public static void main(String[] args) {
        final int parkNum = 3;
        Runnable r1 = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < parkNum; i++) {
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
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    LockSupport.unpark(t1);
                    System.out.println(Thread.currentThread() + "unpark continue, round " + i);
                }
            }
        };
        Thread t2 = new Thread(r2);
        t1.start();
        t2.start();
    }
}
