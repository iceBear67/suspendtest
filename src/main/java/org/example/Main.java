

import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class Main {
    static class SuspendPoint {
        private static final ExecutorService OPERATOR = Executors.newSingleThreadExecutor();
        private final ReentrantLock lock = new ReentrantLock();
        private final Consumer<SuspendPoint> onSuspend;
        private final Runnable onContinue;

        public SuspendPoint(Consumer<SuspendPoint> onSuspend, Runnable onContinue) {
            this.onSuspend = onSuspend;
            this.onContinue = onContinue;
            try {
                OPERATOR.submit(this::suspend).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        public void suspend() {
            onSuspend.accept(this);
            lock.lock();
        }

        public void go(){
            OPERATOR.submit(lock::unlock);
            onContinue.run();
        }


    }

    public static void main(String[] args) {
        var list = new LinkedBlockingQueue<SuspendPoint>();
        for (int i = 0; i < 5000; i++) {
            var point = new SuspendPoint(list::add, () -> {

            });
            Thread.ofVirtual().start(() -> {
                    point.suspend();
                    int platformCounter = 0;
                    int virtualCounter = 0;
                for (Map.Entry<Thread, StackTraceElement[]> threadEntry : Thread.getAllStackTraces().entrySet()) {
                    var thread = threadEntry.getKey();
                    if (thread.isVirtual()) {
                        virtualCounter++;
                    }else{
                        platformCounter++;
                    }
                }
                    System.out.println("Running again! Is virtual: "+Thread.currentThread().isVirtual()+" name: "+Thread.currentThread().getName()+" virt: "+virtualCounter+" plat:"+platformCounter);

            });
        }
        while (new Scanner(System.in).hasNextLine()) {
            list.poll().go();
        }
    }
}