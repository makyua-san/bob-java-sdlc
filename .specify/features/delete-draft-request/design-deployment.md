# 下書き削除機能 配備設計書

## 1. 設計概要

### 1.1 設計目的

下書き削除API機能の配備アーキテクチャ、デプロイメント戦略、環境設定、リソース要件を定義する。

### 1.2 設計原則

- **既存インフラ活用**: 既存のECS Fargate環境を継続使用
- **ゼロダウンタイム**: ブルーグリーンデプロイメントによる無停止デプロイ
- **環境分離**: 開発・ステージング・本番環境の明確な分離
- **自動化**: CI/CDパイプラインによる自動デプロイ

### 1.3 参照ドキュメント

- [`spec.md`](spec.md): 下書き削除機能仕様
- [`design-deployment.md`](../../../design-deployment.md): 既存配備設計
- [`design-app.md`](design-app.md): アプリケーション設計

---

## 2. デプロイメント戦略

### 2.1 デプロイ手順

#### 2.1.1 Phase 1: データベースマイグレーション

**実行タイミング**: アプリケーションデプロイ前

**マイグレーションファイル**: `V5__add_deleted_flag_to_purchase_requests.sql`

**配置場所**: `src/main/resources/db/migration/`

**内容**:
```sql
-- 削除フラグ関連カラムの追加
ALTER TABLE purchase_requests
ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN deleted_at TIMESTAMP,
ADD COLUMN deleted_by VARCHAR(50);

-- 削除フラグのインデックス追加（検索性能向上）
CREATE INDEX idx_deleted ON purchase_requests(deleted);

-- 既存データの削除フラグをfalseに設定（デフォルト値で自動設定されるが明示的に実行）
UPDATE purchase_requests SET deleted = FALSE WHERE deleted IS NULL;

-- コメント追加（ドキュメント目的）
COMMENT ON COLUMN purchase_requests.deleted IS '論理削除フラグ（true: 削除済み、false: 未削除）';
COMMENT ON COLUMN purchase_requests.deleted_at IS '削除日時（削除時に設定）';
COMMENT ON COLUMN purchase_requests.deleted_by IS '削除者のユーザー名（削除時に設定）';
```

**実行方法**:
- Flywayによる自動マイグレーション
- アプリケーション起動時に自動実行
- 既存データへの影響なし（デフォルト値で対応）

**ロールバックSQL**:
```sql
-- インデックス削除
DROP INDEX IF EXISTS idx_deleted;

-- カラム削除
ALTER TABLE purchase_requests
DROP COLUMN IF EXISTS deleted,
DROP COLUMN IF EXISTS deleted_at,
DROP COLUMN IF EXISTS deleted_by;
```

#### 2.1.2 Phase 2: アプリケーションデプロイ

**デプロイ方式**: ブルーグリーンデプロイメント

| フェーズ | 説明 | 所要時間 | ロールバック方法 |
|---------|------|---------|----------------|
| Blue（現行） | 既存バージョン稼働 | - | - |
| Green（新規） | 新バージョンデプロイ | 5分 | Blueに切り戻し |
| ヘルスチェック | 新バージョン動作確認 | 2分 | Blueに切り戻し |
| 切り替え | ALBターゲットグループ変更 | 1分 | 即座に切り戻し可能 |
| Blue停止 | 旧バージョン停止 | 1分 | - |

**合計所要時間**: 約10分

#### 2.1.3 Phase 3: 動作確認

**確認項目**:

1. **ヘルスチェック確認**
   ```bash
   curl -X GET https://api.example.com/actuator/health
   ```
   期待レスポンス: `{"status":"UP"}`

2. **削除API動作確認**
   ```bash
   # 下書き申請を作成
   curl -X POST https://api.example.com/api/v1/requests \
     -H "Authorization: Bearer ${TOKEN}" \
     -H "Content-Type: application/json" \
     -d '{
       "itemName": "テスト備品",
       "quantity": 1,
       "unitPrice": 1000,
       "purchaseReason": "テスト"
     }'
   
   # 作成した申請を削除
   curl -X DELETE https://api.example.com/api/v1/requests/1 \
     -H "Authorization: Bearer ${TOKEN}"
   ```
   期待レスポンス: `{"status":"success","message":"下書きを削除しました"}`

3. **既存機能の回帰テスト**
   - 申請一覧取得（削除済み申請が表示されないこと）
   - 申請詳細取得（削除済み申請は404エラー）
   - 申請提出・承認・却下（既存機能が正常動作）

### 2.2 環境別デプロイ計画

#### 2.2.1 開発環境（dev）

| 項目 | 内容 |
|------|------|
| デプロイタイミング | 機能実装完了後、即座 |
| ダウンタイム | なし（ブルーグリーン） |
| 承認プロセス | 不要（開発者が直接デプロイ） |
| ロールバック判断 | 開発者判断 |

#### 2.2.2 ステージング環境（stg）

| 項目 | 内容 |
|------|------|
| デプロイタイミング | 統合テスト前 |
| ダウンタイム | なし（ブルーグリーン） |
| 承認プロセス | テックリード承認 |
| ロールバック判断 | QA担当者 + テックリード |

#### 2.2.3 本番環境（prod）

| 項目 | 内容 |
|------|------|
| デプロイタイミング | リリース計画に基づく |
| ダウンタイム | なし（ブルーグリーン） |
| 承認プロセス | PO + テックリード + 運用担当 |
| ロールバック判断 | PO + テックリード |
| デプロイ時間帯 | 業務時間外（推奨） |

---

## 3. 環境設定

### 3.1 アプリケーション設定

既存の設定ファイルへの変更は不要。論理削除は既存のトランザクション管理内で実行される。

**application.yml（変更なし）**:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:purchaserequest}
    username: ${DB_USERNAME:purchaserequest_user}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate  # Flywayによるマイグレーション管理
  flyway:
    enabled: true
    baseline-on-migrate: true
```

### 3.2 データベース接続設定

既存の設定を継続使用。追加の設定変更は不要。

**環境変数**:

| 環境変数 | 開発環境 | ステージング | 本番 |
|---------|---------|------------|------|
| DB_HOST | localhost | stg-db.example.com | prod-db.example.com |
| DB_PORT | 5432 | 5432 | 5432 |
| DB_NAME | purchaserequest | purchaserequest_stg | purchaserequest_prod |
| DB_USERNAME | purchaserequest_user | purchaserequest_user | purchaserequest_user |
| DB_PASSWORD | (Secrets Manager) | (Secrets Manager) | (Secrets Manager) |

---

## 4. リソース要件

### 4.1 追加リソース

削除機能追加による追加リソースは最小限：

| リソース | 変更内容 | 影響 | 対応 |
|---------|---------|------|------|
| CPU | 変更なし | 削除は軽量な操作 | 不要 |
| メモリ | 変更なし | 追加メモリ不要 | 不要 |
| ストレージ | +3カラム分 | 1レコードあたり約20バイト増加 | 不要 |
| ネットワーク | 変更なし | 既存API同様のトラフィック | 不要 |

### 4.2 データベース容量見積もり

| 項目 | 見積もり | 根拠 |
|------|---------|------|
| 追加カラムサイズ | 20バイト/レコード | BOOLEAN(1) + TIMESTAMP(8) + VARCHAR(50) |
| 年間申請数 | 10,000件 | 既存見積もり |
| 年間増加容量 | 200KB | 10,000 × 20バイト |
| 5年間増加容量 | 1MB | 200KB × 5年 |

**結論**: データベース容量への影響は無視できるレベル

### 4.3 ECS タスク定義

既存のタスク定義を継続使用。変更不要。

**タスク定義（変更なし）**:
```json
{
  "family": "purchase-request-app",
  "cpu": "512",
  "memory": "1024",
  "containerDefinitions": [
    {
      "name": "app",
      "image": "${ECR_REPOSITORY_URL}:${IMAGE_TAG}",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "SPRING_PROFILES_ACTIVE",
          "value": "${ENVIRONMENT}"
        }
      ],
      "secrets": [
        {
          "name": "DB_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:region:account:secret:db-password"
        }
      ]
    }
  ]
}
```

---

## 5. CI/CDパイプライン

### 5.1 パイプライン構成

既存のGitHub Actionsワークフローを継続使用。

**ワークフロー**: `.github/workflows/deploy.yml`

**トリガー**:
- `main` ブランチへのプッシュ（本番デプロイ）
- `develop` ブランチへのプッシュ（開発環境デプロイ）
- `staging` ブランチへのプッシュ（ステージングデプロイ）

**ステップ**:
1. コードチェックアウト
2. Java 21セットアップ
3. Mavenビルド
4. 単体テスト実行
5. 静的解析（SpotBugs, Checkstyle）
6. Dockerイメージビルド
7. ECRへプッシュ
8. ECSタスク定義更新
9. ECSサービス更新（ブルーグリーンデプロイ）
10. ヘルスチェック
11. Slack通知

### 5.2 デプロイ承認フロー

**開発環境**:
- 自動デプロイ（承認不要）

**ステージング環境**:
- テックリードの承認が必要
- GitHub Actionsの手動承認ステップ

**本番環境**:
- PO + テックリード + 運用担当の承認が必要
- GitHub Actionsの手動承認ステップ
- デプロイ時間帯の制限（業務時間外推奨）

---

## 6. モニタリングとアラート

### 6.1 デプロイメントメトリクス

**CloudWatch メトリクス**:

| メトリクス | 説明 | アラート閾値 |
|-----------|------|------------|
| `ecs.service.deployment.success` | デプロイ成功率 | < 95% |
| `ecs.service.deployment.duration` | デプロイ所要時間 | > 15分 |
| `ecs.service.running_tasks` | 実行中タスク数 | < 2 |
| `alb.target.health_check.success` | ヘルスチェック成功率 | < 95% |

### 6.2 デプロイ後の監視

**監視期間**: デプロイ後30分間

**監視項目**:

1. **アプリケーションログ**
   - エラーログの急増
   - 削除API呼び出しログ
   - 例外スタックトレース

2. **メトリクス**
   - レスポンスタイム
   - エラー率
   - リクエスト数

3. **ヘルスチェック**
   - `/actuator/health` エンドポイント
   - データベース接続状態

### 6.3 ロールバック判断基準

以下のいずれかに該当する場合、即座にロールバック：

| 条件 | 閾値 | 対応 |
|------|------|------|
| エラー率上昇 | > 5% | 即座にロールバック |
| レスポンスタイム悪化 | > 3秒（95パーセンタイル） | 即座にロールバック |
| ヘルスチェック失敗 | 連続3回失敗 | 即座にロールバック |
| データベース接続エラー | 発生 | 即座にロールバック |

---

## 7. ロールバック手順

### 7.1 アプリケーションロールバック

**手順**:

1. **ALBターゲットグループ切り替え**
   ```bash
   aws elbv2 modify-listener \
     --listener-arn ${LISTENER_ARN} \
     --default-actions Type=forward,TargetGroupArn=${BLUE_TARGET_GROUP_ARN}
   ```

2. **ECSサービス更新**
   ```bash
   aws ecs update-service \
     --cluster ${CLUSTER_NAME} \
     --service ${SERVICE_NAME} \
     --task-definition ${PREVIOUS_TASK_DEFINITION}
   ```

3. **動作確認**
   - ヘルスチェック確認
   - 既存機能の動作確認

**所要時間**: 約5分

### 7.2 データベースロールバック

**注意**: データベースロールバックは慎重に実施

**手順**:

1. **削除済みデータの確認**
   ```sql
   SELECT COUNT(*) FROM purchase_requests WHERE deleted = TRUE;
   ```

2. **削除済みデータが存在しない場合のみロールバック実行**
   ```sql
   -- インデックス削除
   DROP INDEX IF EXISTS idx_deleted;
   
   -- カラム削除
   ALTER TABLE purchase_requests
   DROP COLUMN IF EXISTS deleted,
   DROP COLUMN IF EXISTS deleted_at,
   DROP COLUMN IF EXISTS deleted_by;
   ```

3. **削除済みデータが存在する場合**
   - データベースロールバックは実施しない
   - アプリケーションのみロールバック
   - 削除済みデータは保持される（データ整合性確保）

---

## 8. トラブルシューティング

### 8.1 デプロイ失敗時の対応

| 障害パターン | 症状 | 原因 | 対応手順 |
|------------|------|------|---------|
| マイグレーション失敗 | アプリ起動失敗 | SQL構文エラー | ロールバックSQL実行 → 修正 → 再デプロイ |
| ヘルスチェック失敗 | タスク起動失敗 | DB接続エラー | 接続設定確認 → ロールバック |
| イメージプル失敗 | タスク起動失敗 | ECR認証エラー | IAMロール確認 → 再デプロイ |
| タスク起動失敗 | サービス異常 | リソース不足 | リソース増強 → 再デプロイ |

### 8.2 デプロイ後の問題対応

| 問題 | 症状 | 原因 | 対応手順 |
|------|------|------|---------|
| 削除APIエラー | 400/403/404エラー | ビジネスロジックエラー | ログ確認 → 修正 → ホットフィックス |
| 一覧取得遅延 | レスポンス遅延 | インデックス未作成 | インデックス確認 → 作成 |
| データ不整合 | 削除済みが表示 | キャッシュ問題 | キャッシュクリア |

### 8.3 緊急連絡先

| 役割 | 連絡先 | 対応範囲 |
|------|--------|---------|
| テックリード | tech-lead@example.com | 技術判断、ロールバック承認 |
| インフラ担当 | infra@example.com | AWS環境、ネットワーク |
| DBA | dba@example.com | データベース、マイグレーション |
| PO | po@example.com | ビジネス判断、リリース判断 |

---

## 9. デプロイチェックリスト

### 9.1 デプロイ前チェック

- [ ] マイグレーションファイル作成完了
- [ ] マイグレーションファイルのレビュー完了
- [ ] ロールバックSQL準備完了
- [ ] 単体テスト実行完了（カバレッジ80%以上）
- [ ] 統合テスト実行完了
- [ ] 静的解析実行完了（エラーなし）
- [ ] ステージング環境での動作確認完了
- [ ] デプロイ承認取得完了
- [ ] ロールバック手順確認完了
- [ ] 緊急連絡先確認完了

### 9.2 デプロイ中チェック

- [ ] マイグレーション実行成功
- [ ] Dockerイメージビルド成功
- [ ] ECRプッシュ成功
- [ ] ECSタスク起動成功
- [ ] ヘルスチェック成功
- [ ] ALB切り替え成功
- [ ] 旧タスク停止成功

### 9.3 デプロイ後チェック

- [ ] ヘルスチェック確認（30分間）
- [ ] 削除API動作確認
- [ ] 既存機能の回帰テスト
- [ ] エラーログ確認（異常なし）
- [ ] メトリクス確認（正常範囲内）
- [ ] レスポンスタイム確認（1秒以内）
- [ ] データベース接続確認
- [ ] Slack通知確認

---

## 10. 環境別設定差分

### 10.1 開発環境（dev）

| 項目 | 設定値 | 備考 |
|------|--------|------|
| DB_HOST | localhost | ローカルPostgreSQL |
| SPRING_PROFILES_ACTIVE | dev | 開発プロファイル |
| LOG_LEVEL | DEBUG | 詳細ログ出力 |
| ECS_TASK_COUNT | 1 | 最小構成 |

### 10.2 ステージング環境（stg）

| 項目 | 設定値 | 備考 |
|------|--------|------|
| DB_HOST | stg-db.example.com | RDS（Single-AZ） |
| SPRING_PROFILES_ACTIVE | stg | ステージングプロファイル |
| LOG_LEVEL | INFO | 標準ログ出力 |
| ECS_TASK_COUNT | 2 | 冗長構成 |

### 10.3 本番環境（prod）

| 項目 | 設定値 | 備考 |
|------|--------|------|
| DB_HOST | prod-db.example.com | RDS（Multi-AZ） |
| SPRING_PROFILES_ACTIVE | prod | 本番プロファイル |
| LOG_LEVEL | INFO | 標準ログ出力 |
| ECS_TASK_COUNT | 3 | 高可用性構成 |

---

**作成日**: 2026-04-03  
**作成者**: AI（Plan Mode）  
**バージョン**: 1.0  
**関連ドキュメント**: [`spec.md`](spec.md), [`design-app.md`](design-app.md), [`design-observability.md`](design-observability.md)