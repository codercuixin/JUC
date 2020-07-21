package forkjoinpool;

import juc.ForkJoinPool;
import juc.RecursiveAction;

import java.util.ArrayList;
import java.util.List;

/**
 * * @Author: cuixin
 * * @Date: 2020/7/21 10:06
 * ref: http://tutorials.jenkov.com/java-util-concurrent/java-fork-and-join-forkjoinpool.html
 * ref: http://coopsoft.com/ar/CalamityArticle.html
 */
public class MyRecursiveAction extends RecursiveAction {
    private long workLoad = 0;
    public MyRecursiveAction(long workLoad){
        this.workLoad = workLoad;
    }

    @Override
    protected void compute() {
        //if work is above threshold, break tasks up into smaller tasks
        if(this.workLoad > 16){
            System.out.println(Thread.currentThread().getName()+"——Splitting workLoad: "+this.workLoad);
            List<MyRecursiveAction> subTasks = createSubTasks();
            for(RecursiveAction subtask: subTasks){
                subtask.fork();
            }
        }else{
            System.out.println(Thread.currentThread().getName()+"——doing workload myself: "+this.workLoad);
        }
    }
    private List<MyRecursiveAction> createSubTasks(){
        List<MyRecursiveAction> subTasks = new ArrayList<>();
        MyRecursiveAction subTask1 = new MyRecursiveAction(this.workLoad/ 2);
        MyRecursiveAction subTask2 = new MyRecursiveAction(this.workLoad/ 2);
        subTasks.add(subTask1);
        subTasks.add(subTask2);
        return subTasks;
    }

    public static void main(String[] args){
        ForkJoinPool forkJoinPool = new ForkJoinPool(4);
        MyRecursiveAction myRecursiveAction = new MyRecursiveAction(48);
        forkJoinPool.invoke(myRecursiveAction);
    }
}
