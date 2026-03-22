# 第五章：WaitStrategy 等待策略

## 5.1 等待策略的作用

当消费者处理速度快于生产者时，消费者需要"等待"新事件的到来。WaitStrategy 定义了这种等待行为。

```java
public interface WaitStrategy {
    long waitFor(long sequence, Sequence cursor, 
                 Sequence dependentSequence, SequenceBarrier barrier)
            throws AlertException, InterruptedException, TimeoutException;
    
    void signalAllWhenBlocking();
}
```

## 5.2 各种等待策略详解

### 5.2.1 BusySpinWaitStrategy（忙等待）

```java
// 源码简化
public long waitFor(long sequence, Sequence cursor, ...) {
    while ((availableSequence = cursor.get()) < sequence) {
        ThreadHints.onSpinWait();  // JDK 9+ 的自旋提示
    }
    return availableSequence;
}
```

```
CPU 占用: ████████████████ 100%
延迟:     ▏ 极低（纳秒级）
原理:     消费者线程持续自旋，不让出 CPU
适用:     延迟要求极低且 CPU 核心数充足的场景
注意:     需要绑定 CPU 核心（线程数 <= 物理核心数）
```

### 5.2.2 YieldingWaitStrategy（让步等待）

```java
// 源码简化
private static final int SPIN_TRIES = 100;

public long waitFor(long sequence, Sequence cursor, ...) {
    int counter = SPIN_TRIES;
    while ((availableSequence = cursor.get()) < sequence) {
        counter = applyWaitMethod(counter);
    }
    return availableSequence;
}

private int applyWaitMethod(int counter) {
    if (counter == 0) {
        Thread.yield();  // 让出 CPU 时间片
    } else {
        --counter;       // 先自旋 100 次
    }
    return counter;
}
```

```
CPU 占用: ██████████████░░ ~85%
延迟:     ██ 低（亚微秒级）
原理:     先自旋 100 次，然后 yield 让出 CPU
适用:     低延迟场景，且允许一定 CPU 消耗
推荐:     大多数低延迟场景的首选
```

### 5.2.3 SleepingWaitStrategy（睡眠等待）

```java
// 源码简化
private static final int DEFAULT_RETRIES = 200;

public long waitFor(long sequence, Sequence cursor, ...) {
    int counter = DEFAULT_RETRIES;
    while ((availableSequence = cursor.get()) < sequence) {
        counter = applyWaitMethod(counter);
    }
    return availableSequence;
}

private int applyWaitMethod(int counter) {
    if (counter > 100) {
        --counter;           // 前 100 次: 自旋
    } else if (counter > 0) {
        --counter;
        Thread.yield();      // 第 100-200 次: yield
    } else {
        LockSupport.parkNanos(sleepTimeNs);  // 之后: sleep
    }
    return counter;
}
```

```
CPU 占用: ████░░░░░░░░░░░░ ~25%
延迟:     ████████ 中等（微秒级）
原理:     三段式：自旋 → yield → sleep
适用:     对延迟不太敏感，需要节省 CPU 的场景
推荐:     异步日志等后台处理场景
```

### 5.2.4 BlockingWaitStrategy（阻塞等待）

```java
// 源码简化
private final Lock lock = new ReentrantLock();
private final Condition processorNotifyCondition = lock.newCondition();

public long waitFor(long sequence, Sequence cursor, ...) {
    if (cursor.get() < sequence) {
        lock.lock();
        try {
            while (cursor.get() < sequence) {
                processorNotifyCondition.await();  // 阻塞等待
            }
        } finally {
            lock.unlock();
        }
    }
    return cursor.get();
}

public void signalAllWhenBlocking() {
    lock.lock();
    try {
        processorNotifyCondition.signalAll();  // 生产者唤醒消费者
    } finally {
        lock.unlock();
    }
}
```

```
CPU 占用: █░░░░░░░░░░░░░░░ ~5%
延迟:     ████████████████ 高（毫秒级）
原理:     使用 Lock + Condition 阻塞等待
适用:     吞吐量优先，CPU 资源受限的场景
注意:     这是 Disruptor 中唯一使用锁的策略
```

### 5.2.5 TimeoutBlockingWaitStrategy（超时阻塞等待）

```java
public long waitFor(long sequence, Sequence cursor, ...) {
    if (cursor.get() < sequence) {
        lock.lock();
        try {
            while (cursor.get() < sequence) {
                if (!processorNotifyCondition.await(timeout, timeUnit)) {
                    throw TimeoutException.INSTANCE;  // 超时抛异常
                }
            }
        } finally {
            lock.unlock();
        }
    }
    return cursor.get();
}
```

```
CPU 占用: █░░░░░░░░░░░░░░░ ~5%
延迟:     ████████████ 可控
原理:     BlockingWaitStrategy + 超时机制
适用:     需要超时处理的场景（如心跳检测）
```

## 5.3 等待策略选型指南

```
                       CPU 占用
                    低 ◄─────────────► 高
                    │                   │
              高    │  Blocking    BusySpin
              │     │     ↑            ↑
    延  延迟   │     │     │            │
    迟  敏感   │     │  Timeout    Yielding
              │     │     ↑            ↑
              低    │  Sleeping        │
                    │                   │
                    └───────────────────┘

决策流程:
  1. 延迟要求 < 1μs？
     → CPU 核心充足？ → BusySpinWaitStrategy
     → CPU 受限？    → YieldingWaitStrategy

  2. 延迟要求 < 100μs？
     → YieldingWaitStrategy 或 SleepingWaitStrategy

  3. 延迟要求 > 100μs 或不敏感？
     → 需要超时？ → TimeoutBlockingWaitStrategy
     → 不需要？  → BlockingWaitStrategy 或 SleepingWaitStrategy
```

## 5.4 Log4j2 的选择

Log4j2 的 AsyncLogger 默认使用 `TimeoutBlockingWaitStrategy`：
- 日志写入对延迟不太敏感
- 需要节省 CPU（日志只是辅助功能）
- 超时机制可以处理异常情况

## 5.5 本章小结

WaitStrategy 是 Disruptor 的一个精妙设计，通过可插拔的策略模式，让用户根据场景选择最合适的等待行为。没有银弹，选择取决于你的延迟要求和 CPU 资源。

---
**上一章**：[Sequence 与并发控制](04-Sequence与并发控制.md)  
**下一章**：[EventProcessor 事件处理](06-EventProcessor事件处理.md)
