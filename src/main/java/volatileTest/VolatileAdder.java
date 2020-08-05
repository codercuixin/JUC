package volatileTest;

/**
 * * @Author: cuixin
 * * @Date: 2020/8/5 19:25
 */
public class VolatileAdder {
    private volatile int x;
    public void add(){
        //不是原子操作
        x++;
    }
}
