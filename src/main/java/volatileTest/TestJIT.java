package volatileTest;


public class TestJIT
{
    private static volatile int field1;
    private static  int field2;
    private static int field3;
    private static int field4;
    private static int field5;
    private static volatile int field6;
     
    private static void assign(int i)
    {
        field1 = i << 1;
        field2 = i << 2;
        field3 = i << 3;
        field4 = i << 4;
        field5 = i << 5;
        field6 = i << 6;
    }
 
    public static void main(String[] args) throws Exception
    {
        for (int i = 0; i < 10000; i++)
        {
            assign(i);
        }
        Thread.sleep(1000);
    }
}