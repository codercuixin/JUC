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
 * This example is the improved version of the last one where only one task is fork from this task.
 * This also return a value.
 * */
public class CountedCompleterExample2 {
    public static void main(String[] args) {
        List<Integer> list = new ArrayList<>();
        for (int i = 1; i < 8; i++) {
            list.add(i);
        }
        Integer sum = ForkJoinPool.commonPool().invoke(new FactorialTask(null, new AtomicReference<>(new Integer(0)),
                list));
        System.out.println("Sum of factorial = " + sum);

    }

    private static class FactorialTask extends CountedCompleter<Integer> {
        private static int SEQUENTIAL_THRESHOLD = 2;
        private List<Integer> integerList;
        private AtomicReference<Integer> result;

        private FactorialTask(CountedCompleter<Integer> parent, AtomicReference<Integer> result,
                              List<Integer> integerList) {
            super(parent);
            this.integerList = integerList;
            this.result = result;
        }

        @Override
        public Integer getRawResult() {
            return result.get();
        }

        @Override
        public void compute() {
          if(integerList.size() <= SEQUENTIAL_THRESHOLD){
              sumFactorials();
              propagateCompletion();
          }else{
              int middle = integerList.size() /2;
              List<Integer> newList = integerList.subList(middle, integerList.size());
              integerList = integerList.subList(0, middle);
              addToPendingCount(1);
              FactorialTask task = new FactorialTask(this, result, newList);
              task.fork();
              this.compute();
          }
        }

        private void addFactorialToResult(int factorial) {
            result.getAndAccumulate(factorial, (b1, b2) -> b1 + b2);
        }

        private void sumFactorials() {
            for (Integer i : integerList) {
                int resultOfI = calculateFactorial(i);
                addFactorialToResult(resultOfI);
            }
        }

        private int calculateFactorial(int i) {
            int result = 1;
            for (int start = 2; start <= i; start++) {
                result *= start;
            }
            return result;
        }
    }
}
