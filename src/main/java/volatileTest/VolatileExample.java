package volatileTest;

class VolatileExample {
  int x = 0;
  volatile boolean v = false;
  public void writer() {
    x = 42;
    v = true;
  }

  public boolean reader() {
    if (v == true) {
      //uses x - guaranteed to see 42.
      // java -ea volatileTest/VolatileExample
      assert x == 42;
      System.out.println("read x = 42");
      return true;
    }
    return false;
  }

  public static void main(String[] args){
    VolatileExample volatileExample = new VolatileExample();

    Thread t1 = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          Thread.sleep(10000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        volatileExample.writer();

      }
    });
    Thread t2 = new Thread(new Runnable() {
      @Override
      public void run() {
        while(!volatileExample.reader()){

        }
      }
    });
    t1.start();
    t2.start();
  }
}