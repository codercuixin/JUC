package synchronizedTest;

class Test {
    int count;
    synchronized void bump() {
        count++;
    }
    static int classCount;
    static synchronized void classBump() {
        classCount++;
    }

    public static void main(String[] args){
        for(int i=0; i< 100000; i++){
            classBump();
        }
        System.out.println(classCount);
        Test test = new Test();
        for(int i=0; i< 100000; i++){
           test.bump();
        }
        System.out.println(test.count);
    }
}