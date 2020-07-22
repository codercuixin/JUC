package forkjoinpool;

import juc.CountedCompleter;
import juc.ForkJoinPool;
import juc.atomic.AtomicReference;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
/**
 * * @Author: cuixin
 * * @Date: 2020/7/22 10:14
 * ref: https://www.logicbig.com/how-to/code-snippets/jcode-java-concurrency-countedcompleter.html
 * In this example each task forks two tasks.
 */
public class CountedCompleterExample {
    public static void main(String[] args) {
        List<Integer> list = new ArrayList<>();
        for (int i = 1; i < 8; i++) {
            list.add(i);
        }
       ForkJoinPool.commonPool().invoke(new FactorialTask(null,
                list));
    }

    private static class FactorialTask extends CountedCompleter<Void>{
        private static int SEQUENTIAL_THRESHOLD = 2;
        private List<Integer> integerList;
        private int numCalculated;
        private FactorialTask(CountedCompleter<Void> parent,
                              List<Integer> integerList){
            super(parent);
            this.integerList = integerList;
        }


        @Override
        public void compute() {
            if(integerList.size() <= SEQUENTIAL_THRESHOLD){
                showFactorials();
            }
            else{
                int middle = integerList.size() /2;
                List<Integer> rightList = integerList.subList(middle, integerList.size());
                List<Integer> leftList = integerList.subList(0, middle);
                addToPendingCount(2);
                FactorialTask taskRight =new FactorialTask(this, rightList);
                FactorialTask taskLeft = new FactorialTask(this, leftList);
                taskRight.fork();
                taskLeft.fork();
            }
            tryComplete();
        }

        @Override
        public void onCompletion(CountedCompleter<?> caller) {
            if(caller == this){
                System.out.printf("completed thread: %s, numberOfCalculated=%d%n", Thread.currentThread().getName(),
                        numCalculated);
            }
        }

        private void showFactorials(){
            for(Integer i: integerList){
                int factorial = calculateFactorial(i);
                System.out.printf("%s! = %s, thread = %s%n", i, factorial, Thread
                        .currentThread().getName());
                numCalculated++;
            }
        }

        private int calculateFactorial(int i){
            int result = 1;
            for(int start=2; start <= i; start++ ){
                result *= start;
            }
            return result;
        }
    }
}
