# 이벤트 드리븐 아키텍처와 메시지 큐

## 이벤트 드리븐 아키텍처 개요

### 이벤트 드리븐 아키텍처란?
**Event-Driven Architecture (EDA)**는 이벤트의 생성, 감지, 소비를 통해 애플리케이션 컴포넌트 간의 통신을 수행하는 아키텍처 패턴입니다. 시스템 간 느슨한 결합을 제공하고 확장성과 유연성을 크게 향상시킵니다.

### 핵심 구성 요소
```
Event Producer → Event Router → Event Consumer
     ↓              ↓              ↓
 주문 서비스    →  Message Queue → 재고 서비스
                      ↓          → 결제 서비스  
                   Event Store   → 알림 서비스
```

## 이벤트 설계와 구현

### 1. 도메인 이벤트 설계
```java
// 기본 이벤트 인터페이스
public interface DomainEvent {
    String getEventId();
    String getEventType();
    LocalDateTime getOccurredAt();
    String getAggregateId();
    Long getVersion();
}

// 추상 이벤트 클래스
public abstract class AbstractDomainEvent implements DomainEvent {
    private final String eventId;
    private final String eventType;
    private final LocalDateTime occurredAt;
    private final String aggregateId;
    private final Long version;
    
    protected AbstractDomainEvent(String aggregateId, Long version) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = this.getClass().getSimpleName();
        this.occurredAt = LocalDateTime.now();
        this.aggregateId = aggregateId;
        this.version = version;
    }
    
    // getters...
}

// 구체적인 도메인 이벤트들
public class OrderCreatedEvent extends AbstractDomainEvent {
    private final String customerId;
    private final BigDecimal totalAmount;
    private final List<OrderItem> items;
    private final String shippingAddress;
    
    public OrderCreatedEvent(String orderId, Long version, String customerId, 
                           BigDecimal totalAmount, List<OrderItem> items, String shippingAddress) {
        super(orderId, version);
        this.customerId = customerId;
        this.totalAmount = totalAmount;
        this.items = items;
        this.shippingAddress = shippingAddress;
    }
    
    // getters...
}

public class OrderCancelledEvent extends AbstractDomainEvent {
    private final String reason;
    private final String cancelledBy;
    
    public OrderCancelledEvent(String orderId, Long version, String reason, String cancelledBy) {
        super(orderId, version);
        this.reason = reason;
        this.cancelledBy = cancelledBy;
    }
    
    // getters...
}

public class PaymentProcessedEvent extends AbstractDomainEvent {
    private final String paymentId;
    private final BigDecimal amount;
    private final PaymentStatus status;
    private final String paymentMethod;
    
    public PaymentProcessedEvent(String orderId, Long version, String paymentId,
                               BigDecimal amount, PaymentStatus status, String paymentMethod) {
        super(orderId, version);
        this.paymentId = paymentId;
        this.amount = amount;
        this.status = status;
        this.paymentMethod = paymentMethod;
    }
    
    // getters...
}
```

### 2. 이벤트 발행자 구현
```java
// 이벤트 발행 인터페이스
public interface EventPublisher {
    void publish(DomainEvent event);
    void publishAll(List<DomainEvent> events);
}

// Spring Events를 활용한 동기식 발행자
@Component
public class SpringEventPublisher implements EventPublisher {
    
    private final ApplicationEventPublisher applicationEventPublisher;
    
    public SpringEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }
    
    @Override
    public void publish(DomainEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
    
    @Override
    public void publishAll(List<DomainEvent> events) {
        events.forEach(this::publish);
    }
}

// 메시지 큐를 활용한 비동기 발행자  
@Component
public class MessageQueueEventPublisher implements EventPublisher {
    
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    
    public MessageQueueEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void publish(DomainEvent event) {
        try {
            String routingKey = generateRoutingKey(event);
            String eventJson = objectMapper.writeValueAsString(event);
            
            // 메시지 속성 설정
            MessageProperties properties = new MessageProperties();
            properties.setContentType("application/json");
            properties.setHeader("eventType", event.getEventType());
            properties.setHeader("eventId", event.getEventId());
            properties.setHeader("occurredAt", event.getOccurredAt().toString());
            
            Message message = new Message(eventJson.getBytes(), properties);
            
            rabbitTemplate.send("events.exchange", routingKey, message);
            
        } catch (JsonProcessingException e) {
            throw new EventPublishException("이벤트 직렬화 실패", e);
        }
    }
    
    private String generateRoutingKey(DomainEvent event) {
        // 이벤트 타입에 따른 라우팅 키 생성
        return event.getEventType().toLowerCase().replace("event", "");
    }
    
    @Override
    public void publishAll(List<DomainEvent> events) {
        events.forEach(this::publish);
    }
}
```

### 3. 애그리게이트에서 이벤트 생성
```java
public class Order {
    private String id;
    private String customerId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private List<OrderItem> items;
    private Long version;
    
    // 도메인 이벤트 수집용
    private final List<DomainEvent> domainEvents = new ArrayList<>();
    
    // 주문 생성
    public static Order create(String customerId, List<OrderItem> items) {
        Order order = new Order();
        order.id = UUID.randomUUID().toString();
        order.customerId = customerId;
        order.items = new ArrayList<>(items);
        order.status = OrderStatus.PENDING;
        order.totalAmount = calculateTotalAmount(items);
        order.version = 0L;
        
        // 도메인 이벤트 생성
        order.addDomainEvent(new OrderCreatedEvent(
            order.id, order.version, order.customerId, 
            order.totalAmount, order.items, "주소정보"
        ));
        
        return order;
    }
    
    // 주문 확정
    public void confirm() {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("주문을 확정할 수 없는 상태입니다: " + this.status);
        }
        
        this.status = OrderStatus.CONFIRMED;
        this.version++;
        
        addDomainEvent(new OrderConfirmedEvent(this.id, this.version, this.totalAmount));
    }
    
    // 주문 취소
    public void cancel(String reason, String cancelledBy) {
        if (this.status == OrderStatus.SHIPPED || this.status == OrderStatus.DELIVERED) {
            throw new IllegalStateException("배송 중이거나 배송완료된 주문은 취소할 수 없습니다");
        }
        
        this.status = OrderStatus.CANCELLED;
        this.version++;
        
        addDomainEvent(new OrderCancelledEvent(this.id, this.version, reason, cancelledBy));
    }
    
    // 결제 완료 처리
    public void markPaymentCompleted(String paymentId, PaymentMethod paymentMethod) {
        if (this.status != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("확정되지 않은 주문의 결제를 완료할 수 없습니다");
        }
        
        this.status = OrderStatus.PAID;
        this.version++;
        
        addDomainEvent(new OrderPaymentCompletedEvent(
            this.id, this.version, paymentId, this.totalAmount, paymentMethod
        ));
    }
    
    private void addDomainEvent(DomainEvent event) {
        this.domainEvents.add(event);
    }
    
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }
    
    public void clearDomainEvents() {
        this.domainEvents.clear();
    }
    
    private static BigDecimal calculateTotalAmount(List<OrderItem> items) {
        return items.stream()
                   .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                   .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

### 4. 애플리케이션 서비스에서 이벤트 발행
```java
@Service
@Transactional
public class OrderApplicationService {
    
    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;
    
    public OrderApplicationService(OrderRepository orderRepository, EventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }
    
    public String createOrder(CreateOrderCommand command) {
        // 도메인 객체 생성 (이벤트 포함)
        Order order = Order.create(command.getCustomerId(), command.getItems());
        
        // 영속화
        Order savedOrder = orderRepository.save(order);
        
        // 도메인 이벤트 발행
        eventPublisher.publishAll(savedOrder.getDomainEvents());
        savedOrder.clearDomainEvents();
        
        return savedOrder.getId();
    }
    
    public void confirmOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("주문을 찾을 수 없습니다: " + orderId));
        
        order.confirm();
        
        orderRepository.save(order);
        eventPublisher.publishAll(order.getDomainEvents());
        order.clearDomainEvents();
    }
    
    public void cancelOrder(String orderId, String reason, String cancelledBy) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("주문을 찾을 수 없습니다: " + orderId));
        
        order.cancel(reason, cancelledBy);
        
        orderRepository.save(order);
        eventPublisher.publishAll(order.getDomainEvents());
        order.clearDomainEvents();
    }
}
```

## 이벤트 소비자 구현

### 1. Spring Events 기반 동기 처리
```java
@Component
public class OrderEventHandler {
    
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    
    public OrderEventHandler(InventoryService inventoryService, 
                           PaymentService paymentService,
                           NotificationService notificationService) {
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.notificationService = notificationService;
    }
    
    @EventListener
    @Async  // 비동기 처리로 성능 향상
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            // 재고 예약
            inventoryService.reserveStock(event.getAggregateId(), event.getItems());
            
            // 결제 요청  
            paymentService.requestPayment(event.getAggregateId(), event.getTotalAmount());
            
        } catch (Exception e) {
            // 보상 트랜잭션 또는 재시도 로직
            handleOrderCreationFailure(event, e);
        }
    }
    
    @EventListener
    public void handleOrderConfirmed(OrderConfirmedEvent event) {
        // 고객에게 주문 확정 알림
        notificationService.sendOrderConfirmationEmail(event.getAggregateId());
        
        // 재고 확정 (예약 → 확정)
        inventoryService.confirmStockReservation(event.getAggregateId());
    }
    
    @EventListener  
    public void handleOrderCancelled(OrderCancelledEvent event) {
        // 재고 예약 해제
        inventoryService.releaseStockReservation(event.getAggregateId());
        
        // 결제 취소
        paymentService.cancelPayment(event.getAggregateId());
        
        // 취소 알림
        notificationService.sendOrderCancellationEmail(event.getAggregateId(), event.getReason());
    }
    
    private void handleOrderCreationFailure(OrderCreatedEvent event, Exception e) {
        // 실패 시 보상 트랜잭션 실행
        try {
            inventoryService.releaseStockReservation(event.getAggregateId());
        } catch (Exception compensationException) {
            // 보상 실패 로그 및 알림
            logger.error("보상 트랜잭션 실패: {}", compensationException.getMessage());
        }
    }
}
```

### 2. RabbitMQ 기반 비동기 처리
```java
@Component
public class OrderEventConsumer {
    
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    
    public OrderEventConsumer(InventoryService inventoryService,
                            PaymentService paymentService, 
                            NotificationService notificationService) {
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.notificationService = notificationService;
    }
    
    @RabbitListener(queues = "order.created.inventory.queue")
    public void handleOrderCreatedForInventory(OrderCreatedEvent event) {
        try {
            inventoryService.reserveStock(event.getAggregateId(), event.getItems());
            
        } catch (InsufficientStockException e) {
            // 재고 부족 시 주문 취소 이벤트 발행
            publishInventoryFailureEvent(event, e.getMessage());
            
        } catch (Exception e) {
            // 재시도를 위해 예외를 다시 던짐 (DLQ로 이동)
            throw new EventProcessingException("재고 처리 실패", e);
        }
    }
    
    @RabbitListener(queues = "order.created.payment.queue")  
    public void handleOrderCreatedForPayment(OrderCreatedEvent event) {
        try {
            PaymentResult result = paymentService.processPayment(
                event.getAggregateId(), event.getTotalAmount()
            );
            
            if (result.isSuccess()) {
                publishPaymentSuccessEvent(event, result);
            } else {
                publishPaymentFailureEvent(event, result.getErrorMessage());
            }
            
        } catch (Exception e) {
            throw new EventProcessingException("결제 처리 실패", e);
        }
    }
    
    @RabbitListener(queues = "order.confirmed.notification.queue")
    public void handleOrderConfirmedForNotification(OrderConfirmedEvent event) {
        try {
            notificationService.sendOrderConfirmationEmail(event.getAggregateId());
            notificationService.sendSMSNotification(event.getAggregateId());
            
        } catch (Exception e) {
            // 알림 실패는 중요하지 않으므로 로그만 남김
            logger.warn("알림 발송 실패: {}", e.getMessage());
        }
    }
    
    // 데드 레터 큐 처리
    @RabbitListener(queues = "order.events.dlq")
    public void handleDeadLetterQueue(Message message) {
        String eventType = (String) message.getMessageProperties().getHeaders().get("eventType");
        String eventId = (String) message.getMessageProperties().getHeaders().get("eventId");
        
        logger.error("데드 레터 큐로 이동된 이벤트: {} (ID: {})", eventType, eventId);
        
        // 알림 발송 또는 수동 처리를 위한 저장
        saveFailedEventForManualProcessing(message);
    }
    
    private void publishInventoryFailureEvent(OrderCreatedEvent originalEvent, String errorMessage) {
        // 재고 실패 이벤트 발행
        InventoryReservationFailedEvent failureEvent = new InventoryReservationFailedEvent(
            originalEvent.getAggregateId(), originalEvent.getVersion(), errorMessage
        );
        
        // 이벤트 발행 로직...
    }
}
```

### 3. 이벤트 저장소와 이벤트 소싱
```java
// 이벤트 저장소 인터페이스
public interface EventStore {
    void saveEvents(String aggregateId, List<DomainEvent> events, long expectedVersion);
    List<DomainEvent> getEventsForAggregate(String aggregateId);
    List<DomainEvent> getEventsFromVersion(String aggregateId, long version);
}

// 이벤트 저장소 구현
@Repository
public class JpaEventStore implements EventStore {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    private final ObjectMapper objectMapper;
    
    public JpaEventStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    @Transactional
    public void saveEvents(String aggregateId, List<DomainEvent> events, long expectedVersion) {
        // 버전 충돌 검사
        Long currentVersion = getCurrentVersion(aggregateId);
        if (currentVersion != null && !currentVersion.equals(expectedVersion)) {
            throw new ConcurrencyException("버전 충돌 발생");
        }
        
        for (DomainEvent event : events) {
            EventEntity eventEntity = new EventEntity();
            eventEntity.setEventId(event.getEventId());
            eventEntity.setAggregateId(aggregateId);
            eventEntity.setEventType(event.getEventType());
            eventEntity.setVersion(event.getVersion());
            eventEntity.setOccurredAt(event.getOccurredAt());
            
            try {
                eventEntity.setEventData(objectMapper.writeValueAsString(event));
            } catch (JsonProcessingException e) {
                throw new EventSerializationException("이벤트 직렬화 실패", e);
            }
            
            entityManager.persist(eventEntity);
        }
    }
    
    @Override
    public List<DomainEvent> getEventsForAggregate(String aggregateId) {
        List<EventEntity> eventEntities = entityManager
            .createQuery("SELECT e FROM EventEntity e WHERE e.aggregateId = :aggregateId ORDER BY e.version", 
                        EventEntity.class)
            .setParameter("aggregateId", aggregateId)
            .getResultList();
        
        return eventEntities.stream()
                          .map(this::deserializeEvent)
                          .collect(Collectors.toList());
    }
    
    @Override
    public List<DomainEvent> getEventsFromVersion(String aggregateId, long version) {
        List<EventEntity> eventEntities = entityManager
            .createQuery("SELECT e FROM EventEntity e WHERE e.aggregateId = :aggregateId AND e.version > :version ORDER BY e.version", 
                        EventEntity.class)
            .setParameter("aggregateId", aggregateId)
            .setParameter("version", version)
            .getResultList();
        
        return eventEntities.stream()
                          .map(this::deserializeEvent)
                          .collect(Collectors.toList());
    }
    
    private Long getCurrentVersion(String aggregateId) {
        return entityManager
            .createQuery("SELECT MAX(e.version) FROM EventEntity e WHERE e.aggregateId = :aggregateId", Long.class)
            .setParameter("aggregateId", aggregateId)
            .getSingleResult();
    }
    
    private DomainEvent deserializeEvent(EventEntity eventEntity) {
        try {
            Class<?> eventClass = Class.forName(getEventClassName(eventEntity.getEventType()));
            return (DomainEvent) objectMapper.readValue(eventEntity.getEventData(), eventClass);
        } catch (Exception e) {
            throw new EventDeserializationException("이벤트 역직렬화 실패", e);
        }
    }
    
    private String getEventClassName(String eventType) {
        // 이벤트 타입에서 클래스명 추출 로직
        return "com.example.events." + eventType;
    }
}

// 이벤트 소싱을 활용한 애그리게이트 재구성
@Service
public class EventSourcingOrderService {
    
    private final EventStore eventStore;
    
    public EventSourcingOrderService(EventStore eventStore) {
        this.eventStore = eventStore;
    }
    
    public Order getOrder(String orderId) {
        List<DomainEvent> events = eventStore.getEventsForAggregate(orderId);
        
        if (events.isEmpty()) {
            throw new OrderNotFoundException("주문을 찾을 수 없습니다: " + orderId);
        }
        
        return rebuildOrderFromEvents(events);
    }
    
    private Order rebuildOrderFromEvents(List<DomainEvent> events) {
        Order order = new Order(); // 빈 애그리게이트 생성
        
        for (DomainEvent event : events) {
            order.apply(event); // 각 이벤트를 순차적으로 적용
        }
        
        return order;
    }
}
```

## 메시지 큐 시스템 구성

### 1. RabbitMQ 설정
```java
@Configuration
@EnableRabbit
public class RabbitMQConfig {
    
    // 이벤트 교환기
    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange("events.exchange", true, false);
    }
    
    // 주문 관련 큐들
    @Bean
    public Queue orderCreatedInventoryQueue() {
        return QueueBuilder.durable("order.created.inventory.queue")
                          .withArgument("x-dead-letter-exchange", "events.dlx")
                          .withArgument("x-dead-letter-routing-key", "order.created.inventory.dlq")
                          .build();
    }
    
    @Bean
    public Queue orderCreatedPaymentQueue() {
        return QueueBuilder.durable("order.created.payment.queue")
                          .withArgument("x-dead-letter-exchange", "events.dlx")
                          .withArgument("x-dead-letter-routing-key", "order.created.payment.dlq")
                          .build();
    }
    
    @Bean
    public Queue orderConfirmedNotificationQueue() {
        return QueueBuilder.durable("order.confirmed.notification.queue")
                          .withArgument("x-dead-letter-exchange", "events.dlx")
                          .withArgument("x-dead-letter-routing-key", "order.confirmed.notification.dlq")
                          .build();
    }
    
    // 데드 레터 교환기와 큐
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange("events.dlx", true, false);
    }
    
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable("order.events.dlq").build();
    }
    
    // 바인딩 설정
    @Bean
    public Binding orderCreatedInventoryBinding() {
        return BindingBuilder.bind(orderCreatedInventoryQueue())
                           .to(eventsExchange())
                           .with("order.created");
    }
    
    @Bean
    public Binding orderCreatedPaymentBinding() {
        return BindingBuilder.bind(orderCreatedPaymentQueue())
                           .to(eventsExchange())
                           .with("order.created");
    }
    
    @Bean
    public Binding orderConfirmedNotificationBinding() {
        return BindingBuilder.bind(orderConfirmedNotificationQueue())
                           .to(eventsExchange())
                           .with("order.confirmed");
    }
    
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                           .to(deadLetterExchange())
                           .with("*.dlq");
    }
    
    // 메시지 컨버터 설정
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        template.setRetryTemplate(retryTemplate());
        return template;
    }
    
    @Bean
    public RetryTemplate retryTemplate() {
        return RetryTemplate.builder()
                           .maxAttempts(3)
                           .exponentialBackoff(1000, 2, 10000)
                           .retryOn(AmqpException.class)
                           .build();
    }
}
```

### 2. Apache Kafka 설정
```java
@Configuration
@EnableKafka
public class KafkaConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    // Producer 설정
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // 안정성 설정
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // 성능 설정
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
    
    // Consumer 설정
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "order-service");
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // 메시지 처리 설정
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        
        // JSON 역직렬화 설정
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.events");
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // 동시성 설정
        factory.setConcurrency(3);
        
        // 에러 핸들링
        factory.setErrorHandler(new SeekToCurrentErrorHandler(
            new DeadLetterPublishingRecoverer(kafkaTemplate()), 
            new FixedBackOff(1000L, 2)));
        
        // 수동 커밋
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        return factory;
    }
}

// Kafka 이벤트 발행자
@Component
public class KafkaEventPublisher implements EventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    public KafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    @Override
    public void publish(DomainEvent event) {
        String topic = getTopicName(event.getEventType());
        String key = event.getAggregateId(); // 같은 애그리게이트의 이벤트는 같은 파티션으로
        
        kafkaTemplate.send(topic, key, event)
                    .addCallback(
                        result -> logger.info("이벤트 발행 성공: {}", event.getEventId()),
                        failure -> logger.error("이벤트 발행 실패: {}", failure.getMessage())
                    );
    }
    
    private String getTopicName(String eventType) {
        return eventType.toLowerCase().replace("event", "");
    }
}

// Kafka 이벤트 소비자
@Component
public class KafkaOrderEventConsumer {
    
    @KafkaListener(topics = "order.created", groupId = "inventory-service")
    public void handleOrderCreatedForInventory(OrderCreatedEvent event, Acknowledgment ack) {
        try {
            inventoryService.reserveStock(event.getAggregateId(), event.getItems());
            ack.acknowledge(); // 수동 커밋
            
        } catch (Exception e) {
            logger.error("재고 처리 실패: {}", e.getMessage());
            // 재시도 또는 DLQ 처리
            throw e;
        }
    }
    
    @KafkaListener(topics = "order.created", groupId = "payment-service")
    public void handleOrderCreatedForPayment(OrderCreatedEvent event, Acknowledgment ack) {
        try {
            paymentService.processPayment(event.getAggregateId(), event.getTotalAmount());
            ack.acknowledge();
            
        } catch (Exception e) {
            logger.error("결제 처리 실패: {}", e.getMessage());
            throw e;
        }
    }
}
```

## 고급 패턴과 실무 사례

### 1. Saga 패턴과 이벤트 조합
```java
// Saga 오케스트레이터
@Component
public class OrderProcessingSaga {
    
    private final EventPublisher eventPublisher;
    private final SagaStateRepository sagaStateRepository;
    
    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        // Saga 상태 생성
        SagaState sagaState = new SagaState(event.getAggregateId(), SagaStep.ORDER_CREATED);
        sagaStateRepository.save(sagaState);
        
        // 재고 예약 명령 이벤트 발행
        eventPublisher.publish(new ReserveInventoryCommand(
            event.getAggregateId(), event.getItems()
        ));
    }
    
    @EventListener
    public void handleInventoryReserved(InventoryReservedEvent event) {
        SagaState sagaState = sagaStateRepository.findByOrderId(event.getOrderId());
        sagaState.moveToNextStep(SagaStep.INVENTORY_RESERVED);
        sagaStateRepository.save(sagaState);
        
        // 결제 처리 명령 이벤트 발행
        eventPublisher.publish(new ProcessPaymentCommand(
            event.getOrderId(), event.getTotalAmount()
        ));
    }
    
    @EventListener
    public void handlePaymentProcessed(PaymentProcessedEvent event) {
        SagaState sagaState = sagaStateRepository.findByOrderId(event.getOrderId());
        
        if (event.getStatus() == PaymentStatus.SUCCESS) {
            sagaState.moveToNextStep(SagaStep.PAYMENT_COMPLETED);
            sagaStateRepository.save(sagaState);
            
            // 주문 확정 이벤트 발행
            eventPublisher.publish(new ConfirmOrderCommand(event.getOrderId()));
            
        } else {
            // 보상 트랜잭션 시작
            startCompensation(sagaState);
        }
    }
    
    @EventListener
    public void handleInventoryReservationFailed(InventoryReservationFailedEvent event) {
        SagaState sagaState = sagaStateRepository.findByOrderId(event.getOrderId());
        sagaState.markAsFailed(event.getErrorMessage());
        sagaStateRepository.save(sagaState);
        
        // 주문 취소 이벤트 발행
        eventPublisher.publish(new CancelOrderCommand(
            event.getOrderId(), "재고 부족으로 인한 취소"
        ));
    }
    
    private void startCompensation(SagaState sagaState) {
        sagaState.startCompensation();
        sagaStateRepository.save(sagaState);
        
        // 재고 예약 해제
        eventPublisher.publish(new ReleaseInventoryCommand(sagaState.getOrderId()));
        
        // 주문 취소
        eventPublisher.publish(new CancelOrderCommand(
            sagaState.getOrderId(), "결제 실패로 인한 취소"
        ));
    }
}
```

### 2. 이벤트 스트리밍과 CQRS 조합
```java
// Command Side - 쓰기 모델
@Service
@Transactional
public class OrderCommandService {
    
    private final EventStore eventStore;
    private final EventPublisher eventPublisher;
    
    public void createOrder(CreateOrderCommand command) {
        Order order = Order.create(command.getCustomerId(), command.getItems());
        
        // 이벤트 저장
        eventStore.saveEvents(order.getId(), order.getDomainEvents(), 0);
        
        // 이벤트 발행
        eventPublisher.publishAll(order.getDomainEvents());
    }
}

// Query Side - 읽기 모델
@Component
public class OrderProjectionHandler {
    
    private final OrderReadModelRepository orderReadModelRepository;
    
    @KafkaListener(topics = "order.created")
    public void handleOrderCreated(OrderCreatedEvent event) {
        OrderReadModel readModel = new OrderReadModel();
        readModel.setId(event.getAggregateId());
        readModel.setCustomerId(event.getCustomerId());
        readModel.setTotalAmount(event.getTotalAmount());
        readModel.setStatus("PENDING");
        readModel.setCreatedAt(event.getOccurredAt());
        
        orderReadModelRepository.save(readModel);
    }
    
    @KafkaListener(topics = "order.confirmed")
    public void handleOrderConfirmed(OrderConfirmedEvent event) {
        OrderReadModel readModel = orderReadModelRepository.findById(event.getAggregateId());
        readModel.setStatus("CONFIRMED");
        readModel.setUpdatedAt(event.getOccurredAt());
        
        orderReadModelRepository.save(readModel);
    }
    
    @KafkaListener(topics = "order.cancelled")
    public void handleOrderCancelled(OrderCancelledEvent event) {
        OrderReadModel readModel = orderReadModelRepository.findById(event.getAggregateId());
        readModel.setStatus("CANCELLED");
        readModel.setCancellationReason(event.getReason());
        readModel.setUpdatedAt(event.getOccurredAt());
        
        orderReadModelRepository.save(readModel);
    }
}

// 읽기 전용 서비스
@Service
@Transactional(readOnly = true)
public class OrderQueryService {
    
    private final OrderReadModelRepository orderReadModelRepository;
    
    public List<OrderReadModel> getOrdersByCustomer(String customerId) {
        return orderReadModelRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }
    
    public OrderReadModel getOrder(String orderId) {
        return orderReadModelRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("주문을 찾을 수 없습니다"));
    }
    
    public List<OrderReadModel> getOrdersByStatus(String status) {
        return orderReadModelRepository.findByStatus(status);
    }
}
```

## 인터뷰 꼬리질문 대비

### Q1: "이벤트 드리븐 아키텍처의 단점은 무엇인가요?"
**답변 포인트:**
- **복잡성 증가**: 분산 시스템의 복잡성과 디버깅 어려움
- **일관성 문제**: 최종 일관성으로 인한 일시적 불일치
- **이벤트 순서**: 이벤트 처리 순서 보장의 어려움
- **중복 처리**: 메시지 중복 전송에 대한 멱등성 처리 필요

### Q2: "이벤트 순서를 어떻게 보장하나요?"
**답변 포인트:**
- **파티션 키 활용**: Kafka에서 같은 애그리게이트는 같은 파티션으로
- **버전 정보 포함**: 이벤트에 버전 정보를 포함하여 순서 검증
- **단일 스레드 처리**: 순서가 중요한 이벤트는 단일 스레드로 처리
- **이벤트 재정렬**: 수신 측에서 버전 기반 재정렬 로직 구현

### Q3: "이벤트 처리 실패 시 어떻게 대응하나요?"
**답변 포인트:**
- **재시도 메커니즘**: 지수 백오프를 포함한 재시도 정책
- **데드 레터 큐**: 최종 실패 이벤트를 별도 큐로 이동
- **보상 트랜잭션**: 실패 시 이미 처리된 작업을 되돌리는 로직
- **알림 및 모니터링**: 실패 상황에 대한 즉각적인 알림 시스템

## 실무 베스트 프랙티스

1. **이벤트 설계**: 비즈니스 의미를 담은 명확한 이벤트 설계
2. **멱등성 보장**: 중복 이벤트 처리에 대한 멱등성 구현
3. **스키마 진화**: 이벤트 스키마 변경에 대한 하위 호환성 고려
4. **모니터링**: 이벤트 흐름과 처리 상태에 대한 종합적 모니터링
5. **테스트 전략**: 이벤트 기반 시스템의 통합 테스트 및 계약 테스트

이벤트 드리븐 아키텍처는 마이크로서비스 간의 느슨한 결합과 높은 확장성을 제공하는 핵심 패턴입니다. 적절한 설계와 구현을 통해 복잡한 비즈니스 프로세스를 효과적으로 관리할 수 있습니다.