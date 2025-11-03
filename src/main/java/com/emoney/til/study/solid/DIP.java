//package com.emoney.til.study.solid;
//
///**
// * 고수준 모듈(비즈니스 로직)은 하위 모듈(세부 구현)을 통해 완성되므로 고수준 모듈이 저수준 모듈에 의존한다고 할 수 있다.
// * 여기서 저수준 모듈이 추상화(인터페이스)를 통해 구현하게 되면 의존의 방향이 바뀌기 떄문에 의존 역전 원칙이라고 한다.
// */
//public class DIP {
//
//    // 전통적인 방식 (의존성 역전 전)
//    private MySQLOrderRepository orderRepository = new MySQLOrderRepository();
//    // OrderService가 구체적인 MySQLOrderRepository에 직접 의존
//
//    // DIP 적용 (의존성 역전 후)
//    private OrderRepository orderRepository; // 인터페이스에 의존
//
//    public DIP(OrderRepository orderRepository) {
//        this.orderRepository = orderRepository;
//    }
//
//}
