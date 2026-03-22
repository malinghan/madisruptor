# 第八章：手写简易 Disruptor

## 8.1 为什么要手写？

> "I hear and I forget. I see and I remember. I do and I understand." - 孔子

通过从零实现一个简化版的 Disruptor，可以彻底理解其核心原理。我们将实现以下核心功能：

- RingBuffer（预分配环形数组）
- Sequence（带缓存行填充的序号）
- 单生产者序号分配
- 消费者等待与消费
- 简单的 WaitStrategy

## 8.2 实现计划

```
我们的 MiniDisruptor 包含:
  
  MiniSequence          - 带 padding 的序号
  MiniRingBuffer        - 环形缓冲区
  MiniEventFactory      - 事件工厂接口
  MiniEventHandler      - 事件处理器接口
  MiniWaitStrategy      - 等待策略接口
  MiniSequenceBarrier   - 序号屏障
  MiniBatchProcessor    - 批量事件处理器
  MiniDisruptor         - 门面类，整合所有组件
```

## 8.3 核心代码讲解

### Step 1: MiniSequence

```java
// 关键: 使用继承链实现缓存行填充
public class MiniSequence {
    // 前置填充
    private long p1, p2, p3, p4, p5, p6, p7;
    
    // 实际值 (volatile 保证可见性)
    private volatile long value = -1;
    
    // 后置填充
    private long p8, p9, p10, p11, p12, p13, p14;
    
    public long get() { return value; }
    public void set(long value) { this.value = value; }
    
    // CAS 操作
    public boolean compareAndSet(long expected, long update) {
        return UNSAFE.compareAndSwapLong(this, VALUE_OFFSET, expected, update);
    }
}
```

### Step 2: MiniRingBuffer

```java
public class MiniRingBuffer<E> {
    private final Object[] entries;  // 预分配数组
    private final int bufferSize;
    private final int indexMask;     // bufferSize - 1
    private final MiniSequence cursor = new MiniSequence();
    
    public MiniRingBuffer(MiniEventFactory<E> factory, int bufferSize) {
        this.bufferSize = bufferSize;
        this.indexMask = bufferSize - 1;
        this.entries = new Object[bufferSize];
        // 预填充
        for (int i = 0; i < bufferSize; i++) {
            entries[i] = factory.newInstance();
        }
    }
    
    @SuppressWarnings("unchecked")
    public E get(long sequence) {
        return (E) entries[(int)(sequence & indexMask)];
    }
    
    public long next() {
        // 单生产者，直接递增
        long next = cursor.get() + 1;
        // 检查是否追尾消费者...
        cursor.set(next);
        return next;
    }
    
    public void publish(long sequence) {
        cursor.set(sequence);
    }
}
```

### Step 3: 消费者处理循环

```java
public class MiniBatchProcessor<E> implements Runnable {
    private final MiniRingBuffer<E> ringBuffer;
    private final MiniEventHandler<E> handler;
    private final MiniSequence sequence = new MiniSequence();
    
    @Override
    public void run() {
        long nextSequence = sequence.get() + 1;
        while (running) {
            long cursor = ringBuffer.getCursor();
            // 批量消费
            while (nextSequence <= cursor) {
                E event = ringBuffer.get(nextSequence);
                handler.onEvent(event, nextSequence, nextSequence == cursor);
                nextSequence++;
            }
            sequence.set(nextSequence - 1);
            // 等待策略...
        }
    }
}
```

## 8.4 完整实现

完整的代码实现见 Demo 07:
[`demo07_custom_disruptor`](../src/main/java/com/madisruptor/demo07_custom_disruptor/)

包含：
1. `MiniSequence.java` - 带缓存行填充的序号
2. `MiniRingBuffer.java` - 简化版环形缓冲区
3. `MiniEventFactory.java` - 事件工厂接口
4. `MiniEventHandler.java` - 事件处理器接口
5. `MiniWaitStrategy.java` - 等待策略接口
6. `MiniSequenceBarrier.java` - 序号屏障
7. `MiniBatchProcessor.java` - 批量事件处理器
8. `MiniDisruptor.java` - 门面整合类
9. `CustomDisruptorDemo.java` - 演示程序

## 8.5 与官方 Disruptor 的差异

| 特性 | MiniDisruptor | 官方 Disruptor |
|------|--------------|---------------|
| 生产者 | 仅单生产者 | 单/多生产者 |
| 消费者 | 单消费者 | 多消费者 + 依赖链 |
| 等待策略 | Yield + Sleep | 5+ 种策略 |
| 缓存行填充 | 基本实现 | 完善的继承链填充 |
| 异常处理 | 简单处理 | 完善的异常策略 |
| DSL API | 无 | 流式 API |
| Unsafe | VarHandle | Unsafe |

## 8.6 本章小结

通过手写简易 Disruptor，我们深入理解了：
- 缓存行填充如何避免伪共享
- 环形数组如何实现无 GC 的事件传递
- 序号机制如何协调生产者和消费者
- 批量消费如何提升吞吐量

恭喜你完成了整个 Disruptor 学习之旅！

---
**上一章**：[高级特性与最佳实践](07-高级特性与最佳实践.md)
