package reentrantlock;


import java.util.concurrent.TimeUnit;
import juc.locks.ReentrantLock;

/**
 * * @Author: cuixin
 * * @Date: 2019/12/3 11:05
 */
public class ReentrantLockDemo {
    public static void main(String[] args){
//        testLockUnlock();
        testTryLockAndUnlock();
    }

    public static void testLockUnlock(){
        final ReentrantLock reentrantLock = new ReentrantLock(false);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                //获取独占锁，拿不到就阻塞
                reentrantLock.lock();
                try {
                    System.out.println(Thread.currentThread().getName() + " has got lock");
                    Thread.sleep(1000);
                    System.out.println(Thread.currentThread().getName()+" has sleep 1s and  release now");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    //试图释放此锁
                    reentrantLock.unlock();
                }

            }
        };
        Thread t1 = new Thread(r);
        Thread t2 = new Thread(r);
        t1.start();
        t2.start();
      }
    public static void testTryLockAndUnlock(){
        final ReentrantLock reentrantLock = new ReentrantLock(false);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    //改成2000，再看看结果。
                    //tryLock 如果在给定的等待时间内锁没有被另一个线程持有，并且当前线程没有被中断{@linkplain Thread#interrupt}，则获取锁
                    if(reentrantLock.tryLock(50, TimeUnit.MILLISECONDS)) {
                        try {
                            System.out.println(Thread.currentThread().getName() + " has got lock");
                            Thread.sleep(1000);
                            System.out.println(Thread.currentThread().getName() + " has sleep 1s and  release now");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } finally {
                            reentrantLock.unlock();
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        };
        Thread t1 = new Thread(r);
        Thread t2 = new Thread(r);
        t1.start();
        t2.start();
    }
}
