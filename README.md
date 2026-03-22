# MaDisruptor - LMAX Disruptor 深度学习教程

> 一个完整的 LMAX Disruptor 学习项目，从原理到实战，从入门到精通。

## 项目简介

[LMAX Disruptor](https://github.com/LMAX-Exchange/disruptor) 是一个高性能的线程间消息传递框架，由 LMAX Exchange 开发。它能够在无锁的情况下实现每秒数百万次的消息传递，是 Java 高并发编程的经典框架。

本项目通过 **8个循序渐进的Demo** + **8章详细文档**，帮助你彻底理解 Disruptor 的设计原理和使用方法。

## 目录结构

```
madisruptor/
├── README.md                          # 项目总览（本文件）
├── pom.xml                            # Maven 配置
├── docs/                              # 教程文档
│   ├── 01-为什么需要Disruptor.md
│   ├── 02-核心概念与架构.md
│   ├── 03-RingBuffer原理详解.md
│   ├── 04-Sequence与并发控制.md
│   ├── 05-WaitStrategy等待策略.md
│   ├── 06-EventProcessor事件处理.md
│   ├── 07-高级特性与最佳实践.md
│   └── 08-手写简易Disruptor.md
├── src/main/java/com/madisruptor/
│   ├── demo01_helloworld/             # Demo 1: Hello World 入门
│   ├── demo02_ringbuffer/             # Demo 2: RingBuffer 深入
│   ├── demo03_multi_producer_consumer/ # Demo 3: 多生产者/多消费者
│   ├── demo04_waitstrategy/           # Demo 4: 等待策略对比
│   ├── demo05_handler_chain/          # Demo 5: 处理器链(管道)
│   ├── demo06_benchmark/             # Demo 6: 性能基准测试
│   ├── demo07_custom_disruptor/       # Demo 7: 手写简易 Disruptor
│   └── demo08_order_system/           # Demo 8: 实战-订单处理系统
└── src/test/java/com/madisruptor/     # 单元测试
```

## 学习路线图

```
                    ┌─────────────────────────────────────────────┐
                    │          MaDisruptor 学习路线                  │
                    └─────────────────────────────────────────────┘
                                        │
              ┌─────────────────────────┼─────────────────────────┐
              ▼                         ▼                         ▼
     ┌────────────────┐     ┌────────────────────┐     ┌──────────────────┐
     │  第一阶段: 入门  │     │  第二阶段: 原理深入  │     │  第三阶段: 实战精通 │
     └────────────────┘     └────────────────────┘     └──────────────────┘
              │                         │                         │
     ┌────────┴────────┐     ┌─────────┴─────────┐     ┌────────┴────────┐
     │ Doc 01 + Demo 01│     │ Doc 03 + Demo 02  │     │ Doc 07 + Demo 06│
     │ Doc 02 + Demo 01│     │ Doc 04 + Demo 03  │     │ Doc 08 + Demo 07│
     │                 │     │ Doc 05 + Demo 04  │     │        Demo 08  │
     │                 │     │ Doc 06 + Demo 05  │     │                 │
     └─────────────────┘     └───────────────────┘     └─────────────────┘
```

### 第一阶段：快速入门 (1-2 小时)

| 序号 | 文档 | Demo | 要点 |
|------|------|------|------|
| 1 | [为什么需要 Disruptor](docs/01-为什么需要Disruptor.md) | - | 传统队列的问题、Disruptor 的优势 |
| 2 | [核心概念与架构](docs/02-核心概念与架构.md) | [Demo 01: Hello World](src/main/java/com/madisruptor/demo01_helloworld/) | RingBuffer、Event、EventHandler、Sequencer |

### 第二阶段：原理深入 (3-5 小时)

| 序号 | 文档 | Demo | 要点 |
|------|------|------|------|
| 3 | [RingBuffer 原理详解](docs/03-RingBuffer原理详解.md) | [Demo 02: RingBuffer 深入](src/main/java/com/madisruptor/demo02_ringbuffer/) | 环形数组、位运算取模、缓存行填充 |
| 4 | [Sequence 与并发控制](docs/04-Sequence与并发控制.md) | [Demo 03: 多生产者/多消费者](src/main/java/com/madisruptor/demo03_multi_producer_consumer/) | Sequence、SequenceBarrier、CAS 无锁 |
| 5 | [WaitStrategy 等待策略](docs/05-WaitStrategy等待策略.md) | [Demo 04: 等待策略对比](src/main/java/com/madisruptor/demo04_waitstrategy/) | 各种等待策略的原理和选型 |
| 6 | [EventProcessor 事件处理](docs/06-EventProcessor事件处理.md) | [Demo 05: 处理器链](src/main/java/com/madisruptor/demo05_handler_chain/) | BatchEventProcessor、WorkProcessor、菱形依赖 |

### 第三阶段：实战精通 (3-5 小时)

| 序号 | 文档 | Demo | 要点 |
|------|------|------|------|
| 7 | [高级特性与最佳实践](docs/07-高级特性与最佳实践.md) | [Demo 06: 性能基准测试](src/main/java/com/madisruptor/demo06_benchmark/) | JMH 测试、调优技巧、常见陷阱 |
| 8 | [手写简易 Disruptor](docs/08-手写简易Disruptor.md) | [Demo 07: 手写简易 Disruptor](src/main/java/com/madisruptor/demo07_custom_disruptor/) | 从零实现核心功能，彻底理解原理 |
| 9 | - | [Demo 08: 订单处理系统](src/main/java/com/madisruptor/demo08_order_system/) | 真实业务场景实战 |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+

### 构建项目

```bash
git clone https://github.com/malinghan/madisruptor.git
cd madisruptor
mvn clean compile
```

### 运行 Demo

```bash
# Demo 01: Hello World
mvn exec:java -Dexec.mainClass="com.madisruptor.demo01_helloworld.HelloDisruptorDemo"

# Demo 02: RingBuffer 深入
mvn exec:java -Dexec.mainClass="com.madisruptor.demo02_ringbuffer.RingBufferDemo"

# Demo 03: 多生产者/多消费者
mvn exec:java -Dexec.mainClass="com.madisruptor.demo03_multi_producer_consumer.MultiProducerConsumerDemo"

# Demo 04: 等待策略对比
mvn exec:java -Dexec.mainClass="com.madisruptor.demo04_waitstrategy.WaitStrategyDemo"

# Demo 05: 处理器链
mvn exec:java -Dexec.mainClass="com.madisruptor.demo05_handler_chain.HandlerChainDemo"

# Demo 06: 性能基准测试
mvn exec:java -Dexec.mainClass="com.madisruptor.demo06_benchmark.BenchmarkDemo"

# Demo 07: 手写简易 Disruptor
mvn exec:java -Dexec.mainClass="com.madisruptor.demo07_custom_disruptor.CustomDisruptorDemo"

# Demo 08: 订单处理系统
mvn exec:java -Dexec.mainClass="com.madisruptor.demo08_order_system.OrderSystemDemo"
```

## Disruptor 核心架构概览

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        Disruptor 核心架构                                 │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   Producer(s)                                                            │
│   ┌──────────┐  ┌──────────┐                                            │
│   │Producer 1│  │Producer 2│  ...                                       │
│   └────┬─────┘  └────┬─────┘                                            │
│        │              │                                                  │
│        ▼              ▼                                                  │
│   ┌─────────────────────────────────┐                                   │
│   │          Sequencer              │  (协调生产者发布)                    │
│   │   ┌───────────────────────┐     │                                   │
│   │   │  SingleProducerSeq    │     │  单生产者: 无竞争，最高性能         │
│   │   │  MultiProducerSeq     │     │  多生产者: CAS 无锁竞争            │
│   │   └───────────────────────┘     │                                   │
│   └─────────────┬───────────────────┘                                   │
│                 │                                                        │
│                 ▼                                                        │
│   ┌─────────────────────────────────┐                                   │
│   │         RingBuffer              │  (预分配的环形数组)                  │
│   │                                 │                                   │
│   │    ┌───┬───┬───┬───┬───┬───┐   │                                   │
│   │    │ 0 │ 1 │ 2 │ 3 │ 4 │ 5 │   │  size = 2^n (位运算取模)          │
│   │    └───┴───┴───┴───┴───┴───┘   │                                   │
│   │         ↑ 循环写入 ↓             │                                   │
│   └─────────────┬───────────────────┘                                   │
│                 │                                                        │
│                 ▼                                                        │
│   ┌─────────────────────────────────┐                                   │
│   │      SequenceBarrier            │  (协调消费者等待)                    │
│   │   ┌───────────────────────┐     │                                   │
│   │   │    WaitStrategy       │     │  BusySpin / Yield / Block / ...   │
│   │   └───────────────────────┘     │                                   │
│   └─────────────┬───────────────────┘                                   │
│                 │                                                        │
│        ┌────────┼────────┐                                              │
│        ▼        ▼        ▼                                              │
│   Consumer(s)                                                            │
│   ┌──────────┐┌──────────┐┌──────────┐                                  │
│   │Consumer 1││Consumer 2││Consumer 3│                                  │
│   │(EventHdl)││(EventHdl)││(WorkHdl) │                                  │
│   └──────────┘└──────────┘└──────────┘                                  │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

## 为什么 Disruptor 这么快？

| 优化手段 | 传统 BlockingQueue | Disruptor |
|----------|-------------------|-----------|
| 内存分配 | 每次入队 new 对象 | **预分配 Event 对象，循环复用** |
| 缓存友好 | 链表结构，随机内存访问 | **数组结构，顺序访问，CPU 缓存友好** |
| 伪共享 | 无处理 | **CacheLine Padding，避免伪共享** |
| 锁机制 | synchronized / ReentrantLock | **CAS 无锁 + Sequence 协调** |
| 等待策略 | 固定的 wait/notify | **可插拔的 WaitStrategy** |
| 批量处理 | 逐条处理 | **批量读取，提升吞吐量** |

## 核心概念速查

| 概念 | 说明 |
|------|------|
| **RingBuffer** | 环形数组，存储 Event 的容器。大小必须为 2 的 N 次方 |
| **Event** | 存储在 RingBuffer 中的数据载体（POJO） |
| **EventFactory** | 创建 Event 的工厂，用于预填充 RingBuffer |
| **EventHandler** | 事件处理器接口，消费者实现此接口处理事件 |
| **EventTranslator** | 将外部数据翻译到 Event 中的转换器 |
| **Sequence** | 递增的序号，标识 RingBuffer 中的位置（带 CacheLine Padding） |
| **Sequencer** | 协调生产者的序号分配（Single / Multi） |
| **SequenceBarrier** | 协调消费者等待可用事件的屏障 |
| **WaitStrategy** | 消费者等待新事件时的策略 |
| **EventProcessor** | 事件处理的执行单元（BatchEventProcessor / WorkProcessor） |

## 参考资料

- [LMAX Disruptor GitHub](https://github.com/LMAX-Exchange/disruptor)
- [Disruptor Technical Paper](https://lmax-exchange.github.io/disruptor/disruptor.html)
- [Mechanical Sympathy Blog](https://mechanical-sympathy.blogspot.com/)
- [Martin Fowler - The LMAX Architecture](https://martinfowler.com/articles/lmax.html)

## License

MIT License
