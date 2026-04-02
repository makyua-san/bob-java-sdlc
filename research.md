# 技術調査・前提条件整理（Phase 0）

## 1. 調査目的

備品購入申請システム（MVP）の実装に向けて、技術スタック、アーキテクチャパターン、および実装上の制約事項を明確化する。

## 2. 技術スタック

### 2.1 基盤技術

| 項目 | 技術 | バージョン | 選定理由 |
|------|------|-----------|---------|
| 言語 | Java | 21 | LTS版、最新の言語機能（Record、Pattern Matching等）を活用 |
| フレームワーク | Spring Boot | 3.x | エンタープライズ標準、豊富なエコシステム |
| ビルドツール | Maven | 3.9系 | プロジェクト標準、依存性管理の安定性 |
| データベース | PostgreSQL | 15以上 | リレーショナルDB、JSON型サポート、高い信頼性 |
| ログ | SLF4J + Logback | - | 構造化ログ（JSON）出力対応 |

### 2.2 主要ライブラリ

| ライブラリ | 用途 | 備考 |
|-----------|------|------|
| Spring Data JPA | データアクセス層 | リポジトリパターン実装 |
| Spring Security | 認証・認可 | MVP: 簡易認証、将来: 外部IdP連携 |
| Spring Validation | 入力検証 | Bean Validation (JSR-380) |
| Lombok | ボイラープレート削減 | Getter/Setter/Constructor自動生成 |
| MapStruct | オブジェクトマッピング | Entity ⇔ DTO変換 |
| Thymeleaf | サーバーサイドレンダリング | HTMLテンプレートエンジン |
| HikariCP | コネクションプール | Spring Boot標準、高性能 |
| Flyway | DBマイグレーション | バージョン管理されたスキーマ変更 |

### 2.3 テスト関連

| ライブラリ | 用途 |
|-----------|------|
| JUnit 5 | ユニットテスト |
| Mockito | モックテスト |
| AssertJ | アサーション |
| Spring Boot Test | 統合テスト |
| Testcontainers | DB統合テスト（PostgreSQLコンテナ） |

### 2.4 静的解析・品質管理

| ツール | 用途 |
|--------|------|
| SpotBugs | バグ検出 |
| Checkstyle | コーディング規約チェック |
| OWASP Dependency-Check | 依存ライブラリ脆弱性チェック |
| JaCoCo | コードカバレッジ測定 |

## 3. アーキテクチャパターン

### 3.1 レイヤードアーキテクチャ

```
┌─────────────────────────────────────┐
│   Presentation Layer (Controller)   │  ← REST API / Thymeleaf View
├─────────────────────────────────────┤
│   Application Layer (Service)       │  ← ビジネスロジック
├─────────────────────────────────────┤
│   Domain Layer (Entity/VO)          │  ← ドメインモデル
├─────────────────────────────────────┤
│   Infrastructure Layer (Repository) │  ← データアクセス
└─────────────────────────────────────┘
```

### 3.2 責務分離

| レイヤー | 責務 | 禁止事項 |
|---------|------|---------|
| Controller | HTTPリクエスト/レスポンス処理、入力検証、DTOマッピング | ビジネスロジック、直接的なDB操作 |
| Service | ビジネスロジック、トランザクション管理、ドメインルール適用 | HTTPレスポンス生成、直接的なDB操作 |
| Repository | データアクセス、CRUD操作、クエリ実行 | ビジネスロジック |
| Entity | ドメインモデル、データ構造定義 | 外部依存（DB、HTTP等） |

### 3.3 依存性注入（DI）

- コンストラクタインジェクションを標準とする
- `@Autowired`フィールドインジェクションは使用しない（テスタビリティ低下）
- インターフェースベースの設計（Service層はインターフェース定義）

## 4. データモデル設計方針

### 4.1 エンティティ設計

- JPA Entity + Lombokで定義
- 不変性を重視（可能な限り`final`フィールド）
- 値オブジェクト（Value Object）の活用

### 4.2 ステータス管理

- Enum型で定義（`RequestStatus`）
- ステータス遷移ロジックはドメイン層に集約
- 不正な遷移は例外スロー

### 4.3 監査情報

全エンティティに以下を含める：
- `createdAt`: 作成日時（自動設定）
- `updatedAt`: 更新日時（自動更新）
- `createdBy`: 作成者（ログインユーザーから取得）
- `updatedBy`: 更新者（ログインユーザーから取得）

## 5. API設計方針

### 5.1 RESTful API原則

| HTTPメソッド | 用途 | 例 |
|-------------|------|-----|
| GET | リソース取得 | `GET /api/requests` |
| POST | リソース作成 | `POST /api/requests` |
| PUT | リソース更新（全体） | `PUT /api/requests/{id}` |
| PATCH | リソース部分更新 | `PATCH /api/requests/{id}/approve` |
| DELETE | リソース削除 | `DELETE /api/requests/{id}` |

### 5.2 エンドポイント命名規則

- 複数形を使用（`/requests`、`/approvals`）
- ケバブケース（`/purchase-requests`）
- バージョニング（`/api/v1/requests`）

### 5.3 レスポンス形式

```json
{
  "status": "success",
  "data": { ... },
  "message": "操作が成功しました",
  "timestamp": "2026-04-02T14:18:00Z"
}
```

エラーレスポンス：
```json
{
  "status": "error",
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "入力値が不正です",
    "details": [
      {
        "field": "rejectionReason",
        "message": "却下理由は必須です"
      }
    ]
  },
  "timestamp": "2026-04-02T14:18:00Z"
}
```

## 6. セキュリティ要件

### 6.1 認証・認可

| 項目 | MVP実装 | 将来拡張 |
|------|---------|---------|
| 認証方式 | Spring Security + セッション | OAuth 2.0 / OIDC |
| ユーザー管理 | DB管理（User テーブル） | 外部IdP連携（Azure AD等） |
| パスワード | BCrypt ハッシュ化 | - |
| 権限管理 | ロールベース（ROLE_USER, ROLE_APPROVER） | 属性ベース（ABAC） |

### 6.2 入力検証

- Bean Validation（`@NotNull`, `@Size`, `@Min`, `@Max`等）
- カスタムバリデーター（ステータス遷移検証等）
- SQLインジェクション対策: PreparedStatement（JPA標準）
- XSS対策: Thymeleafの自動エスケープ

### 6.3 CSRF対策

- Spring SecurityのCSRFトークン機能を有効化
- APIエンドポイントは`SameSite=Strict` Cookie

## 7. パフォーマンス要件

### 7.1 目標値

| 操作 | 目標レスポンスタイム |
|------|-------------------|
| 申請一覧表示 | 1秒以内 |
| 申請登録 | 2秒以内 |
| 承認・却下 | 2秒以内 |

### 7.2 最適化戦略

- **N+1問題対策**: FETCH JOIN、EntityGraph
- **インデックス設計**: ステータス、申請者ID、申請日にインデックス
- **コネクションプール**: HikariCP（最小10、最大20接続）
- **キャッシング**: Spring Cache（申請一覧は5分キャッシュ）

## 8. 観測性（Observability）

### 8.1 ログ戦略

- **構造化ログ（JSON形式）**: Logback + Logstash Encoder
- **ログレベル**:
  - ERROR: システムエラー、予期しない例外
  - WARN: ビジネスルール違反、リトライ可能なエラー
  - INFO: 業務イベント（申請登録、承認、却下）
  - DEBUG: デバッグ情報（開発環境のみ）

### 8.2 必須ログ項目

全ログに以下を含める：
- `requestId`: リクエスト追跡ID（UUID）
- `userId`: 実行ユーザーID
- `action`: 実行アクション（`CREATE_REQUEST`, `APPROVE`, `REJECT`）
- `timestamp`: タイムスタンプ（ISO 8601形式）
- `duration`: 処理時間（ミリ秒）

### 8.3 メトリクス

- レスポンスタイム（P50, P95, P99）
- エラー率（HTTP 4xx, 5xx）
- 業務KPI（申請数、承認率、却下率）

## 9. 設定値管理

### 9.1 外部化する設定値

| 設定項目 | 設定ファイル | デフォルト値 | 備考 |
|---------|------------|------------|------|
| 高額申請閾値 | `application.yml` | 50000 | `purchase.request.high-amount-threshold` |
| ページサイズ | `application.yml` | 20 | `purchase.request.page-size` |
| セッションタイムアウト | `application.yml` | 30分 | `server.servlet.session.timeout` |
| DB接続プール | `application.yml` | 最小10、最大20 | `spring.datasource.hikari.*` |

### 9.2 環境別設定

- `application.yml`: 共通設定
- `application-dev.yml`: 開発環境
- `application-prod.yml`: 本番環境
- `application-test.yml`: テスト環境

## 10. テスト戦略

### 10.1 テストピラミッド

```
        /\
       /E2E\        ← 少数（クリティカルパスのみ）
      /------\
     / 統合   \      ← 中程度（主要フロー）
    /----------\
   / ユニット   \    ← 多数（カバレッジ80%以上）
  /--------------\
```

### 10.2 テスト種別

| テスト種別 | 対象 | ツール | カバレッジ目標 |
|-----------|------|--------|--------------|
| ユニットテスト | Service, Repository | JUnit 5, Mockito | 80%以上 |
| 統合テスト | Controller, Service, Repository | Spring Boot Test, Testcontainers | 主要フロー網羅 |
| E2Eテスト | 画面操作 | Selenium（将来） | クリティカルパス |

### 10.3 テストデータ管理

- ファクトリーパターン（`TestDataFactory`）
- Given-When-Thenパターン
- テストメソッド名は日本語（例: `高額申請の閾値を超える場合に注意表示が出ること`）

## 11. CI/CD

### 11.1 ビルドパイプライン

```
コミット → ビルド → 静的解析 → ユニットテスト → 統合テスト → デプロイ
```

### 11.2 品質ゲート

| ゲート | 基準 |
|--------|------|
| ビルド成功 | 必須 |
| ユニットテストカバレッジ | 80%以上 |
| 静的解析（SpotBugs） | 重大バグ0件 |
| 脆弱性チェック | 高リスク脆弱性0件 |

## 12. インフラ構成（IaC）

### 12.1 Terraform管理対象

- VPC、サブネット、セキュリティグループ
- RDS（PostgreSQL）
- ECS（コンテナ実行環境）
- ALB（ロードバランサー）
- CloudWatch（ログ・メトリクス）

### 12.2 環境分離

- 開発環境（dev）
- ステージング環境（stg）
- 本番環境（prod）

## 13. 制約事項・リスク

### 13.1 技術的制約

| 制約 | 影響 | 対策 |
|------|------|------|
| Java 21必須 | 実行環境の制限 | Dockerイメージで統一 |
| PostgreSQL必須 | DB選択肢の制限 | RDS Managed Serviceで運用負荷軽減 |
| サーバーサイドレンダリング | SPAと比較してUX制約 | Thymeleaf + HTMX（将来）でインタラクティブ性向上 |

### 13.2 MVP範囲外機能

以下は将来拡張として扱う：
- 複数承認者による段階的承認
- 申請の取り下げ・再申請
- 承認後の発注管理
- 予算管理機能
- 通知機能（メール、Slack等）

### 13.3 リスク

| リスク | 影響度 | 対策 |
|--------|--------|------|
| 高額閾値の頻繁な変更 | 中 | 設定ファイルで外部化、再起動不要な仕組み（将来） |
| ステータス遷移の複雑化 | 高 | ドメイン層に遷移ロジックを集約、テストで網羅 |
| 承認権限の管理 | 中 | ロールベースで単純化、将来的にABACへ移行 |

## 14. 次ステップ

Phase 0完了後、以下のPhase 1成果物を作成する：

1. **design-app.md**: アプリケーション設計（ドメインモデル、API設計、ステータス遷移）
2. **design-deployment.md**: 配備設計（実行環境、IaC、設定値管理）
3. **design-observability.md**: 観測設計（ログ項目、メトリクス、アラート）

---

**作成日**: 2026-04-02  
**作成者**: AI（Plan Mode）  
**バージョン**: 1.0