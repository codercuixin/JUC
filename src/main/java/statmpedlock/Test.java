package statmpedlock;

/**
 * * @Author: cuixin
 * * @Date: 2019/12/10 18:48
 */
public class Test {
    public static void main(String[] args) {
//        testWriteAndOptimisticRead();
        tesOptimisticReadAndLockUpgrade();
    }

    /**
     * 测试写锁模式和乐观读模式
     */
    public static void testWriteAndOptimisticRead() {
        final Point p = new Point();
        Thread writeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    p.move(1, 1);
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        Thread optimisticReadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    double ret = p.distanceFromOrigin();
                    System.out.println("optimisticReadThread: distanceFromOrigin  " + ret);
                }
            }
        });
        writeThread.start();
        optimisticReadThread.start();
    }

    /**
     * 测试乐观读模式，以及读锁模式升级到写锁模式
     */
    public static void tesOptimisticReadAndLockUpgrade() {
        final Point p = new Point();
        Thread optimisticReadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    double ret = p.distanceFromOrigin();
                    if(ret > 0) {
                        System.out.println("optimisticReadThread  " + ret);
                        break;
                    }
                }

            }
        });
        Thread lockUpgradeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                long cur = System.currentTimeMillis();
                p.moveIfAtOrigin(cur % 100, cur % 100);
            }
        });
        optimisticReadThread.start();
        lockUpgradeThread.start();
    }
}
