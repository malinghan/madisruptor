# 第六章：EventProcessor 事件处理

## 6.1 EventProcessor 体系

```
EventProcessor (接口)
├── BatchEventProcessor  - 批量事件处理器，1:1 对应 EventHandler
└── WorkProcessor        - 工作者模式处理器，多个竞争消费
```

## 6.2 BatchEventProcessor

BatchEventProcessor 是最常用的事件处理器，它会在一个独立线程中运行，批量消费事件。

### 核心处理循环

```java
// 简化的 BatchEventProcessor.run()
public void run() {
    T event = null;
    long nextSequence = sequence.get() + 1L;
    
    while (true) {
        try {
            // 1. 等待可用的序号（可能批量返回多个）
            long availableSequence = sequenceBarrier.waitFor(nextSequence);
            
            // 2. 批量处理：从 nextSequence 到 availableSequence
            while (nextSequence <= availableSequence) {
                event = dataProvider.get(nextSequence);
                boolean endOfBatch = (nextSequence == availableSequence);
                
                // 3. 调用用户的 EventHandler
                eventHandler.onEvent(event, nextSequence, endOfBatch);
                nextSequence++;
            }
            
            // 4. 更新消费进度
            sequence.set(availableSequence);
            
        } catch (TimeoutException e) {
            // 超时处理
        } catch (AlertException e) {
            // 被通知停止
            if (running.get() != RUNNING) break;
        } catch (Throwable e) {
            // 异常处理
            exceptionHandler.handleEventException(e, nextSequence, event);
            sequence.set(nextSequence);
            nextSequence++;
        }
    }
}
```

### 批量处理的威力

```
假设: 生产者发布了 seq 5, 6, 7, 8, 9, 10
消费者当前在 seq 4

传统方式（逐条处理）:
  wait(5) → process(5)
  wait(6) → process(6)
  ...
  wait(10) → process(10)
  → 6 次 wait 调用

Disruptor 批量方式:
  waitFor(5) → 返回 10（因为到 10 都已可用）
  process(5), process(6), ..., process(10)
  → 只需 1 次 wait 调用！

性能优势:
  ✅ 减少系统调用和上下文切换
  ✅ 利用 CPU 缓存局部性
  ✅ endOfBatch 标志允许用户在批次结束时做批量提交
```

### endOfBatch 的妙用

```java
public class BatchingHandler implements EventHandler<OrderEvent> {
    private final List<OrderEvent> batch = new ArrayList<>();
    
    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        batch.add(event);
        
        if (endOfBatch) {
            // 批量写入数据库，大幅提升吞吐量
            database.batchInsert(batch);
            batch.clear();
        }
    }
}
```

## 6.3 WorkProcessor 与 WorkerPool

WorkProcessor 用于多个消费者竞争消费同一事件（类似线程池的工作模式）。

```
EventHandler 模式 (广播):
  Event → Handler1 处理
       → Handler2 处理  （每个 Handler 都会处理每个事件）
       → Handler3 处理

WorkProcessor 模式 (竞争):
  Event → Worker1 或 Worker2 或 Worker3 处理
       （每个事件只被一个 Worker 处理）
```

```java
// WorkerPool 使用示例
WorkHandler<OrderEvent>[] workers = new WorkHandler[3];
workers[0] = new OrderWorker("Worker-1");
workers[1] = new OrderWorker("Worker-2");
workers[2] = new OrderWorker("Worker-3");

WorkerPool<OrderEvent> workerPool = new WorkerPool<>(
    ringBuffer, barrier, exceptionHandler, workers);

// 每个 Worker 通过 CAS 竞争序号
// Worker1: CAS(10, 11) → 成功 → 处理 event[11]
// Worker2: CAS(10, 11) → 失败 → CAS(11, 12) → 成功 → 处理 event[12]
```

## 6.4 消费者组合模式

Disruptor DSL 提供了灵活的消费者组合方式：

### 串行处理（Pipeline）

```java
// C1 → C2 → C3
disruptor.handleEventsWith(handler1)
         .then(handler2)
         .then(handler3);
```

```
  [Producer] → [C1] → [C2] → [C3]
  
  C2 等待 C1 处理完毕才开始
  C3 等待 C2 处理完毕才开始
```

### 并行处理

```java
// C1, C2, C3 同时处理
disruptor.handleEventsWith(handler1, handler2, handler3);
```

```
              → [C1]
  [Producer] → [C2]   （三个消费者并行，每个都处理所有事件）
              → [C3]
```

### 菱形处理（Diamond）

```java
// C1 和 C2 并行，C3 等待 C1 和 C2 都完成
disruptor.handleEventsWith(handler1, handler2)
         .then(handler3);
```

```
              → [C1] ─┐
  [Producer]           ├→ [C3]
              → [C2] ─┘

  C3 等待 C1 和 C2 都处理完毕
```

### 六边形处理

```java
// 更复杂的依赖关系
EventHandlerGroup<Event> group1 = disruptor.handleEventsWith(h1, h2);
EventHandlerGroup<Event> group2 = disruptor.after(h1).handleEventsWith(h3);
disruptor.after(h2, h3).handleEventsWith(h4);
```

```
              → [H1] → [H3] ─┐
  [Producer]                   ├→ [H4]
              → [H2] ─────────┘
```

## 6.5 异常处理

```java
// 自定义异常处理器
public class MyExceptionHandler implements ExceptionHandler<OrderEvent> {
    @Override
    public void handleEventException(Throwable ex, long sequence, OrderEvent event) {
        // 记录日志，决定是否继续
        log.error("处理事件异常: seq={}, event={}", sequence, event, ex);
    }
    
    @Override
    public void handleOnStartException(Throwable ex) {
        log.error("启动异常", ex);
    }
    
    @Override
    public void handleOnShutdownException(Throwable ex) {
        log.error("关闭异常", ex);
    }
}

disruptor.setDefaultExceptionHandler(new MyExceptionHandler());
```

## 6.6 本章小结

EventProcessor 的设计实现了高效的批量消费和灵活的消费者组合。通过 DSL 风格的 API，用户可以轻松构建复杂的事件处理管道。

---
**上一章**：[WaitStrategy 等待策略](05-WaitStrategy等待策略.md)  
**下一章**：[高级特性与最佳实践](07-高级特性与最佳实践.md)
