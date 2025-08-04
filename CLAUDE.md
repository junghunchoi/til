# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
This is a comprehensive TIL (Today I Learn) repository designed for **job interview preparation** for mid-senior level Java/Spring developers. It combines a Spring Boot application for hands-on coding practice with extensive theoretical documentation covering essential computer science topics commonly asked in technical interviews.

### Purpose and Goals
- **Interview Preparation**: Focused on questions frequently asked in mid-senior developer interviews
- **Practical Learning**: Each topic includes both theoretical knowledge and working code examples
- **Quick Reference**: Designed for rapid review and study sessions
- **Comprehensive Coverage**: Spans from basic concepts to advanced architectural patterns

## Build System
- **Build Tool**: Gradle with wrapper
- **Java Version**: 21
- **Spring Boot Version**: 3.4.4

## Common Commands

### Build & Run
```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun

# Run tests
./gradlew test

# Clean build artifacts
./gradlew clean

# Create executable JAR
./gradlew bootJar
```

### Testing
```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.emoney.til.async.AsyncTest"

# Run tests with specific pattern
./gradlew test --tests "*Concurrency*"
```

## Interview-Focused Documentation Structure

The `cs/` directory is organized by major technical interview categories:

### 1. Core Java (`cs/java/`)
- JVM internals, garbage collection, memory management
- Concurrency, thread management, synchronization
- Collections framework and data structures
- Modern Java features (Virtual Threads, Records, etc.)

### 2. Spring Framework (`cs/spring/`)
- Core Spring concepts (DI, AOP, Bean lifecycle)
- Spring Boot auto-configuration and customization
- Spring WebFlux and reactive programming
- Spring Security and OAuth 2.0 implementation
- Performance optimization and monitoring

### 3. JPA/ORM (`cs/jpa/`)
- Hibernate internals and performance tuning
- N+1 problem solutions and query optimization
- Persistence context and caching strategies
- Complex query handling (QueryDSL, Criteria, Native)

### 4. Database (`cs/database/`)
- RDBMS vs NoSQL comparison and selection criteria
- Database indexing, query optimization, execution plans
- Transaction management, isolation levels, MVCC
- Sharding, partitioning, and scalability patterns

### 5. Microservices & Distributed Systems (`cs/microservices/`)
- Service decomposition and domain-driven design
- Distributed transaction patterns (Saga, Event Sourcing)
- Circuit breaker and fault tolerance patterns
- Event-driven architecture and message queues

### 6. Infrastructure & Operations (`cs/infra/`)
- Application monitoring and performance tuning
- Load testing and capacity planning
- Deployment strategies (Blue-Green, Canary, Rolling)
- Container orchestration and Kubernetes

### 7. Security (`cs/security/`)
- Authentication and authorization patterns
- OAuth 2.0, JWT implementation
- Common security vulnerabilities and prevention

### 8. Testing (`cs/testing/`)
- Test pyramid and testing strategies
- Unit, integration, and E2E testing patterns
- Test automation and CI/CD integration

### 9. Design Patterns (`cs/common/designpattern/`)
- GoF patterns with practical Java implementations
- Modern architectural patterns (CQRS, Event Sourcing)

## Code Architecture

### Package Structure
- `com.emoney.til` - Main application package
  - `async/` - Asynchronous processing examples and configuration
  - `hanghae99/` - Study materials and examples from Hanghae99 bootcamp
    - `day1/` - Database concurrency examples (optimistic/pessimistic locking)
  - `study/` - General study materials
    - `solid/` - SOLID principles examples

### Key Components

#### Async Configuration
- `AsyncConfig.java` provides custom thread pool configuration
- Thread pool settings: 5 core, 10 max threads, 25 queue capacity
- Custom exception handling for async operations
- Demonstrates production-ready async patterns

#### Concurrency Examples
- `DatabaseConcurrencyExample.java` demonstrates database locking strategies
- Shows practical implementations of optimistic and pessimistic locking
- Includes thread-safe operations and version conflict handling
- Useful for understanding database concurrency interview questions

## Database Configuration
- MySQL database connection configured in `application.yml`
- Uses Spring Data JPA with Hibernate
- Connection details point to external MySQL instance
- Configuration supports both learning and testing scenarios

## Learning and Documentation Standards

### Documentation Format
Each learning document follows this structure:
1. **Core Concepts** - Essential theoretical knowledge
2. **Practical Examples** - Working code demonstrating concepts
3. **Interview Follow-up Questions** - Common follow-up questions with answer points
4. **Real-world Scenarios** - Practical application examples
5. **Key Takeaways** - Important points to remember for interviews

### Code Examples
- All code examples are production-ready and follow best practices
- Comments explain not just "what" but "why" 
- Examples include both successful and failure scenarios
- Error handling and edge cases are explicitly covered

### Interview Preparation Features
- **Quick Review Sections** - Key points summarized for rapid study
- **Common Pitfalls** - Typical mistakes and how to avoid them
- **Follow-up Questions** - Likely interviewer questions with suggested answers
- **Practical Scenarios** - Real-world problems and solutions

## Testing Strategy
- JUnit 5 (Jupiter) for unit tests
- Test classes demonstrate various testing patterns and strategies
- Includes examples of mocking, integration testing, and test containers
- Tests serve as executable documentation for concepts

## Development Notes
- **Language**: Documentation is written in Korean (한국어) for native comprehension
- **Audience**: Targeted at mid-senior level developers (3-7 years experience)
- **Focus Areas**: Backend development, system design, and architectural patterns
- **Interview Relevance**: Every topic is selected based on actual interview frequency
- **Continuous Updates**: Content is regularly updated based on current industry trends

## Using This Repository for Interview Prep

### Study Approach
1. **Topic-based Study**: Focus on one `cs/` subdirectory at a time
2. **Hands-on Practice**: Run and modify the code examples
3. **Question Practice**: Use the follow-up questions for self-assessment
4. **Mock Interviews**: Practice explaining concepts using the provided examples

### Key Interview Topics Priority
1. **High Priority**: Spring, JPA, Database, Java Concurrency
2. **Medium Priority**: Microservices, Testing, Security
3. **Advanced**: Infrastructure, Design Patterns

This repository serves as both a learning platform and a comprehensive interview preparation resource for Java/Spring developers.