src/main/java/com/emoney/cs/common/solid/Solid.java


1. 단일 책임 원칙 - 패스
2. 리스코프 치환 원칙

자식이 부모를 대체할 수 있어야 한다는 원칙.
단순히 impletements를 통해 오버라이딩하는 것이 아니라 그 기능이 동일하게 동작하여야 한다.

3. 개방-폐쇄 원칙

인터페이스를 통해 확장에는 열려있고, 수정에는 닫혀있어야 한다는 원칙
인터페이스 구현을 통해 기능을 확장할 수 있으며, 기존의 코드 수정 및 영향을 최소하해야한다.

4. 인터페이스 분리 원칙 - 패스 
5. 의존성 역전 원칙

저수준 모듈이 고수준 모듈에 의존해서는 안되며, 둘 다 추상화에 의존해야 한다는 원칙
가령, db가 바뀌어도 save메소드를 통해 각각의 db에 맞는 구현체가 동작하도록 한다.

```java
 // ❌ 나쁜 예
  class OrderService {
      private MySQLRepository repo;  // 구현체에 직접 의존
  }

  // ✅ 좋은 예  
  class OrderService {
      private Repository repo;  // 추상화에 의존
  }
```