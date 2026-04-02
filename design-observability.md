# 観測設計書（Phase 1）

## 1. 設計概要

### 1.1 設計目的

備品購入申請システム（MVP）の観測性（Observability）を確保するため、ログ出力、メトリクス収集、アラート設定、トラブルシューティング手順を定義する。

### 1.2 観測性の3本柱

- **ログ（Logs）**: 個別イベントの詳細記録
- **メトリクス（Metrics）**: 時系列の数値データ
- **トレース（Traces）**: リクエストの追跡（将来拡張）

### 1.3 設計原則

- **構造化ログ**: JSON形式で機械可読性を確保
- **コンテキスト伝播**: requestIdによる追跡
- **業務イベント重視**: 技術ログだけでなく業務イベントも記録
- **プライバシー保護**: 個人情報をログに出力しない

### 1.4 参照ドキュメント

- [`spec.md`](spec.md): 機能仕様
- [`research.md`](research.md): 技術調査・前提条件
- [`design-app.md`](design-app.md): アプリケーション設計
- [`design-deployment.md`](design-deployment.md): 配備設計

## 2. ログ設計

### 2.1 ログレベル定義

| レベル | 用途 | 例 |
|--------|------|-----|
| ERROR | システムエラー、予期しない例外 | DB接続失敗、外部API呼び出し失敗 |
| WARN | ビジネスルール違反、リトライ可能なエラー | 不正なステータス遷移、権限不足 |
| INFO | 業務イベント、重要な状態変化 | 申請登録、承認、却下 |
| DEBUG | デバッグ情報（開発環境のみ） | メソッド呼び出し、変数値 |

### 2.2 構造化ログフォーマット

#### 2.2.1 標準ログ項目

すべてのログに以下の項目を含める：

```json
{
  "timestamp": "2026-04-02T14:23:00.123Z",
  "level": "INFO",
  "logger": "com.example.purchaserequest.service.PurchaseRequestServiceImpl",
  "thread": "http-nio-8080-exec-1",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user123",
  "action": "CREATE_REQUEST",
  "message": "申請を登録しました",
  "duration": 125,
  "context": {
    "requestId": 1,
    "itemName": "ノートPC",
    "totalAmount": 150000,
    "status": "DRAFT"
  }
}
```

#### 2.2.2 ログ項目詳細

| 項目 | 型 | 必須 | 説明 | 例 |
|------|---|------|------|-----|
| timestamp | string | ○ | タイムスタンプ（ISO 8601） | 2026-04-02T14:23:00.123Z |
| level | string | ○ | ログレベル | INFO |
| logger | string | ○ | ロガー名（クラス名） | com.example...ServiceImpl |
| thread | string | ○ | スレッド名 | http-nio-8080-exec-1 |
| requestId | string | ○ | リクエスト追跡ID（UUID） | 550e8400-... |
| userId | string | △ | 実行ユーザーID | user123 |
| action | string | △ | 業務アクション | CREATE_REQUEST |
| message | string | ○ | ログメッセージ | 申請を登録しました |
| duration | number | △ | 処理時間（ミリ秒） | 125 |
| context | object | △ | コンテキスト情報 | {...} |
| error | object | △ | エラー情報 | {...} |

### 2.3 業務アクション定義

| アクション | 説明 | ログレベル | 出力タイミング |
|-----------|------|-----------|--------------|
| CREATE_REQUEST | 申請登録（下書き） | INFO | 登録成功時 |
| SUBMIT_REQUEST | 申請提出 | INFO | 提出成功時 |
| APPROVE_REQUEST | 申請承認 | INFO | 承認成功時 |
| REJECT_REQUEST | 申請却下 | INFO | 却下成功時 |
| VIEW_REQUEST_LIST | 申請一覧表示 | DEBUG | 一覧取得時 |
| VIEW_REQUEST_DETAIL | 申請詳細表示 | DEBUG | 詳細取得時 |
| INVALID_STATUS_TRANSITION | 不正なステータス遷移 | WARN | 遷移失敗時 |
| UNAUTHORIZED_OPERATION | 権限不足 | WARN | 権限チェック失敗時 |
| VALIDATION_ERROR | バリデーションエラー | WARN | 入力検証失敗時 |
| SYSTEM_ERROR | システムエラー | ERROR | 予期しない例外発生時 |

### 2.4 ログ出力実装

#### 2.4.1 Logback設定（logback-spring.xml）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- コンソール出力（開発環境） -->
    <springProfile name="dev">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="CONSOLE" />
        </root>
    </springProfile>

    <!-- JSON出力（本番環境） -->
    <springProfile name="prod,stg">
        <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdcKeyName>requestId</includeMdcKeyName>
                <includeMdcKeyName>userId</includeMdcKeyName>
                <includeMdcKeyName>action</includeMdcKeyName>
                <fieldNames>
                    <timestamp>timestamp</timestamp>
                    <version>[ignore]</version>
                    <levelValue>[ignore]</levelValue>
                </fieldNames>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="JSON" />
        </root>
    </springProfile>

    <!-- パッケージ別ログレベル -->
    <logger name="com.example.purchaserequest" level="INFO" />
    <logger name="org.springframework.web" level="INFO" />
    <logger name="org.springframework.security" level="WARN" />
    <logger name="org.hibernate.SQL" level="DEBUG" />
    <logger name="org.hibernate.type.descriptor.sql.BasicBinder" level="TRACE" />
</configuration>
```

#### 2.4.2 MDC（Mapped Diagnostic Context）設定

```java
@Component
public class RequestIdFilter extends OncePerRequestFilter {
    
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String REQUEST_ID_MDC_KEY = "requestId";
    
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }
        
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }
}
```

#### 2.4.3 ログ出力ユーティリティ

```java
@Component
@Slf4j
public class BusinessLogger {
    
    /**
     * 業務イベントをログ出力
     */
    public void logBusinessEvent(String action, String message, Map<String, Object> context) {
        MDC.put("action", action);
        try {
            if (context != null && !context.isEmpty()) {
                log.info("{} - Context: {}", message, toJson(context));
            } else {
                log.info(message);
            }
        } finally {
            MDC.remove("action");
        }
    }
    
    /**
     * エラーをログ出力
     */
    public void logError(String action, String message, Throwable throwable, Map<String, Object> context) {
        MDC.put("action", action);
        try {
            Map<String, Object> errorContext = new HashMap<>();
            if (context != null) {
                errorContext.putAll(context);
            }
            errorContext.put("errorClass", throwable.getClass().getName());
            errorContext.put("errorMessage", throwable.getMessage());
            
            log.error("{} - Context: {}", message, toJson(errorContext), throwable);
        } finally {
            MDC.remove("action");
        }
    }
    
    private String toJson(Map<String, Object> map) {
        try {
            return new ObjectMapper().writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return map.toString();
        }
    }
}
```

#### 2.4.4 サービス層でのログ出力例

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseRequestServiceImpl implements PurchaseRequestService {
    
    private final BusinessLogger businessLogger;
    private final PurchaseRequestRepository repository;
    
    @Override
    @Transactional
    public PurchaseRequestDto createDraft(CreatePurchaseRequestDto dto, Long requesterId) {
        long startTime = System.currentTimeMillis();
        
        try {
            // ビジネスロジック実行
            PurchaseRequest request = PurchaseRequest.builder()
                .requesterId(requesterId)
                .itemName(dto.getItemName())
                .quantity(dto.getQuantity())
                .unitPrice(dto.getUnitPrice())
                .purchaseReason(dto.getPurchaseReason())
                .desiredDeliveryDate(dto.getDesiredDeliveryDate())
                .remarks(dto.getRemarks())
                .status(RequestStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
            
            PurchaseRequest saved = repository.save(request);
            
            // 業務ログ出力
            Map<String, Object> context = Map.of(
                "requestId", saved.getId(),
                "itemName", saved.getItemName(),
                "totalAmount", saved.getTotalAmount(),
                "status", saved.getStatus(),
                "duration", System.currentTimeMillis() - startTime
            );
            businessLogger.logBusinessEvent("CREATE_REQUEST", "申請を登録しました", context);
            
            return toDto(saved);
            
        } catch (Exception e) {
            // エラーログ出力
            Map<String, Object> errorContext = Map.of(
                "itemName", dto.getItemName(),
                "requesterId", requesterId,
                "duration", System.currentTimeMillis() - startTime
            );
            businessLogger.logError("CREATE_REQUEST", "申請登録に失敗しました", e, errorContext);
            throw e;
        }
    }
}
```

### 2.5 ログ出力例

#### 2.5.1 申請登録成功

```json
{
  "timestamp": "2026-04-02T14:23:00.123Z",
  "level": "INFO",
  "logger": "com.example.purchaserequest.service.PurchaseRequestServiceImpl",
  "thread": "http-nio-8080-exec-1",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user123",
  "action": "CREATE_REQUEST",
  "message": "申請を登録しました - Context: {\"requestId\":1,\"itemName\":\"ノートPC\",\"totalAmount\":150000,\"status\":\"DRAFT\",\"duration\":125}"
}
```

#### 2.5.2 承認成功

```json
{
  "timestamp": "2026-04-02T14:24:00.456Z",
  "level": "INFO",
  "logger": "com.example.purchaserequest.service.PurchaseRequestServiceImpl",
  "thread": "http-nio-8080-exec-2",
  "requestId": "660e8400-e29b-41d4-a716-446655440001",
  "userId": "approver456",
  "action": "APPROVE_REQUEST",
  "message": "申請を承認しました - Context: {\"requestId\":1,\"approverId\":2,\"previousStatus\":\"SUBMITTED\",\"newStatus\":\"APPROVED\",\"duration\":89}"
}
```

#### 2.5.3 権限不足エラー

```json
{
  "timestamp": "2026-04-02T14:25:00.789Z",
  "level": "WARN",
  "logger": "com.example.purchaserequest.service.PurchaseRequestServiceImpl",
  "thread": "http-nio-8080-exec-3",
  "requestId": "770e8400-e29b-41d4-a716-446655440002",
  "userId": "user789",
  "action": "UNAUTHORIZED_OPERATION",
  "message": "承認権限がありません - Context: {\"requestId\":1,\"userId\":\"user789\",\"requiredRole\":\"ROLE_APPROVER\"}"
}
```

#### 2.5.4 システムエラー

```json
{
  "timestamp": "2026-04-02T14:26:00.012Z",
  "level": "ERROR",
  "logger": "com.example.purchaserequest.service.PurchaseRequestServiceImpl",
  "thread": "http-nio-8080-exec-4",
  "requestId": "880e8400-e29b-41d4-a716-446655440003",
  "userId": "user123",
  "action": "CREATE_REQUEST",
  "message": "申請登録に失敗しました - Context: {\"itemName\":\"ノートPC\",\"requesterId\":1,\"duration\":45,\"errorClass\":\"org.springframework.dao.DataAccessException\",\"errorMessage\":\"Connection timeout\"}",
  "stack_trace": "org.springframework.dao.DataAccessException: Connection timeout\n\tat ..."
}
```

## 3. メトリクス設計

### 3.1 メトリクス種別

| 種別 | 説明 | 例 |
|------|------|-----|
| カウンター | 累積値（増加のみ） | リクエスト総数、エラー総数 |
| ゲージ | 現在値（増減） | アクティブセッション数、DB接続数 |
| ヒストグラム | 分布（P50, P95, P99） | レスポンスタイム |
| サマリー | 統計値 | リクエストサイズ |

### 3.2 収集メトリクス

#### 3.2.1 アプリケーションメトリクス

| メトリクス名 | 種別 | 説明 | ラベル |
|------------|------|------|--------|
| `http_requests_total` | Counter | HTTPリクエスト総数 | method, uri, status |
| `http_request_duration_seconds` | Histogram | リクエスト処理時間 | method, uri, status |
| `business_events_total` | Counter | 業務イベント総数 | action, status |
| `purchase_requests_total` | Counter | 申請総数 | status |
| `purchase_requests_amount_total` | Counter | 申請金額合計 | status |
| `high_amount_requests_total` | Counter | 高額申請総数 | - |
| `approval_rate` | Gauge | 承認率（%） | - |
| `rejection_rate` | Gauge | 却下率（%） | - |

#### 3.2.2 システムメトリクス

| メトリクス名 | 種別 | 説明 |
|------------|------|------|
| `jvm_memory_used_bytes` | Gauge | JVMメモリ使用量 |
| `jvm_gc_pause_seconds` | Histogram | GC停止時間 |
| `hikaricp_connections_active` | Gauge | アクティブDB接続数 |
| `hikaricp_connections_idle` | Gauge | アイドルDB接続数 |
| `hikaricp_connections_pending` | Gauge | 待機中DB接続数 |

### 3.3 Micrometer設定

#### 3.3.1 application.yml

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
```

#### 3.3.2 カスタムメトリクス実装

```java
@Component
@RequiredArgsConstructor
public class BusinessMetrics {
    
    private final MeterRegistry meterRegistry;
    
    /**
     * 業務イベントをカウント
     */
    public void recordBusinessEvent(String action, String status) {
        Counter.builder("business_events_total")
            .tag("action", action)
            .tag("status", status)
            .register(meterRegistry)
            .increment();
    }
    
    /**
     * 申請をカウント
     */
    public void recordPurchaseRequest(RequestStatus status, BigDecimal amount) {
        Counter.builder("purchase_requests_total")
            .tag("status", status.name())
            .register(meterRegistry)
            .increment();
        
        Counter.builder("purchase_requests_amount_total")
            .tag("status", status.name())
            .register(meterRegistry)
            .increment(amount.doubleValue());
    }
    
    /**
     * 高額申請をカウント
     */
    public void recordHighAmountRequest() {
        Counter.builder("high_amount_requests_total")
            .register(meterRegistry)
            .increment();
    }
    
    /**
     * 承認率を更新
     */
    public void updateApprovalRate(double rate) {
        Gauge.builder("approval_rate", () -> rate)
            .register(meterRegistry);
    }
}
```

#### 3.3.3 サービス層でのメトリクス記録

```java
@Service
@RequiredArgsConstructor
public class PurchaseRequestServiceImpl implements PurchaseRequestService {
    
    private final BusinessMetrics businessMetrics;
    
    @Override
    @Transactional
    public PurchaseRequestDto approveRequest(Long requestId, Long approverId) {
        // ビジネスロジック実行
        PurchaseRequest request = repository.findById(requestId)
            .orElseThrow(() -> new RequestNotFoundException("申請が見つかりません"));
        
        request.approve(approverId);
        PurchaseRequest saved = repository.save(request);
        
        // メトリクス記録
        businessMetrics.recordBusinessEvent("APPROVE_REQUEST", "success");
        businessMetrics.recordPurchaseRequest(RequestStatus.APPROVED, saved.getTotalAmount());
        
        return toDto(saved);
    }
}
```

### 3.4 Prometheusメトリクス出力例

```
# HELP http_requests_total HTTPリクエスト総数
# TYPE http_requests_total counter
http_requests_total{method="POST",uri="/api/v1/requests",status="200"} 1234

# HELP http_request_duration_seconds リクエスト処理時間
# TYPE http_request_duration_seconds histogram
http_request_duration_seconds_bucket{method="POST",uri="/api/v1/requests",status="200",le="0.1"} 1000
http_request_duration_seconds_bucket{method="POST",uri="/api/v1/requests",status="200",le="0.5"} 1200
http_request_duration_seconds_bucket{method="POST",uri="/api/v1/requests",status="200",le="1.0"} 1230
http_request_duration_seconds_sum{method="POST",uri="/api/v1/requests",status="200"} 123.45
http_request_duration_seconds_count{method="POST",uri="/api/v1/requests",status="200"} 1234

# HELP business_events_total 業務イベント総数
# TYPE business_events_total counter
business_events_total{action="CREATE_REQUEST",status="success"} 500
business_events_total{action="APPROVE_REQUEST",status="success"} 300
business_events_total{action="REJECT_REQUEST",status="success"} 50

# HELP purchase_requests_total 申請総数
# TYPE purchase_requests_total counter
purchase_requests_total{status="DRAFT"} 100
purchase_requests_total{status="SUBMITTED"} 200
purchase_requests_total{status="APPROVED"} 300
purchase_requests_total{status="REJECTED"} 50

# HELP high_amount_requests_total 高額申請総数
# TYPE high_amount_requests_total counter
high_amount_requests_total 150
```

## 4. アラート設計

### 4.1 アラート定義

#### 4.1.1 インフラアラート

| アラート名 | 条件 | 重要度 | 通知先 | 対応 |
|-----------|------|--------|--------|------|
| ECS CPU高使用率 | CPU > 80% が5分継続 | 警告 | Slack | スケールアウト検討 |
| ECS メモリ高使用率 | メモリ > 80% が5分継続 | 警告 | Slack | メモリリーク調査 |
| RDS CPU高使用率 | CPU > 80% が5分継続 | 警告 | Slack | クエリ最適化検討 |
| RDS 接続数上限 | 接続数 > 最大の80% | 警告 | Slack | 接続プール設定見直し |
| ALB ターゲット異常 | 異常ターゲット >= 1 | 緊急 | Slack + PagerDuty | 即座に調査 |
| ALB 5xxエラー率 | 5xxエラー率 > 5% が5分継続 | 緊急 | Slack + PagerDuty | 即座に調査 |

#### 4.1.2 アプリケーションアラート

| アラート名 | 条件 | 重要度 | 通知先 | 対応 |
|-----------|------|--------|--------|------|
| エラー率上昇 | エラー率 > 5% が5分継続 | 警告 | Slack | ログ確認 |
| レスポンスタイム悪化 | P95 > 2秒 が5分継続 | 警告 | Slack | パフォーマンス調査 |
| DB接続エラー | DB接続エラー > 10件/分 | 緊急 | Slack + PagerDuty | DB状態確認 |
| 承認率異常低下 | 承認率 < 50% が1時間継続 | 情報 | Slack | 業務状況確認 |

### 4.2 CloudWatch Alarms設定例

#### 4.2.1 ECS CPU高使用率

```hcl
resource "aws_cloudwatch_metric_alarm" "ecs_cpu_high" {
  alarm_name          = "purchase-request-prod-ecs-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = "300"
  statistic           = "Average"
  threshold           = "80"
  alarm_description   = "ECS CPU使用率が80%を超えています"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    ClusterName = "purchase-request-prod"
    ServiceName = "purchase-request-app"
  }
}
```

#### 4.2.2 ALB 5xxエラー率

```hcl
resource "aws_cloudwatch_metric_alarm" "alb_5xx_error_rate" {
  alarm_name          = "purchase-request-prod-alb-5xx-error-rate"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = "300"
  statistic           = "Sum"
  threshold           = "10"
  alarm_description   = "ALB 5xxエラーが増加しています"
  alarm_actions       = [aws_sns_topic.critical_alerts.arn]

  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
  }
}
```

### 4.3 通知設定

#### 4.3.1 Slack通知

```
【警告】ECS CPU高使用率
─────────────────────────
環境: 本番
サービス: purchase-request-app
メトリクス: CPUUtilization
現在値: 85%
閾値: 80%
期間: 5分間継続
─────────────────────────
対応: スケールアウトを検討してください
```

#### 4.3.2 PagerDuty通知（緊急時）

```
【緊急】ALB ターゲット異常
─────────────────────────
環境: 本番
サービス: purchase-request-app
異常ターゲット数: 2台
正常ターゲット数: 0台
─────────────────────────
対応: 即座にECSタスクの状態を確認してください
```

## 5. トラブルシューティング

### 5.1 典型的な障害パターン

#### 5.1.1 申請登録失敗

**症状**: 申請登録時に500エラー

**調査手順**:
1. CloudWatch Logsで該当requestIdのログを検索
2. エラーログからスタックトレースを確認
3. DB接続状態を確認（RDS メトリクス）
4. アプリケーションログで前後のイベントを確認

**ログ検索クエリ**:
```
fields @timestamp, level, action, message, context
| filter requestId = "550e8400-e29b-41d4-a716-446655440000"
| sort @timestamp desc
```

#### 5.1.2 承認操作が遅い

**症状**: 承認ボタンクリック後、レスポンスが遅い

**調査手順**:
1. Prometheusで`http_request_duration_seconds`を確認
2. CloudWatch Logsで該当リクエストのdurationを確認
3. RDS Performance Insightsでスロークエリを確認
4. アプリケーションログでN+1問題を確認

**メトリクス確認クエリ**:
```promql
histogram_quantile(0.95, 
  rate(http_request_duration_seconds_bucket{uri="/api/v1/requests/{id}/approve"}[5m])
)
```

#### 5.1.3 高額申請の注意表示が出ない

**症状**: 閾値を超えているのに警告が表示されない

**調査手順**:
1. 設定値を確認（`purchase.request.high-amount-threshold`）
2. アプリケーションログで閾値判定ロジックを確認
3. フロントエンドのコンソールログを確認
4. APIレスポンスの`isHighAmount`フラグを確認

**ログ検索クエリ**:
```
fields @timestamp, action, message, context.totalAmount, context.isHighAmount
| filter action = "CREATE_REQUEST"
| filter context.totalAmount > 50000
| sort @timestamp desc
```

### 5.2 ログ追跡手順

#### 5.2.1 requestIdによる追跡

```
# 特定リクエストの全ログを時系列で表示
fields @timestamp, level, action, message
| filter requestId = "550e8400-e29b-41d4-a716-446655440000"
| sort @timestamp asc
```

#### 5.2.2 ユーザー操作の追跡

```
# 特定ユーザーの操作履歴を表示
fields @timestamp, action, message, context
| filter userId = "user123"
| filter action in ["CREATE_REQUEST", "SUBMIT_REQUEST", "APPROVE_REQUEST", "REJECT_REQUEST"]
| sort @timestamp desc
| limit 100
```

#### 5.2.3 エラー発生状況の確認

```
# 直近1時間のエラーを集計
fields @timestamp, action, message
| filter level = "ERROR"
| stats count() by action
| sort count desc
```

### 5.3 パフォーマンス分析

#### 5.3.1 レスポンスタイム分析

```promql
# P50, P95, P99レスポンスタイム
histogram_quantile(0.50, rate(http_request_duration_seconds_bucket[5m]))
histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))
histogram_quantile(0.99, rate(http_request_duration_seconds_bucket[5m]))
```

#### 5.3.2 エンドポイント別レスポンスタイム

```promql
# エンドポイント別P95レスポンスタイム
histogram_quantile(0.95, 
  sum(rate(http_request_duration_seconds_bucket[5m])) by (uri, le)
)
```

#### 5.3.3 エラー率

```promql
# 直近5分間のエラー率
sum(rate(http_requests_total{status=~"5.."}[5m])) 
/ 
sum(rate(http_requests_total[5m])) * 100
```

## 6. ダッシュボード設計

### 6.1 CloudWatch Dashboard

#### 6.1.1 インフラダッシュボード

```
┌─────────────────────────────────────────────────────────┐
│ ECS CPU使用率                │ ECS メモリ使用率          │
│ ─────────────────────────── │ ───────────────────────  │
│ [グラフ: 時系列]              │ [グラフ: 時系列]          │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│ RDS CPU使用率                │ RDS 接続数                │
│ ─────────────────────────── │ ───────────────────────  │
│ [グラフ: 時系列]              │ [グラフ: 時系列]          │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│ ALB リクエスト数             │ ALB エラー率              │
│ ─────────────────────────── │ ───────────────────────  │
│ [グラフ: 時系列]              │ [グラフ: 時系列]          │
└─────────────────────────────────────────────────────────┘
```

#### 6.1.2 アプリケーションダッシュボード

```
┌─────────────────────────────────────────────────────────┐
│ リクエスト数（総数）          │ エラー率                  │
│ ─────────────────────────── │ ───────────────────────  │
│ [数値: 1,234 req/min]        │ [数値: 0.5%]              │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│ レスポンスタイム（P50/P95/P99）                          │
│ ───────────────────────────────────────────────────────│
│ [グラフ: 時系列、3本の線]                                │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│ 業務イベント数（アクション別） │ 申請ステータス分布        │
│ ─────────────────────────── │ ───────────────────────  │
│ [グラフ: 積み上げ棒グラフ]    │ [グラフ: 円グラフ]        │
└─────────────────────────────────────────────────────────┘
```

#### 6.1.3 業務KPIダッシュボード

```
┌─────────────────────────────────────────────────────────┐
│ 申請総数（今日）              │ 承認率（今週）            │
│ ─────────────────────────── │ ───────────────────────  │
│ [数値: 45件]                  │ [数値: 85%]               │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│ 高額申請数（今月）            │ 平均承認時間（今週）      │
│ ─────────────────────────── │ ───────────────────────  │
│ [数値: 12件]                  │ [数値: 2.5時間]           │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│ 申請金額推移（月別）                                      │
│ ───────────────────────────────────────────────────────│
│ [グラフ: 棒グラフ]                                        │
└─────────────────────────────────────────────────────────┘
```

### 6.2 Grafana Dashboard（将来拡張）

Prometheusメトリクスを可視化するGrafanaダッシュボードを構築する。

## 7. 運用チェックリスト

### 7.1 日次チェック

- [ ] エラーログの確認（ERROR, WARN）
- [ ] レスポンスタイムの確認（P95 < 2秒）
- [ ] エラー率の確認（< 1%）
- [ ] RDS接続数の確認（< 最大の80%）

### 7.2 週次チェック

- [ ] ログ保持期間の確認
- [ ] ディスク使用量の確認
- [ ] バックアップ成功の確認
- [ ] 業務KPIの確認（承認率、平均承認時間）

### 7.3 月次チェック

- [ ] コスト分析（CloudWatch Logs、メトリクス）
- [ ] パフォーマンストレンド分析
- [ ] アラート発生状況の分析
- [ ] ログ保持ポリシーの見直し

---

**作成日**: 2026-04-02  
**作成者**: AI（Plan Mode）  
**バージョン**: 1.0  
**参照仕様**: [`spec.md`](spec.md) v1.0