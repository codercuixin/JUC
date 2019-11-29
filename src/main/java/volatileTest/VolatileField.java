package volatileTest;

/**
 * * @Author: cuixin
 * * @Date: 2019/10/8 19:10
 */
public class VolatileField {
    private volatile int a;
    public int getA(){
        return a;
    }
    public void setA(int a){
        this.a = a;
    }
    public static void main(String[] args){

    }
}
