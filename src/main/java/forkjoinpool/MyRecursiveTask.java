package forkjoinpool;

import juc.ForkJoinPool;
import juc.RecursiveTask;

import java.util.ArrayList;
import java.util.List;

/**
 * * @Author: cuixin
 * * @Date: 2020/7/21 10:30
 */
public class MyRecursiveTask extends RecursiveTask<Long> {
    private long workLoad = 0;

    public MyRecursiveTask(long workLoad) {
        this.workLoad = workLoad;
    }

    @Override
    protected Long compute() {
        //if work is above threshold, break tasks up into smaller tasks
        if (this.workLoad > 16) {
            System.out.println(Thread.currentThread().getName() + "——Splitting workLoad: " + this.workLoad);
            List<MyRecursiveTask> subTasks = createSubTasks();
            for (MyRecursiveTask subtask : subTasks) {
                subtask.fork();
            }
            long result = 0;
            for (MyRecursiveTask subtask : subTasks) {
                result += subtask.join();
            }
            System.out.println(Thread.currentThread().getName() + "——returns : " + result);
            return result;
        } else {
            System.out.println(Thread.currentThread().getName() + "——doing workload myself: " + this.workLoad);
            System.out.println(Thread.currentThread().getName() + "——returns : " + this.workLoad);
            return this.workLoad;
        }
    }

    private List<MyRecursiveTask> createSubTasks() {
        List<MyRecursiveTask> subTasks = new ArrayList<>();
        MyRecursiveTask subTask1 = new MyRecursiveTask(this.workLoad / 2);
        MyRecursiveTask subTask2 = new MyRecursiveTask(this.workLoad / 2);
        subTasks.add(subTask1);
        subTasks.add(subTask2);
        return subTasks;
    }

    public static void main(String[] args) {
        ForkJoinPool forkJoinPool = new ForkJoinPool(8);
        MyRecursiveTask myRecursiveTask = new MyRecursiveTask(128);
        long mergeResult = forkJoinPool.invoke(myRecursiveTask);
        System.out.print("mergeResult = " + mergeResult);
    }
}
