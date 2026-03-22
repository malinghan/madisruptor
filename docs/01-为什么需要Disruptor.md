# 第一章：为什么需要 Disruptor

## 1.1 传统并发队列的痛点

在 Java 并发编程中，线程间通信最常用的方式是通过队列（Queue）。JDK 提供了多种并发队列：

- `ArrayBlockingQueue` - 基于数组的有界阻塞队列
- `LinkedBlockingQueue` - 基于链表的可选有界阻塞队列
- `ConcurrentLinkedQueue` - 基于链表的无界非阻塞队列

但在超高吞吐量场景下，这些队列存在严重的性能瓶颈：

### 问题 1：锁竞争（Lock Contention）

```java
// ArrayBlockingQueue 的 put 方法
public void put(E e) throws InterruptedException {
    final ReentrantLock lock = this.lock;
    lock.lockInterruptibly();  // <-- 锁！所有生产者和消费者都竞争这把锁
    try {
        while (count == items.length)
            notFull.await();
        enqueue(e);
    } finally {
        lock.unlock();
    }
}
```

**问题**：所有的生产者和消费者都要竞争同一把锁，在高并发下线程频繁阻塞唤醒，上下文切换开销巨大。

### 问题 2：伪共享（False Sharing）

```
CPU Cache Line (64 bytes)
┌──────────────────────────────────────┐
│  head (8B)  │  tail (8B)  │  其他...  │
└──────────────────────────────────────┘
       ↑               ↑
    Thread A         Thread B
    (生产者)          (消费者)
```

在 `ArrayBlockingQueue` 中，`putIndex` 和 `takeIndex` 等变量可能存在于同一个 CPU 缓存行（Cache Line，通常 64 字节）中。当一个线程修改了 `putIndex`，整个缓存行都会失效，迫使另一个线程重新从主存加载 `takeIndex`，即使 `takeIndex` 并没有被修改。

### 问题 3：频繁的内存分配与 GC

```java
// LinkedBlockingQueue 每次入队都 new 一个 Node
private void enqueue(Node<E> node) {
    last = last.next = node;  // <-- new Node<E>(e) 在调用处
}
```

**问题**：每次入队都创建新的 `Node` 对象，大量临时对象给 GC 带来压力，导致 STW（Stop-The-World）暂停。

### 问题 4：缓存不友好

`LinkedBlockingQueue` 使用链表结构，节点在内存中随机分布。CPU 预取机制无法有效工作，频繁的缓存未命中（Cache Miss）严重影响性能。

```
内存布局对比：

LinkedBlockingQueue（链表）:
[Node1] → ... → [Node2] → ... → [Node3]     随机分布，缓存不友好
  0x100         0x900         0x400

RingBuffer（数组）:
[Slot0][Slot1][Slot2][Slot3][Slot4][Slot5]    连续分布，缓存友好
 0x100 0x120  0x140  0x160  0x180  0x1A0
```

## 1.2 Disruptor 的诞生

LMAX Exchange 是一家外汇交易平台，需要处理极高吞吐量的交易消息。传统的队列方案无法满足需求（延迟要求 < 1 微秒）。

2011 年，LMAX 团队开源了 Disruptor 框架，并发表了技术论文。其核心思想是：

> **通过精心的架构设计，消除并发中的各种性能杀手，让机械同感（Mechanical Sympathy）成为现实。**

### 什么是 Mechanical Sympathy（机械同感）？

这个术语来自赛车运动——最快的车手不仅懂驾驶技术，还深刻理解赛车的机械原理。

在软件领域，Mechanical Sympathy 意味着：**编写的软件要与底层硬件（CPU 缓存、内存模型）和谐工作**。

## 1.3 Disruptor 的核心优化思路

| 传统队列的问题 | Disruptor 的解决方案 |
|---------------|---------------------|
| 锁竞争 | CAS 无锁 + Sequence 协调 |
| 伪共享 | Cache Line Padding（缓存行填充） |
| 频繁 GC | 预分配 Event 对象，循环复用 |
| 缓存不友好 | 数组 + 顺序访问 |
| 固定的等待策略 | 可插拔的 WaitStrategy |
| 逐条处理 | 批量消费（Batching） |

### 性能对比数据

在 LMAX 的测试中，Disruptor 相比 `ArrayBlockingQueue`：

- **单生产者单消费者**：Disruptor 快约 **5-10 倍**
- **多生产者多消费者**：Disruptor 快约 **10-20 倍**
- **延迟**：Disruptor P99 延迟通常在 **纳秒级别**

## 1.4 Disruptor 的应用场景

1. **金融交易系统** - LMAX Exchange 的核心引擎
2. **日志框架** - Log4j2 使用 Disruptor 作为 AsyncLogger 的底层实现
3. **消息中间件** - 各种 MQ 的内部高性能缓冲
4. **实时数据处理** - 高频数据采集和处理管道
5. **游戏服务器** - 高并发事件处理

## 1.5 本章小结

传统并发队列存在锁竞争、伪共享、GC 压力、缓存不友好等问题。Disruptor 通过一系列精妙的设计，从根本上解决了这些问题，实现了惊人的性能。

接下来的章节中，我们将深入每一个核心概念，理解 Disruptor 是如何做到这一切的。

---
**下一章**：[核心概念与架构](02-核心概念与架构.md)
