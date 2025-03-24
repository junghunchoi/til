<details>
<summary>@Async 학습 [2025.03.24]</summary>

CompletableFuture 를 반환하자
- 작업의 완료여부 체크가 가능하다.
- 비동기의 예외는 호출자에게 반환되지 않을 수 있기때문
- 체이닝 메소드(thenApply, thenAccept, thenRun)를 통해 비동기 작업을 순차적으로 처리할 수 있다.
- 명시적으로 작업 완료를 할 수 있다. (get(), join())
- config는 기본적으로 작성한 클래스를 참고하고 필요에 따라 적절히 수정하는 방식으로

</details>