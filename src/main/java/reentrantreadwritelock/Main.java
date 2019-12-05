package reentrantreadwritelock;

/**
 * * @Author: cuixin
 * * @Date: 2019/12/4 11:18
 */
public class Main {
    public static void main(String[] args){
//        testCachedData();
        testRWDictionary();
    }
    public static void testCachedData(){
        final CachedData cachedData = new CachedData();
        Runnable r = new Runnable() {
            @Override
            public void run() {
                while (true) {
                    cachedData.processCachedData();
                }
            }
        };
        Thread t1 = new Thread(r);
        Thread t2 = new Thread(r);
        t1.start();
        t2.start();
    }
    public static void testRWDictionary(){
        final String key = "Hello";
        final RWDictionary rwDictionary = new RWDictionary();
        Runnable modifyRunnable = new Runnable() {
            @Override
            public void run() {
                for(int i = 0; i< 10; i++){
                    Data d = new Data();
                    d.f = "world"+i;
                    rwDictionary.put(key, d);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        Runnable getRunnable = new Runnable() {
            @Override
            public void run() {
                while (true){
                    System.out.println(rwDictionary.get(key) == null ? "": rwDictionary.get(key).f);
                }
            }
        };

        Thread t1 = new Thread(modifyRunnable);
        Thread t2 = new Thread(getRunnable);
        t1.start();
        t2.start();
    }
}
