一些关于通过 Loom 实现可控挂起的尝试.

# 基本思路

左等等, 右等等, 可总算把 Loom 等出来了.  
和我之前见过的协程设计有点不太一样, Loom 打算让我直接往里面写同步代码. 下了最新最热的 JDK 19 之后我第一时间奔过去 `Continuation`


.. 然而在 `jdk.internal`. 我们没法控制这样底层的 API 来实现协程调度 (虽然说本来也不应该让我们做)  

怎么办呢?

如果 Loom 为了让原先在平台线程上运行的代码运行的和虚拟线程里一样, 那么 `ReentrantLock` 必然是被包括在内的.

所以我花了点时间写了个 demo 测试看看

```java
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
```

只要在对象创建的第一时间让一个单独的线程持有锁, 此后第二次 `suspend` 调用必然被堵塞.    
而 Loom 应该会在这种时候把 `Continuation` 切出, 把平台线程空出来给别的虚拟线程用.  

```java
class A {
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
```

`virtualCounter` 总是 0 而且 `platformCounter` 总是 12, `isVirtual` 总是 true. `Running again` 的提示数目可以远远超出这个数值.  

此时再插入一段不断运行的代码也仍然奏效.

只是当一个想法记录, 之后应该会再来研究.

# 为什么写这个

给 Bot 框架做准备.

```java
class Session extends SessionBase{
    public void onNewSession(){
        var num = expect(NUMBER,"请输入电话号码");
        var string = expect(STRING,"请输入人名");
        // ...
    }
}
```