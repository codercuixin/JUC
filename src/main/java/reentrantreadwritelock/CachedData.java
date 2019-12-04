package reentrantreadwritelock;

import juc.locks.ReentrantReadWriteLock;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * * @Author: cuixin
 * * @Date: 2019/12/4 11:02
 */
public class CachedData {
    Object data;
    volatile boolean cacheValid;
    final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();

    void processCachedData() {
        //获取读锁。
        rwl.readLock().lock();
        if (!cacheValid) {
            // 在获取写锁之前必须释放读锁
            rwl.readLock().unlock();
            rwl.writeLock().lock();
            try {
                // 由于在当前线程之前其他线程可能已经获取了写锁，并且更改了cacheValid的状态，所以需要检查cacheValid的状态。
                if (!cacheValid) {
                    data = getNewValidData();
                    cacheValid = true;
                }
                // 在释放写锁之前先降级获取读锁。
                rwl.readLock().lock();
            } finally {
                rwl.writeLock().unlock(); // 释放写锁，仍然持有读锁。
            }
        }
        try {
            use(data);
        } finally {
            //释放读取。
            rwl.readLock().unlock();
        }
    }
    private AtomicInteger atomicInteger = new AtomicInteger();
    private String getNewValidData(){
        return "Hello World, " + atomicInteger.getAndIncrement();
    }

    private void use(Object data){
        System.out.println(data);
        //每过1s将cacheValid设为false，因而更多的是在读取，而不是过期。
        if(System.currentTimeMillis() % 1000 == 0){
            cacheValid = false;
        }
    }

}
