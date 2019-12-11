package statmpedlock;

import juc.locks.StampedLock;

/**
 * * @Author: cuixin
 * * @Date: 2019/12/5 17:17
 */
public class Point {

    private double x, y;
    private final StampedLock sl = new StampedLock();

    void move(double deltaX, double deltaY) { // 一个独占锁定的方法
        //独占地获取写锁，如果有必须就阻塞直到锁可用。
        long stamp = sl.writeLock();
        try {
            x += deltaX;
            y += deltaY;
        } finally {
            //如果锁状态与给定的戳记相匹配，则释放互独占的写锁。
            sl.unlockWrite(stamp);
        }
    }

    double distanceFromOrigin() { //一个只读的方法
        //返回稍后可以验证的戳记；如果排他锁定，则返回零。
        long stamp = sl.tryOptimisticRead();
        double currentX = x, currentY = y;
        //如果自发放给定戳记stamp以来，还没有线程独占获得写锁，则返回true。
        if (!sl.validate(stamp)) {
            //有线程获取写锁，则不可以乐观读，需要获取读锁（如果拿不到会一直阻塞当前线程）
            stamp = sl.readLock();
            try {
                currentX = x;
                currentY = y;
            } finally {
                //释放读锁
                sl.unlockRead(stamp);
            }
        }
        return Math.sqrt(currentX * currentX + currentY * currentY);
    }

    void moveIfAtOrigin(double newX, double newY) { //锁升级
        // 也可以从乐观模式开始，而不是读模式。Could instead start with optimistic, not read mode
        //非排他性地获取锁，必要时阻塞直到可用。
        long stamp = sl.readLock();
        try {
            while (x == 0.0 && y == 0.0) {
                //尝试升级为写锁
                long ws = sl.tryConvertToWriteLock(stamp);
                if (ws != 0L) { //ws！=0为true，表示读锁升级为写锁成功。
                    stamp = ws;
                    x = newX;
                    y = newY;
                    break;
                } else { //ws==0为true，表示读锁升级为写锁失败
                    //锁升级失败，则需要先释放读锁，在获取写锁。这里，下一次循环执行tryConvertToWriteLock就会非0值，因为stamp已经更新为写锁位设为1。
                    sl.unlockRead(stamp);
                    stamp = sl.writeLock();
                }
            }
        } finally {
            //如果锁状态与给定的戳记匹配，则释放相应的锁模式。
            sl.unlock(stamp);
        }
    }
}
