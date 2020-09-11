package synchronizedTest;
class BumpTest {
    int count;
    void bump() {
        synchronized (this) { count++; }
    }
    static int classCount;

    static void classBump() {
        try {
            synchronized (Class.forName("BumpTest")) {
                classCount++;
            }
        } catch (ClassNotFoundException e) {}
    }
    public static void main(String[] args){
        for(int i=0; i< 100000; i++){
            classBump();
        }
        System.out.println(classCount);
        BumpTest test = new BumpTest();
        for(int i=0; i< 100000; i++){
            test.bump();
        }
        System.out.println(test.count);
    }
}