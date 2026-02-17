# EXPLAIN.md — Анализ поискового запроса

## Запрос

Поиск документов по статусу и автору с пагинацией:

```sql
SELECT *
FROM documents
WHERE status = 'DRAFT'
  AND lower(author) = 'ivan'
  AND created_at >= '2024-01-01 00:00:00+00'
  AND created_at <= '2024-12-31 23:59:59+00'
ORDER BY created_at DESC
LIMIT 20 OFFSET 0;
```

## EXPLAIN (ANALYZE) — пример вывода

```
Limit  (cost=0.43..8.45 rows=20 width=124) (actual time=0.213..0.214 rows=0 loops=1)
  ->  Index Scan Backward using idx_documents_created_at on documents
        (cost=0.43..16.87 rows=1 width=124)
        (actual time=0.212..0.212 rows=0 loops=1)
        Index Cond: ((created_at >= '2024-01-01 00:00:00+00'::timestamptz)
                 AND (created_at <= '2024-12-31 23:59:59+00'::timestamptz))
        Filter: ((status)::text = 'DRAFT' AND lower(author) = 'ivan')
        Rows Removed by Filter: 0
Planning Time: 0.312 ms
Execution Time: 0.241 ms
```

## Индексы и пояснение

### Существующие индексы (из миграции):

| Индекс | Столбец | Назначение |
|---|---|---|
| `idx_documents_status` | `status` | Быстрый поиск по статусу, используется воркерами |
| `idx_documents_author` | `author` | Поиск по автору (case-sensitive) |
| `idx_documents_created_at` | `created_at` | Фильтрация по периоду + сортировка |
| `idx_history_document_id` | `document_id` | JOIN при загрузке истории |

### Почему используется `idx_documents_created_at`:

Планировщик PostgreSQL выбирает индекс по `created_at`, так как:
1. `created_at` используется и для фильтрации диапазона, и для `ORDER BY DESC` — один проход индекса решает обе задачи (Index Scan Backward).
2. `status` и `author` дополнительно фильтруются как **Filter** поверх строк, прошедших по диапазону дат.

### Потенциальные улучшения:

**1. Составной индекс для частого сочетания статус + дата:**
```sql
CREATE INDEX idx_documents_status_created ON documents (status, created_at DESC);
```
При запросах `WHERE status = ? ORDER BY created_at DESC` планировщик пройдёт только строки нужного статуса — значительно меньше I/O на больших таблицах.

**2. Функциональный индекс для поиска по автору без учёта регистра:**
```sql
CREATE INDEX idx_documents_author_lower ON documents (lower(author));
```
Без него `lower(author) = 'ivan'` не использует `idx_documents_author` и приводит к Sequential Scan или Filter.

**3. Покрывающий индекс (covering index):**
```sql
CREATE INDEX idx_documents_cover ON documents (status, created_at DESC)
    INCLUDE (id, number, author, title);
```
При `SELECT id, number, author, title, status, created_at` PostgreSQL не обращается к heap (Index Only Scan) — ускорение на читальных нагрузках.

### Вывод:
Для текущего объёма данных существующих индексов достаточно. При росте до миллионов записей рекомендуется добавить составной индекс `(status, created_at DESC)` и функциональный индекс `(lower(author))`.
