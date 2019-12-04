package countdownlatch;

import juc.CountDownLatch;

import java.util.concurrent.Executor;

/**
 * * @Author: cuixin
 * * @Date: 2019/12/3 17:11
 */
public class CountDownLatchDemo {
      public static void main(String[] args) throws InterruptedException {
          Driver driver = new Driver();
          driver.main();
      }
}

class Driver {
    void main() throws InterruptedException {
        CountDownLatch startSignal = new CountDownLatch(1);
        int N = 3;
        CountDownLatch doneSignal = new CountDownLatch(N);

        for (int i = 0; i < N; ++i) // 创建并启动线程
            new Thread(new Worker(startSignal, doneSignal)).start();

        doSomethingElse();            // 先做点别的事情，让for循环里面创建的线程在startSignal.await()阻塞。
        startSignal.countDown();      //  让for循环里面创建的线程从startSignal.await()返回，继续执行。
        doSomethingElse();
        doneSignal.await();           // 等待for循环里面创建的线程都结束。
        System.out.println("main thread has ended");
    }
    private void doSomethingElse() throws InterruptedException {
        Thread.sleep(10000);
        System.out.println("main thread doSomethingElse, sleep 10s");
    }
}

class Worker implements Runnable {
    private final CountDownLatch startSignal;
    private final CountDownLatch doneSignal;

    Worker(CountDownLatch startSignal, CountDownLatch doneSignal) {
        this.startSignal = startSignal;
        this.doneSignal = doneSignal;
    }

    public void run() {
        try {
            startSignal.await();
            doWork();
            doneSignal.countDown();
        } catch (InterruptedException ex) {
        } // return;
    }

    void doWork() {
        System.out.println(Thread.currentThread().getName()+" has done work");
    }
}