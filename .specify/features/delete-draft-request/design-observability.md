# 下書き削除機能 観測設計書

## 1. 設計概要

### 1.1 設計目的

下書き削除API機能の観測性（Observability）を確保するため、ログ出力、メトリクス収集、アラート設定、トラブルシューティング手順を定義する。

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

- [`spec.md`](spec.md): 下書き削除機能仕様
- [`design-observability.md`](../../../design-observability.md): 既存観測設計
- [`design-app.md`](design-app.md): アプリケーション設計

---

## 2. ログ設計

### 2.1 ログレベル定義

| レベル | 用途 | 例 |
|--------|------|-----|
| ERROR | システムエラー、予期しない例外 | DB接続失敗、予期しない例外 |
| WARN | ビジネスルール違反、リトライ可能なエラー | 権限不足、不正なステータス、既に削除済み |
| INFO | 業務イベント、重要な状態変化 | 削除リクエスト受信、削除成功 |
| DEBUG | デバッグ情報（開発環境のみ） | メソッド呼び出し、変数値 |

### 2.2 構造化ログフォーマット

#### 2.2.1 標準ログ項目

すべてのログに以下の項目を含める：

```json
{
  "timestamp": "2026-04-03T09:15:00.123Z",
  "level": "INFO",
  "logger": "com.example.purchaserequest.service.PurchaseRequestServiceImpl",
  "requestId": "req-123e4567-e89b-12d3-a456-426614174000",
  "userId": "user001",
  "action": "delete_draft",
  "message": "下書き申請を削除しました",
  "executionTimeMs": 45
}
```

#### 2.2.2 削除操作固有のログ項目

| 項目 | 型 | 説明 | 必須 |
|------|---|------|------|
| `requestId` | String | リクエストID（UUID） | ✓ |
| `userId` | String | ユーザー名 | ✓ |
| `action` | String | 操作種別（"delete_draft"） | ✓ |
| `requestIdValue` | Long | 申請ID | ✓ |
| `status` | String | 申請ステータス | ✓ |
| `executionTimeMs` | Long | 実行時間（ミリ秒） | ✓ |
| `errorCode` | String | エラーコード | エラー時のみ |
| `errorMessage` | String | エラーメッセージ | エラー時のみ |
| `ownerId` | Long | 申請者ID | 権限エラー時のみ |

### 2.3 ログ出力ポイント

#### 2.3.1 BusinessLogger への追加メソッド

**ファイルパス**: `src/main/java/com/example/purchaserequest/util/BusinessLogger.java`

```java
/**
 * 削除リクエスト受信ログ
 * 
 * @param action 操作種別
 * @param requestId 申請ID
 * @param username ユーザー名
 */
public static void logRequestReceived(String action, Long requestId, String username) {
    MDC.put("action", action);
    MDC.put("requestIdValue", String.valueOf(requestId));
    MDC.put("userId", username);
    log.info("削除リクエストを受信しました: requestId={}, username={}", requestId, username);
}

/**
 * 削除成功ログ
 * 
 * @param requestId 申請ID
 * @param username ユーザー名
 */
public static void logRequestDeleted(Long requestId, String username) {
    MDC.put("action", "delete_draft");
    MDC.put("requestIdValue", String.valueOf(requestId));
    MDC.put("userId", username);
    log.info("下書き申請を削除しました: requestId={}, username={}", requestId, username);
}

/**
 * 申請が見つからないログ
 * 
 * @param requestId 申請ID
 * @param username ユーザー名
 */
public static void logRequestNotFound(Long requestId, String username) {
    MDC.put("action", "delete_draft");
    MDC.put("requestIdValue", String.valueOf(requestId));
    MDC.put("userId", username);
    MDC.put("errorCode", "REQUEST_NOT_FOUND");
    log.warn("申請が見つかりません: requestId={}, username={}", requestId, username);
}

/**
 * 既に削除済みログ
 * 
 * @param requestId 申請ID
 * @param username ユーザー名
 */
public static void logAlreadyDeleted(Long requestId, String username) {
    MDC.put("action", "delete_draft");
    MDC.put("requestIdValue", String.valueOf(requestId));
    MDC.put("userId", username);
    MDC.put("errorCode", "ALREADY_DELETED");
    log.warn("既に削除済みの申請です: requestId={}, username={}", requestId, username);
}

/**
 * 権限不足ログ
 * 
 * @param requestId 申請ID
 * @param username ユーザー名
 * @param ownerId 申請者ID
 */
public static void logUnauthorizedDelete(Long requestId, String username, Long ownerId) {
    MDC.put("action", "delete_draft");
    MDC.put("requestIdValue", String.valueOf(requestId));
    MDC.put("userId", username);
    MDC.put("ownerId", String.valueOf(ownerId));
    MDC.put("errorCode", "FORBIDDEN");
    log.warn("権限不足: 他人の申請を削除しようとしました: requestId={}, username={}, ownerId={}", 
        requestId, username, ownerId);
}

/**
 * 不正なステータスログ
 * 
 * @param requestId 申請ID
 * @param username ユーザー名
 * @param status 申請ステータス
 */
public static void logInvalidStatusForDelete(Long requestId, String username, RequestStatus status) {
    MDC.put("action", "delete_draft");
    MDC.put("requestIdValue", String.valueOf(requestId));
    MDC.put("userId", username);
    MDC.put("status", status.name());
    MDC.put("errorCode", "INVALID_STATUS");
    log.warn("不正なステータス: 下書き以外の申請を削除しようとしました: requestId={}, username={}, status={}", 
        requestId, username, status);
}
```

#### 2.3.2 ログ出力フロー

```
1. Controller: 削除リクエスト受信
   → BusinessLogger.logRequestReceived()
   
2. Service: バリデーション
   → エラー時: BusinessLogger.logRequestNotFound() / logAlreadyDeleted() / logUnauthorizedDelete() / logInvalidStatusForDelete()
   
3. Service: 削除実行
   → BusinessLogger.logRequestDeleted()
   
4. Controller: レスポンス返却
   → メトリクス記録
```

### 2.4 ログ保持期間

| 環境 | 保持期間 | 保存先 | 備考 |
|------|---------|--------|------|
| 開発（dev） | 7日 | CloudWatch Logs | コスト削減 |
| ステージング（stg） | 30日 | CloudWatch Logs | テスト検証用 |
| 本番（prod） | 90日 | CloudWatch Logs | 監査要件 |

---

## 3. メトリクス設計

### 3.1 削除操作メトリクス

#### 3.1.1 BusinessMetrics への追加メソッド

**ファイルパス**: `src/main/java/com/example/purchaserequest/util/BusinessMetrics.java`

```java
/**
 * 削除操作カウンター
 */
public static void incrementRequestDeleted() {
    Counter.builder("purchase_request.deleted")
        .description("削除された申請の総数")
        .tag("action", "delete_draft")
        .register(meterRegistry)
        .increment();
}

/**
 * 削除失敗カウンター
 * 
 * @param reason 失敗理由（not_found/forbidden/invalid_status/already_deleted）
 */
public static void incrementDeleteFailed(String reason) {
    Counter.builder("purchase_request.delete_failed")
        .description("削除失敗の総数")
        .tag("reason", reason)
        .register(meterRegistry)
        .increment();
}

/**
 * 削除操作の実行時間
 * 
 * @param milliseconds 実行時間（ミリ秒）
 */
public static void recordDeleteExecutionTime(long milliseconds) {
    Timer.builder("purchase_request.delete_execution_time")
        .description("削除操作の実行時間")
        .register(meterRegistry)
        .record(milliseconds, TimeUnit.MILLISECONDS);
}

/**
 * ステータス別削除数
 * 
 * @param status ステータス（常にDRAFT）
 */
public static void incrementDeletedByStatus(RequestStatus status) {
    Counter.builder("purchase_request.deleted_by_status")
        .description("ステータス別削除数")
        .tag("status", status.name())
        .register(meterRegistry)
        .increment();
}
```

#### 3.1.2 メトリクス項目一覧

| メトリクス名 | 種類 | 説明 | タグ | 単位 |
|------------|------|------|------|------|
| `purchase_request.deleted` | Counter | 削除された申請の総数 | action=delete_draft | 件数 |
| `purchase_request.delete_failed` | Counter | 削除失敗の総数 | reason=(not_found/forbidden/invalid_status/already_deleted) | 件数 |
| `purchase_request.delete_execution_time` | Timer | 削除操作の実行時間 | - | ミリ秒 |
| `purchase_request.deleted_by_status` | Counter | ステータス別削除数 | status=DRAFT | 件数 |

### 3.2 メトリクス収集間隔

| メトリクス | 収集間隔 | 保持期間 |
|-----------|---------|---------|
| Counter | リアルタイム | 15ヶ月 |
| Timer | リアルタイム | 15ヶ月 |

### 3.3 メトリクスダッシュボード

**CloudWatch ダッシュボード**: `PurchaseRequest-DeleteDraft`

**ウィジェット構成**:

1. **削除操作数（時系列）**
   - メトリクス: `purchase_request.deleted`
   - 期間: 1時間
   - 統計: Sum

2. **削除失敗数（理由別）**
   - メトリクス: `purchase_request.delete_failed`
   - 期間: 1時間
   - 統計: Sum
   - グループ化: reason

3. **削除実行時間（パーセンタイル）**
   - メトリクス: `purchase_request.delete_execution_time`
   - 期間: 5分
   - 統計: p50, p95, p99

4. **削除成功率**
   - 計算式: `deleted / (deleted + delete_failed) * 100`
   - 期間: 1時間

---

## 4. アラート設計

### 4.1 アラート定義

#### 4.1.1 削除失敗率上昇アラート

**アラート名**: `DeleteFailureRateHigh`

**条件**: 削除失敗率 > 10%（5分間）

**重要度**: Warning

**通知先**: 開発チーム（Slack）

**対応アクション**:
1. CloudWatch Logsで失敗理由を確認
2. 失敗理由別の件数を確認
3. 必要に応じてホットフィックス

**CloudWatch アラーム設定**:
```hcl
resource "aws_cloudwatch_metric_alarm" "delete_failure_rate" {
  alarm_name          = "${var.environment}-purchase-request-delete-failure-rate"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  threshold           = "10"
  alarm_description   = "削除失敗率が閾値を超えました"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  
  metric_query {
    id          = "failure_rate"
    expression  = "m2 / (m1 + m2) * 100"
    label       = "削除失敗率"
    return_data = true
  }
  
  metric_query {
    id = "m1"
    metric {
      metric_name = "purchase_request.deleted"
      namespace   = "PurchaseRequest"
      period      = "300"
      stat        = "Sum"
    }
  }
  
  metric_query {
    id = "m2"
    metric {
      metric_name = "purchase_request.delete_failed"
      namespace   = "PurchaseRequest"
      period      = "300"
      stat        = "Sum"
    }
  }
}
```

#### 4.1.2 削除操作遅延アラート

**アラート名**: `DeleteExecutionTimeSlow`

**条件**: 削除実行時間 > 3秒（95パーセンタイル、5分間）

**重要度**: Warning

**通知先**: 開発チーム（Slack）

**対応アクション**:
1. データベース性能を確認
2. スロークエリログを確認
3. インデックスの有効性を確認

**CloudWatch アラーム設定**:
```hcl
resource "aws_cloudwatch_metric_alarm" "delete_execution_time_slow" {
  alarm_name          = "${var.environment}-purchase-request-delete-execution-time-slow"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "purchase_request.delete_execution_time"
  namespace           = "PurchaseRequest"
  period              = "300"
  statistic           = "p95"
  threshold           = "3000"
  alarm_description   = "削除操作の実行時間が閾値を超えました"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}
```

#### 4.1.3 権限不足エラー急増アラート

**アラート名**: `UnauthorizedDeleteSpike`

**条件**: 権限不足エラー > 10件/分

**重要度**: Critical

**通知先**: セキュリティチーム + 開発チーム（Slack + PagerDuty）

**対応アクション**:
1. 不正アクセスの可能性を調査
2. ログで該当ユーザーを特定
3. 必要に応じてアカウント停止

**CloudWatch アラーム設定**:
```hcl
resource "aws_cloudwatch_metric_alarm" "unauthorized_delete_spike" {
  alarm_name          = "${var.environment}-purchase-request-unauthorized-delete-spike"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "purchase_request.delete_failed"
  namespace           = "PurchaseRequest"
  period              = "60"
  statistic           = "Sum"
  threshold           = "10"
  alarm_description   = "権限不足エラーが急増しました"
  alarm_actions       = [aws_sns_topic.security_alerts.arn]
  
  dimensions = {
    reason = "forbidden"
  }
}
```

### 4.2 アラート通知設定

| アラート | 通知先 | 通知方法 | エスカレーション |
|---------|--------|---------|----------------|
| 削除失敗率上昇 | 開発チーム | Slack | 30分後にテックリード |
| 削除操作遅延 | 開発チーム | Slack | 30分後にインフラ担当 |
| 権限不足エラー急増 | セキュリティ + 開発 | Slack + PagerDuty | 即座にテックリード |

---

## 5. トラブルシューティング

### 5.1 典型的な障害パターンと対応

| 障害パターン | 症状 | 原因 | 対応手順 |
|------------|------|------|---------|
| 削除できない | 400エラー | ステータスがDRAFT以外 | 1. ログで現在のステータス確認<br>2. 申請詳細を確認<br>3. ステータス遷移履歴を確認 |
| 権限エラー | 403エラー | 申請者本人でない | 1. ログでrequesterIdとuserIdを比較<br>2. ユーザー情報を確認<br>3. 認証トークンを確認 |
| 削除済みエラー | 400エラー | 既に削除済み | 1. DBで削除フラグ確認<br>2. 削除日時と削除者を確認<br>3. 重複リクエストの可能性を調査 |
| 削除後も表示される | 一覧に表示 | キャッシュ未更新 | 1. キャッシュクリア<br>2. クエリに削除フラグフィルタがあるか確認<br>3. リポジトリメソッドを確認 |
| 削除操作遅延 | レスポンス遅延 | DB性能問題 | 1. スロークエリログ確認<br>2. インデックス有効性確認<br>3. DB接続プール確認 |

### 5.2 ログ追跡手順

#### 5.2.1 requestId で検索

**CloudWatch Logs Insights クエリ**:
```
fields @timestamp, level, message, requestIdValue, userId, action, status
| filter requestIdValue = "123"
| sort @timestamp desc
```

**用途**: 特定の申請に対する操作履歴を追跡

#### 5.2.2 削除操作のみ抽出

**CloudWatch Logs Insights クエリ**:
```
fields @timestamp, level, message, requestIdValue, userId, status
| filter action = "delete_draft"
| sort @timestamp desc
| limit 100
```

**用途**: 削除操作の全体傾向を把握

#### 5.2.3 エラーのみ抽出

**CloudWatch Logs Insights クエリ**:
```
fields @timestamp, level, message, requestIdValue, userId, status, errorCode
| filter (level = "WARN" or level = "ERROR") and action = "delete_draft"
| sort @timestamp desc
| limit 100
```

**用途**: エラー発生状況を把握

#### 5.2.4 ユーザー別削除数

**CloudWatch Logs Insights クエリ**:
```
fields userId
| filter action = "delete_draft" and level = "INFO" and message like /削除しました/
| stats count() as delete_count by userId
| sort delete_count desc
```

**用途**: ユーザー別の削除傾向を分析

### 5.3 データ整合性確認クエリ

#### 5.3.1 削除済み申請の確認

```sql
-- 削除済み申請の一覧
SELECT id, requester_id, status, deleted, deleted_at, deleted_by
FROM purchase_requests
WHERE deleted = TRUE
ORDER BY deleted_at DESC;
```

#### 5.3.2 データ不整合の検出

```sql
-- 削除済みだがステータスがDRAFT以外の申請（データ不整合）
SELECT id, requester_id, status, deleted, deleted_at
FROM purchase_requests
WHERE deleted = TRUE AND status != 'DRAFT';

-- 削除フラグがTRUEだが削除日時が未設定（データ不整合）
SELECT id, requester_id, deleted, deleted_at, deleted_by
FROM purchase_requests
WHERE deleted = TRUE AND (deleted_at IS NULL OR deleted_by IS NULL);

-- 削除フラグがFALSEだが削除日時が設定されている（データ不整合）
SELECT id, requester_id, deleted, deleted_at, deleted_by
FROM purchase_requests
WHERE deleted = FALSE AND (deleted_at IS NOT NULL OR deleted_by IS NOT NULL);
```

### 5.4 パフォーマンス分析

#### 5.4.1 削除操作の実行時間分析

**CloudWatch Logs Insights クエリ**:
```
fields @timestamp, executionTimeMs, requestIdValue
| filter action = "delete_draft"
| stats avg(executionTimeMs) as avg_time, 
        max(executionTimeMs) as max_time, 
        min(executionTimeMs) as min_time,
        pct(executionTimeMs, 95) as p95_time
| sort @timestamp desc
```

#### 5.4.2 スロークエリの特定

```sql
-- 削除操作のスロークエリ（PostgreSQL）
SELECT query, mean_exec_time, calls
FROM pg_stat_statements
WHERE query LIKE '%purchase_requests%' AND query LIKE '%deleted%'
ORDER BY mean_exec_time DESC
LIMIT 10;
```

---

## 6. 監査ログ

### 6.1 監査ログ項目

削除操作は監査対象として以下の情報を記録：

| 項目 | 内容 | 保持場所 | 保持期間 |
|------|------|---------|---------|
| 操作日時 | `deletedAt` | データベース | 永続 |
| 操作者 | `deletedBy` | データベース | 永続 |
| 対象申請ID | `id` | データベース | 永続 |
| 操作前ステータス | `status`（常にDRAFT） | データベース | 永続 |
| リクエストID | `requestId` | CloudWatch Logs | 90日 |
| 操作結果 | 成功/失敗 | CloudWatch Logs | 90日 |

### 6.2 監査レポート

#### 6.2.1 月次削除レポート

```sql
-- 月別削除数
SELECT 
    DATE_TRUNC('month', deleted_at) AS month,
    COUNT(*) AS delete_count,
    COUNT(DISTINCT deleted_by) AS unique_users
FROM purchase_requests
WHERE deleted = TRUE
GROUP BY DATE_TRUNC('month', deleted_at)
ORDER BY month DESC;
```

#### 6.2.2 ユーザー別削除数

```sql
-- ユーザー別削除数（上位10名）
SELECT 
    deleted_by,
    COUNT(*) AS delete_count
FROM purchase_requests
WHERE deleted = TRUE
GROUP BY deleted_by
ORDER BY delete_count DESC
LIMIT 10;
```

#### 6.2.3 削除理由分析

**CloudWatch Logs Insights クエリ**:
```
fields @timestamp, errorCode, userId
| filter action = "delete_draft" and level = "WARN"
| stats count() as error_count by errorCode
| sort error_count desc
```

---

## 7. 運用手順書

### 7.1 日次監視項目

| 項目 | 確認内容 | 閾値 | 対応 |
|------|---------|------|------|
| 削除成功率 | 削除成功率を確認 | < 95% | ログ確認、原因調査 |
| 削除実行時間 | p95レスポンスタイムを確認 | > 3秒 | DB性能確認 |
| エラー発生数 | エラーログ件数を確認 | > 100件/日 | エラー内容確認 |

### 7.2 週次監視項目

| 項目 | 確認内容 | 対応 |
|------|---------|------|
| 削除傾向分析 | 削除数の推移を確認 | 異常な増減があれば調査 |
| ユーザー別削除数 | 特定ユーザーの削除数を確認 | 異常に多い場合は調査 |
| データ整合性 | データ不整合クエリを実行 | 不整合があれば修正 |

### 7.3 月次監視項目

| 項目 | 確認内容 | 対応 |
|------|---------|------|
| 監査レポート作成 | 月次削除レポートを作成 | 経営層への報告 |
| ログ保持期間確認 | ログが適切に保持されているか確認 | 必要に応じて保持期間延長 |
| アラート設定見直し | アラート閾値の妥当性を確認 | 必要に応じて閾値調整 |

---

## 8. 観測チェックリスト

### 8.1 ログ実装チェック

- [ ] `BusinessLogger.logRequestReceived()` 実装
- [ ] `BusinessLogger.logRequestDeleted()` 実装
- [ ] `BusinessLogger.logRequestNotFound()` 実装
- [ ] `BusinessLogger.logAlreadyDeleted()` 実装
- [ ] `BusinessLogger.logUnauthorizedDelete()` 実装
- [ ] `BusinessLogger.logInvalidStatusForDelete()` 実装
- [ ] Controller層でログ出力
- [ ] Service層でログ出力
- [ ] MDCコンテキスト設定

### 8.2 メトリクス実装チェック

- [ ] `BusinessMetrics.incrementRequestDeleted()` 実装
- [ ] `BusinessMetrics.incrementDeleteFailed()` 実装
- [ ] `BusinessMetrics.recordDeleteExecutionTime()` 実装
- [ ] `BusinessMetrics.incrementDeletedByStatus()` 実装
- [ ] Controller層でメトリクス記録
- [ ] 実行時間計測

### 8.3 アラート設定チェック

- [ ] 削除失敗率上昇アラート設定
- [ ] 削除操作遅延アラート設定
- [ ] 権限不足エラー急増アラート設定
- [ ] SNSトピック設定
- [ ] Slack通知設定
- [ ] PagerDuty連携設定（本番のみ）

### 8.4 ダッシュボード設定チェック

- [ ] CloudWatchダッシュボード作成
- [ ] 削除操作数ウィジェット追加
- [ ] 削除失敗数ウィジェット追加
- [ ] 削除実行時間ウィジェット追加
- [ ] 削除成功率ウィジェット追加

---

**作成日**: 2026-04-03  
**作成者**: AI（Plan Mode）  
**バージョン**: 1.0  
**関連ドキュメント**: [`spec.md`](spec.md), [`design-app.md`](design-app.md), [`design-deployment.md`](design-deployment.md)