# 第四章：Sequence 与并发控制

## 4.1 Sequence 的设计

Sequence 是 Disruptor 中追踪进度的核心类。每个生产者和消费者都有自己的 Sequence。

### 为什么不用 AtomicLong？

`AtomicLong` 虽然也是 CAS 无锁的，但它没有解决伪共享问题：

```java
// JDK AtomicLong
public class AtomicLong extends Number {
    private volatile long value;
    // 没有 padding！多个 AtomicLong 可能共享同一个缓存行
}

// Disruptor Sequence（带缓存行填充）
class LhsPadding {
    protected long p1, p2, p3, p4, p5, p6, p7;
}
class Value extends LhsPadding {
    protected volatile long value;
}
class RhsPadding extends Value {
    protected long p8, p9, p10, p11, p12, p13, p14;
}
public class Sequence extends RhsPadding {
    // value 字段被 7 个 long 前置填充 + 7 个 long 后置填充包围
    // 7 * 8 = 56 bytes padding each side
    // 确保 value 独占一个 64-byte 缓存行
}
```

### 内存布局验证

```
Sequence 对象内存布局 (假设 64B 缓存行):

Offset  Field       Size
0       (header)    16B (对象头)
16      p1          8B
24      p2          8B
32      p3          8B
40      p4          8B
48      p5          8B
56      p6          8B
64      p7          8B   ← 第一个缓存行结束
72      value       8B   ← value 开始于新的缓存行！
80      p8          8B
88      p9          8B
...
128     p14         8B   ← 确保 value 不与后续数据共享缓存行
```

## 4.2 SingleProducerSequencer

单生产者场景下的序号分配器，是最高效的实现：

```java
// 简化的 SingleProducerSequencer.next()
public long next(int n) {
    long nextValue = this.nextValue;  // 非 volatile 读！性能极高
    long nextSequence = nextValue + n;
    long wrapPoint = nextSequence - bufferSize;
    long cachedGatingSequence = this.cachedValue;
    
    // 检查是否会追尾消费者
    if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue) {
        // 只在必要时才查询消费者的最小 sequence
        long minSequence;
        while (wrapPoint > (minSequence = Util.getMinimumSequence(
                gatingSequences, nextValue))) {
            LockSupport.parkNanos(1);  // 短暂等待
        }
        this.cachedValue = minSequence;  // 缓存结果
    }
    
    this.nextValue = nextSequence;
    return nextSequence;
}
```

**为什么不需要 CAS？**
- 只有一个生产者线程，没有竞争
- `nextValue` 字段是普通变量（非 volatile），避免了内存屏障
- 使用 `cachedValue` 缓存消费者位置，减少 volatile 读

## 4.3 MultiProducerSequencer

多生产者场景需要 CAS 来安全竞争序号：

```java
// 简化的 MultiProducerSequencer.next()
public long next(int n) {
    long current;
    long next;
    
    do {
        current = cursor.get();           // volatile 读
        next = current + n;
        long wrapPoint = next - bufferSize;
        long cachedGatingSequence = gatingSequenceCache.get();
        
        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > current) {
            long gatingSequence = Util.getMinimumSequence(
                    gatingSequences, current);
            if (wrapPoint > gatingSequence) {
                LockSupport.parkNanos(1);
                continue;  // 重试
            }
            gatingSequenceCache.set(gatingSequence);
        } else if (cursor.compareAndSet(current, next)) {
            break;  // CAS 成功，获得序号
        }
        // CAS 失败，重试
    } while (true);
    
    return next;
}
```

### 多生产者的可见性问题

```
问题场景:
  Producer1: next() → 获得 seq=5 → 写入数据中...（还没publish）
  Producer2: next() → 获得 seq=6 → 写入数据 → publish(6)
  
  此时消费者看到 cursor=6，但 seq=5 的数据还没准备好！
  
解决方案: availableBuffer
  MultiProducerSequencer 维护一个 int[] availableBuffer
  每个 slot 记录了最新一轮写入的标记
  
  publish(seq) {
      int index = calculateIndex(seq);
      int flag = calculateAvailabilityFlag(seq);
      availableBuffer[index] = flag;  // 标记为可用
  }
  
  isAvailable(seq) {
      int index = calculateIndex(seq);
      int flag = calculateAvailabilityFlag(seq);
      return availableBuffer[index] == flag;  // 检查标记
  }
```

## 4.4 SequenceBarrier 的协调机制

SequenceBarrier 是连接生产者和消费者的桥梁：

```java
// ProcessingSequenceBarrier.waitFor()
public long waitFor(long sequence) throws Exception {
    // 1. 检查是否被告知要停止
    checkAlert();
    
    // 2. 委托给 WaitStrategy 等待
    long availableSequence = waitStrategy.waitFor(
            sequence, cursorSequence, dependentSequence, this);
    
    // 3. 对于多生产者，还需要检查每个 slot 是否真的可用
    if (availableSequence < sequence) {
        return availableSequence;
    }
    
    // 4. 返回实际可消费的最大序号
    return sequencer.getHighestPublishedSequence(sequence, availableSequence);
}
```

### 消费者依赖链

```
场景: C3 依赖 C1 和 C2 都处理完毕

  [Producer] → [RingBuffer] → [C1] ─┐
                            → [C2] ─┴→ [C3]

C3 的 SequenceBarrier 同时追踪:
  - RingBuffer 的 cursor（生产者发布进度）
  - C1 的 Sequence（C1 消费进度）
  - C2 的 Sequence（C2 消费进度）

C3.waitFor(seq) 返回: min(cursor, C1.seq, C2.seq)
→ 确保 C3 只消费 C1 和 C2 都已处理过的事件
```

## 4.5 无锁的本质：内存屏障

Disruptor 使用 `volatile` 和 CAS 来实现无锁并发，其底层依赖于 CPU 的内存屏障：

```
volatile 写 = StoreStore + StoreLoad 屏障
  → 保证写入对其他线程可见

volatile 读 = LoadLoad + LoadStore 屏障  
  → 保证读取到最新值

CAS = Lock 前缀指令
  → 原子性的比较并交换

Disruptor 的序号流转:
  Producer: 写数据 → publish(volatile 写 cursor)
  Consumer: waitFor(volatile 读 cursor) → 读数据
  
  volatile 的内存语义保证了:
  Producer 写数据 happens-before publish
  waitFor returns happens-before Consumer 读数据
  
  → 数据可见性得到保证，无需加锁
```

## 4.6 本章小结

Disruptor 通过精巧的 Sequence 设计（缓存行填充）、针对性的 Sequencer 实现（单生产者无锁 / 多生产者 CAS）、以及基于 volatile 语义的内存可见性保证，实现了高效的并发控制。

---
**上一章**：[RingBuffer 原理详解](03-RingBuffer原理详解.md)  
**下一章**：[WaitStrategy 等待策略](05-WaitStrategy等待策略.md)
