package volatileTest;

import java.util.concurrent.CountDownLatch;

/**
 * * @Author: cuixin
 * * @Date: 2020/8/5 19:25
 */
public class VolatileAdder {
    private  volatile int x;
    public  void add(){
        //不是原子操作
        x++;
    }
    public  int get(){
        return x;
    }


    public static void main(String[] args) throws Exception
    {
        VolatileAdder instance = new VolatileAdder();
        int taskNum = 2;
        CountDownLatch countDownLatch = new CountDownLatch(taskNum);
        for(int i=0; i<taskNum; i++){
            new Thread(new Task(instance, countDownLatch)).start();
        }
        countDownLatch.await();
        System.out.println(instance.get());
    }

    private static class Task implements Runnable{
        private VolatileAdder adder;
        private CountDownLatch latch;
        Task(VolatileAdder adder,  CountDownLatch latch){
            this.adder = adder;
            this.latch = latch;
        }

        @Override
        public void run() {
            for (int i = 0; i < 100000; i++)
            {
                adder.add();
            }
            latch.countDown();
        }
    }
}
