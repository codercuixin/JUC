package statmpedlock;

/**
 * * @Author: cuixin
 * * @Date: 2019/12/10 18:48
 */
public class Test {
    public static void main(String[] args){
        final Point p = new Point();
        Thread moveThread = new Thread(new Runnable() {
            @Override
            public void run() {
              while(true){
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
                  double ret= p.distanceFromOrigin();
                   System.out.println("optimisticReadThread: distanceFromOrigin  "+ ret);
                }
            }
        });
        moveThread.start();
        optimisticReadThread.start();
    }
}
