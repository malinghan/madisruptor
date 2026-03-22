# 第三章：RingBuffer 原理详解

## 3.1 为什么是环形数组？

### 传统队列 vs 环形数组

```
传统队列（数组实现）:
  入队 → [_][_][A][B][C][_][_][_] ← 出队
          ↑             ↑
         head          tail
  问题: 空间浪费，需要数组拷贝或循环处理

环形数组:
         ┌───┐
    ┌───►│ 7 │◄───┐
    │    └───┘    │
  ┌───┐        ┌───┐
  │ 6 │        │ 0 │   Producer → cursor (写到哪了)
  └───┘        └───┘   Consumer → sequence (读到哪了)
  ┌───┐        ┌───┐
  │ 5 │        │ 1 │
  └───┘        └───┘
    │    ┌───┐    │
    └───►│ 4 │◄───┘
         └───┘
    ┌───┐    ┌───┐
    │ 3 │────│ 2 │
    └───┘    └───┘
  
  优势: 内存预分配 + 连续访问 + 无需移动数据
```

环形数组的核心优势：
1. **固定大小，预分配** - 一次性分配所有内存，避免 GC
2. **连续内存** - CPU 缓存预取友好
3. **通过取模实现循环** - 只需维护一个递增的 sequence 即可

## 3.2 位运算取模的妙用

### 为什么 bufferSize 必须是 2 的幂？

```java
// 普通取模运算
index = sequence % bufferSize;    // 除法运算，CPU 耗时较长

// 位运算取模（当 bufferSize 是 2 的幂时）
index = sequence & (bufferSize - 1);  // 位与运算，1个时钟周期

// 原理:
// bufferSize = 8  → 二进制: 1000
// bufferSize - 1  → 二进制: 0111 (掩码)
//
// sequence = 13   → 二进制: 1101
// 13 & 7          → 二进制: 0101 = 5
// 13 % 8          →                 5  ✓ 结果一致！
```

性能差距：位运算只需 1 个 CPU 时钟周期，而除法运算需要 20-90 个时钟周期。在每秒数百万次操作下，这个差距非常显著。

## 3.3 缓存行填充（Cache Line Padding）

### 什么是伪共享？

现代 CPU 以 **缓存行（Cache Line）** 为单位从内存加载数据，通常为 64 字节。

```
问题场景：
┌───────────────────── Cache Line (64B) ─────────────────────┐
│  cursorSequence (8B)  │  consumerSequence (8B)  │  其他...  │
└─────────────────────────────────────────────────────────────┘
        ↑                         ↑
     Thread A (Producer)       Thread B (Consumer)
     修改 cursor               读取 consumer seq

Thread A 修改 cursor → 整个 Cache Line 失效
→ Thread B 必须重新加载 consumer seq（虽然它没被修改！）
→ 这就是 "伪共享"
```

### Disruptor 的解决方案

```java
// Disruptor 的 Sequence 类使用填充避免伪共享
abstract class RingBufferPad {
    protected long p1, p2, p3, p4, p5, p6, p7;
}

abstract class RingBufferFields<E> extends RingBufferPad {
    // 实际的数据字段
    private final long indexMask;
    private final Object[] entries;
    protected final int bufferSize;
    protected final Sequencer sequencer;
}

public final class RingBuffer<E> extends RingBufferFields<E> {
    // 后置填充
    protected long p1, p2, p3, p4, p5, p6, p7;
}
```

填充后的内存布局：

```
┌─── Cache Line 1 ───┐┌─── Cache Line 2 ───┐┌─── Cache Line 3 ───┐
│  p1 p2 p3 p4       ││  实际数据 value     ││  p5 p6 p7          │
│  (前置填充)          ││  (独占一个缓存行)   ││  (后置填充)         │
└─────────────────────┘└─────────────────────┘└─────────────────────┘
```

**效果**：每个 Sequence 的 `value` 独占一个缓存行，不同线程修改不同的 Sequence 不会互相干扰。

## 3.4 预分配与对象复用

### 传统方式 vs Disruptor

```java
// 传统方式：每次都 new
queue.put(new OrderEvent(orderId, price));  // GC 压力大

// Disruptor：预分配 + 复用
// 初始化时
for (int i = 0; i < bufferSize; i++) {
    entries[i] = eventFactory.newInstance();  // 一次性创建
}

// 发布时（不 new，只是覆盖写入）
long seq = ringBuffer.next();
OrderEvent event = ringBuffer.get(seq);  // 获取预分配的对象
event.setOrderId(orderId);               // 覆盖写入
event.setPrice(price);
ringBuffer.publish(seq);                 // 发布
```

**优势**：
- 零内存分配 - 发布事件时不产生任何垃圾对象
- GC 友好 - 预分配的对象长期存活在老年代
- 缓存友好 - 数组中对象内存布局连续

## 3.5 RingBuffer 的核心方法

```java
// 发布流程
long sequence = ringBuffer.next();        // 1. 申请下一个序号
// long sequence = ringBuffer.next(n);    // 批量申请 n 个序号
try {
    Event event = ringBuffer.get(sequence); // 2. 获取该位置的事件
    event.set(...);                         // 3. 填充数据
} finally {
    ringBuffer.publish(sequence);           // 4. 发布（finally 确保发布）
}

// 推荐方式：使用 EventTranslator（更安全）
ringBuffer.publishEvent((event, sequence, arg) -> {
    event.setOrderId(arg);
}, orderId);
```

## 3.6 溢出与追赶保护

当生产者写得太快，要"追尾"消费者时，会怎样？

```
假设 bufferSize = 8

cursor (生产者) = 15
最慢消费者 sequence = 7

生产者想写 sequence=16，对应 index = 16 & 7 = 0
最慢消费者 sequence=7，对应 index = 7 & 7 = 7

此时 index 0 的数据已经被消费了（消费者在 7），可以安全写入。

如果：
cursor = 15
最慢消费者 sequence = 7
生产者想写 sequence = 16

16 - 8 = 8 > 7 (最慢消费者)
→ 会覆盖未消费的数据！
→ next() 方法会阻塞等待，直到消费者跟上

保护条件: next_sequence - bufferSize <= min(消费者sequences)
```

## 3.7 本章小结

RingBuffer 是 Disruptor 的基石。通过环形数组 + 预分配 + 位运算取模 + 缓存行填充，实现了极致的性能优化。理解了 RingBuffer，就理解了 Disruptor 高性能的一半秘密。

---
**上一章**：[核心概念与架构](02-核心概念与架构.md)  
**下一章**：[Sequence 与并发控制](04-Sequence与并发控制.md)
