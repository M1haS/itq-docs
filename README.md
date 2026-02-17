# ITQ Document Service

Backend-сервис для работы с документами: создание, перевод статусов, история, реестр утверждений.

## Стек

- Java 17 + Spring Boot 3.2
- PostgreSQL 15 (Docker Compose)
- JPA/Hibernate + Liquibase
- Maven (multi-module: `service`, `generator`)
- Testcontainers (интеграционные тесты)

---

## Быстрый старт

### 1. Поднять PostgreSQL

```bash
docker-compose up -d
```

### 2. Собрать проект

```bash
mvn clean package -DskipTests
```

### 3. Запустить сервис

```bash
java -jar service/target/service-1.0.0.jar
```

Сервис стартует на `http://localhost:8080`.  
Liquibase-миграции применяются автоматически при старте.

### 4. Запустить утилиту-генератор

Настройки генератора: `generator/src/main/resources/application.yml`  
Ключевые параметры:

| Параметр | По умолчанию | Описание |
|---|---|---|
| `generator.count` | `100` | Количество документов для создания |
| `generator.service-url` | `http://localhost:8080` | URL сервиса |
| `generator.initiator` | `generator-script` | Автор документов |

```bash
java -jar generator/target/generator-1.0.0.jar \
  --generator.count=500 \
  --generator.service-url=http://localhost:8080
```

---

## API

### POST /api/documents
Создать документ.
```json
{
  "author": "ivan",
  "title": "Договор №1"
}
```

### GET /api/documents/{id}
Получить документ с историей.

### GET /api/documents?ids=1,2,3&page=0&size=20&sort=createdAt,desc
Пакетное получение по id или постраничный список всех документов.

### POST /api/documents/submit
Перевести DRAFT → SUBMITTED (пачка до 1000 id).
```json
{
  "ids": [1, 2, 3],
  "initiator": "manager",
  "comment": "Отправлено на согласование"
}
```

### POST /api/documents/approve
Перевести SUBMITTED → APPROVED (пачка до 1000 id).

### GET /api/documents/search
Поиск документов.
```
?status=DRAFT&author=ivan&from=2024-01-01T00:00:00Z&to=2024-12-31T23:59:59Z&page=0&size=20
```
> **Примечание:** Период фильтруется по дате **создания** (`created_at`).

### POST /api/documents/{id}/concurrent-approval-test
Тест конкурентного утверждения.
```json
{
  "threads": 5,
  "attempts": 3,
  "initiator": "tester"
}
```

---

## Фоновые воркеры

Два фоновых процесса работают в сервисе автоматически:

| Воркер | Что делает | Параметр задержки |
|---|---|---|
| `SubmitWorker` | DRAFT → SUBMITTED пачками | `app.workers.submit.fixed-delay-ms` |
| `ApproveWorker` | SUBMITTED → APPROVED пачками | `app.workers.approve.fixed-delay-ms` |

Размер пачки: `app.batch-size` (по умолчанию 50).

Отключить воркеры: `app.workers.submit.enabled=false` / `app.workers.approve.enabled=false`.

---

## Мониторинг прогресса по логам

```
# Создание документов (генератор)
INFO  Created [42/100] id=42 in 12ms
INFO  Progress: 50/100 created, 0 failed

# Фоновая обработка (сервис)
INFO  [SUBMIT-worker] Processing batch: 50 documents (total DRAFT: 200)
INFO  [SUBMIT-worker] Batch done in 145ms: success=48, failed=2, remaining≈150
INFO  [APPROVE-worker] Processing batch: 50 documents (total SUBMITTED: 80)
INFO  [APPROVE-worker] Batch done in 98ms: success=50, failed=0, remaining≈30
```

---

## Запуск тестов

```bash
mvn test -pl service
```

Тесты используют Testcontainers — потребуется Docker.

---

## Конфигурация

`service/src/main/resources/application.yml`:

```yaml
app:
  batch-size: 50               # размер пачки для воркеров
  workers:
    submit:
      enabled: true
      fixed-delay-ms: 10000    # задержка между запусками submit-воркера
    approve:
      enabled: true
      fixed-delay-ms: 15000    # задержка между запусками approve-воркера
```

---

## Опциональные пункты

### Обработка 5000+ id в одном запросе

Текущая реализация обрабатывает каждый документ в отдельной транзакции (`REQUIRES_NEW`) последовательно. При 5000+ id это допустимо, но медленно. Улучшения:

1. **Параллельная обработка** — разбить список на подпачки (например, по 100) и обрабатывать параллельно через `CompletableFuture` с bounded thread pool.
2. **Bulk SELECT FOR UPDATE** — вместо N запросов `SELECT FOR UPDATE` по одному id делать один `SELECT ... WHERE id IN (...) FOR UPDATE SKIP LOCKED`.
3. **Advisory locks** — для тонкого контроля конкурентности без блокировки строк.
4. **Chunked streaming** — принимать id через cursor/stream, избегая загрузки всего списка в память.

### Реестр утверждений в отдельной системе

**Вариант A — отдельная БД:**
- Реестр выносится в отдельную схему или БД PostgreSQL.
- Используется `@Transactional` с distributed XA (JTA/Atomikos) или паттерн **Outbox**: при утверждении записать событие в таблицу `outbox` в той же транзакции, отдельным воркером доставить запись в реестр.
- Outbox надёжнее — не требует distributed transactions.

**Вариант B — отдельный HTTP-сервис:**
- Реестр = отдельный Spring Boot микросервис с собственной БД.
- При утверждении делается HTTP-вызов `POST /registry`.
- Если вызов падает — транзакция утверждения откатывается (текущее поведение уже работает так).
- Для надёжности: Outbox + асинхронная доставка (Kafka/RabbitMQ) + идемпотентный ключ `document_id` на стороне реестра.
