# 実装タスクリスト

## プロジェクト情報
- **プロジェクト名**: 備品購入申請システム（MVP）
- **作成日**: 2026-04-02
- **参照仕様**: [`spec.md`](spec.md:1) v1.0
- **参照設計**: 
  - [`design-app.md`](design-app.md:1) - アプリケーション設計
  - [`design-deployment.md`](design-deployment.md:1) - 配備設計
  - [`design-observability.md`](design-observability.md:1) - 観測設計

---

## タスク凡例
- `[ ]` 未着手
- `[-]` 進行中
- `[x]` 完了
- **P1**: 最優先（ブロッカー）
- **P2**: 高優先度
- **P3**: 中優先度
- **P4**: 低優先度

---

## US1: プロジェクト基盤構築

### 依存関係
なし（最初に実行）

### タスク

- [ ] [T1.1] [P1] [US1] Mavenプロジェクト初期化 - `pom.xml`
  - Spring Boot 3.x、Java 21の依存関係設定
  - Lombok、MapStruct、JUnit 5、Mockito、AssertJ追加
  - SpotBugs、Checkstyle、OWASP Dependency-Checkプラグイン設定

- [ ] [T1.2] [P1] [US1] パッケージ構造作成 - `src/main/java/com/example/purchaserequest/`
  - controller、service、repository、model（entity/dto）、config、exception、utilパッケージ作成

- [ ] [T1.3] [P1] [US1] 設定ファイル作成 - `src/main/resources/`
  - [`application.yml`](design-deployment.md:260-312)（共通設定）
  - [`application-dev.yml`](design-deployment.md:315-341)（開発環境）
  - [`application-prod.yml`](design-deployment.md:344-373)（本番環境）
  - `.gitignore`作成

- [ ] [T1.4] [P2] [US1] ログ設定 - `src/main/resources/logback-spring.xml`
  - [`logback-spring.xml`](design-observability.md:100-142)実装
  - 構造化ログ（JSON形式）設定
  - 環境別ログレベル設定

---

## US2: ドメインモデル実装

### 依存関係
US1完了後

### タスク

- [ ] [T2.1] [P1] [US2] Enumクラス実装 - `src/main/java/com/example/purchaserequest/model/`
  - [`RequestStatus`](design-app.md:237-256) enum実装
  - [`Role`](design-app.md:260-275) enum実装

- [ ] [T2.2] [P1] [US2] Userエンティティ実装 - `src/main/java/com/example/purchaserequest/model/entity/User.java`
  - [`User`](design-app.md:67-114)エンティティ実装
  - `hasApprovalAuthority()`メソッド実装
  - JPA アノテーション設定

- [ ] [T2.3] [P1] [US2] PurchaseRequestエンティティ実装 - `src/main/java/com/example/purchaserequest/model/entity/PurchaseRequest.java`
  - [`PurchaseRequest`](design-app.md:118-234)エンティティ実装
  - `getTotalAmount()`、`isHighAmount()`メソッド実装
  - `submit()`、`approve()`、`reject()`メソッド実装
  - インデックス設定

- [ ] [T2.4] [P2] [US2] DTOクラス実装 - `src/main/java/com/example/purchaserequest/model/dto/`
  - [`CreatePurchaseRequestDto`](design-app.md:831-859)実装（Bean Validation含む）
  - [`PurchaseRequestDto`](design-app.md:863-886)実装
  - [`RejectRequestDto`](design-app.md:890-900)実装
  - `UserSummaryDto`実装

---

## US3: データアクセス層実装

### 依存関係
US2完了後

### タスク

- [ ] [T3.1] [P1] [US3] UserRepository実装 - `src/main/java/com/example/purchaserequest/repository/UserRepository.java`
  - JpaRepositoryを継承
  - `findByUsername()`メソッド定義

- [ ] [T3.2] [P1] [US3] PurchaseRequestRepository実装 - `src/main/java/com/example/purchaserequest/repository/PurchaseRequestRepository.java`
  - [`PurchaseRequestRepository`](design-app.md:798-824)実装
  - カスタムクエリメソッド定義
  - N+1問題対策のFETCH JOIN実装

- [ ] [T3.3] [P1] [US3] Flywayマイグレーション作成 - `src/main/resources/db/migration/`
  - [`V1__create_users_table.sql`](design-deployment.md:444-469)
  - [`V2__create_purchase_requests_table.sql`](design-deployment.md:472-503)
  - [`V3__add_indexes.sql`](design-deployment.md:506-512)
  - `V4__insert_initial_data.sql`（テストデータ）

---

## US4: ビジネスロジック層実装

### 依存関係
US3完了後

### タスク

- [ ] [T4.1] [P1] [US4] カスタム例外クラス実装 - `src/main/java/com/example/purchaserequest/exception/`
  - `PurchaseRequestException`（基底例外）
  - [`InvalidStatusTransitionException`](design-app.md:654-661)
  - `UnauthorizedOperationException`
  - `RequestNotFoundException`
  - `ValidationException`

- [ ] [T4.2] [P1] [US4] バリデーター実装 - `src/main/java/com/example/purchaserequest/util/PurchaseRequestValidator.java`
  - [`PurchaseRequestValidator`](design-app.md:626-648)実装
  - ステータス遷移検証ロジック

- [ ] [T4.3] [P1] [US4] 設定クラス実装 - `src/main/java/com/example/purchaserequest/config/`
  - `ApplicationConfig`（高額申請閾値等の設定値管理）
  - `@ConfigurationProperties`使用

- [ ] [T4.4] [P1] [US4] PurchaseRequestServiceインターフェース定義 - `src/main/java/com/example/purchaserequest/service/PurchaseRequestService.java`
  - [`PurchaseRequestService`](design-app.md:727-758)インターフェース定義

- [ ] [T4.5] [P1] [US4] PurchaseRequestService実装 - `src/main/java/com/example/purchaserequest/service/PurchaseRequestServiceImpl.java`
  - `createDraft()`実装
  - `submitRequest()`実装
  - `getRequests()`実装
  - `getRequestById()`実装
  - `approveRequest()`実装
  - `rejectRequest()`実装
  - 高額申請判定ロジック実装
  - 権限チェックロジック実装

---

## US5: 観測性実装

### 依存関係
US4完了後（並行実装可能）

### タスク

- [ ] [T5.1] [P2] [US5] MDCフィルター実装 - `src/main/java/com/example/purchaserequest/config/RequestIdFilter.java`
  - [`RequestIdFilter`](design-observability.md:147-174)実装
  - requestId自動生成・伝播

- [ ] [T5.2] [P2] [US5] ログユーティリティ実装 - `src/main/java/com/example/purchaserequest/util/BusinessLogger.java`
  - [`BusinessLogger`](design-observability.md:179-226)実装
  - 業務イベントログ出力
  - エラーログ出力

- [ ] [T5.3] [P2] [US5] メトリクス実装 - `src/main/java/com/example/purchaserequest/util/BusinessMetrics.java`
  - [`BusinessMetrics`](design-observability.md:411-460)実装
  - Micrometer使用
  - カスタムメトリクス定義

- [ ] [T5.4] [P2] [US5] サービス層にログ・メトリクス追加
  - 各サービスメソッドにログ出力追加
  - 業務イベントのメトリクス記録追加
  - 処理時間計測

---

## US6: REST API実装

### 依存関係
US4、US5完了後

### タスク

- [ ] [T6.1] [P1] [US6] グローバル例外ハンドラー実装 - `src/main/java/com/example/purchaserequest/controller/GlobalExceptionHandler.java`
  - [`GlobalExceptionHandler`](design-app.md:666-720)実装
  - 統一エラーレスポンス形式
  - `@RestControllerAdvice`使用

- [ ] [T6.2] [P1] [US6] PurchaseRequestController実装 - `src/main/java/com/example/purchaserequest/controller/PurchaseRequestController.java`
  - [`POST /api/v1/requests`](design-app.md:320-360)（申請登録）
  - [`POST /api/v1/requests/{id}/submit`](design-app.md:363-397)（申請提出）
  - [`GET /api/v1/requests`](design-app.md:400-436)（申請一覧）
  - [`GET /api/v1/requests/{id}`](design-app.md:314)（申請詳細）
  - [`PATCH /api/v1/requests/{id}/approve`](design-app.md:439-471)（承認）
  - [`PATCH /api/v1/requests/{id}/reject`](design-app.md:475-519)（却下）

- [ ] [T6.3] [P2] [US6] レスポンスDTO実装 - `src/main/java/com/example/purchaserequest/model/dto/`
  - `ApiResponse`（統一レスポンス形式）
  - `ErrorResponse`、`ErrorDetail`

---

## US7: セキュリティ実装

### 依存関係
US6完了後

### タスク

- [ ] [T7.1] [P1] [US7] Spring Security設定 - `src/main/java/com/example/purchaserequest/config/SecurityConfig.java`
  - [`SecurityConfig`](design-app.md:954-977)実装
  - エンドポイント別権限設定
  - CSRF設定

- [ ] [T7.2] [P2] [US7] UserDetailsService実装 - `src/main/java/com/example/purchaserequest/service/UserDetailsServiceImpl.java`
  - Spring Security連携
  - ユーザー認証ロジック

- [ ] [T7.3] [P2] [US7] パスワードエンコーダー設定
  - BCryptPasswordEncoder Bean定義

---

## US8: 単体テスト実装

### 依存関係
US2-US7完了後（各US完了時に並行実装可能）

### タスク

- [ ] [T8.1] [P2] [US8] エンティティテスト - `src/test/java/com/example/purchaserequest/model/entity/`
  - `PurchaseRequestTest`（ステータス遷移、高額判定）
  - `UserTest`（権限チェック）

- [ ] [T8.2] [P2] [US8] サービス層テスト - `src/test/java/com/example/purchaserequest/service/`
  - `PurchaseRequestServiceImplTest`
  - Mockitoでリポジトリをモック
  - 正常系・異常系・境界値テスト

- [ ] [T8.3] [P2] [US8] リポジトリテスト - `src/test/java/com/example/purchaserequest/repository/`
  - `PurchaseRequestRepositoryTest`
  - `@DataJpaTest`使用
  - カスタムクエリメソッドのテスト

- [ ] [T8.4] [P2] [US8] コントローラーテスト - `src/test/java/com/example/purchaserequest/controller/`
  - `PurchaseRequestControllerTest`
  - `@WebMvcTest`使用
  - MockMvcでAPIテスト

- [ ] [T8.5] [P3] [US8] バリデーターテスト
  - `PurchaseRequestValidatorTest`
  - ステータス遷移ロジックのテスト

---

## US9: 統合テスト実装

### 依存関係
US8完了後

### タスク

- [ ] [T9.1] [P2] [US9] API統合テスト - `src/test/java/com/example/purchaserequest/integration/`
  - `PurchaseRequestApiIntegrationTest`
  - `@SpringBootTest`使用
  - 実際のDBを使用したE2Eテスト
  - 申請登録→提出→承認フロー
  - 申請登録→提出→却下フロー

- [ ] [T9.2] [P3] [US9] セキュリティ統合テスト
  - 権限チェックの統合テスト
  - 認証・認可フローのテスト

---

## US10: インフラ構築（Terraform）

### 依存関係
US1-US9完了後（並行実装可能）

### タスク

- [ ] [T10.1] [P2] [US10] Terraformディレクトリ構造作成 - `infra/`
  - [`modules/`](design-deployment.md:518-551)（vpc、ecs、rds、alb）
  - `environments/`（dev、stg、prod）

- [ ] [T10.2] [P2] [US10] VPCモジュール実装 - `infra/modules/vpc/`
  - [`main.tf`](design-deployment.md:556-603)
  - `variables.tf`、`outputs.tf`

- [ ] [T10.3] [P2] [US10] RDSモジュール実装 - `infra/modules/rds/`
  - [`main.tf`](design-deployment.md:607-642)
  - Multi-AZ設定（本番のみ）

- [ ] [T10.4] [P2] [US10] ECSモジュール実装 - `infra/modules/ecs/`
  - Fargateタスク定義
  - サービス定義
  - Auto Scaling設定

- [ ] [T10.5] [P2] [US10] ALBモジュール実装 - `infra/modules/alb/`
  - ロードバランサー設定
  - ターゲットグループ設定
  - HTTPS設定

- [ ] [T10.6] [P2] [US10] 環境別設定 - `infra/environments/`
  - [`prod/terraform.tfvars`](design-deployment.md:646-671)
  - `stg/terraform.tfvars`
  - `dev/terraform.tfvars`

---

## US11: コンテナ化

### 依存関係
US10完了後

### タスク

- [ ] [T11.1] [P2] [US11] Dockerfile作成 - `Dockerfile`
  - [`Dockerfile`](design-deployment.md:129-169)実装
  - マルチステージビルド
  - 非rootユーザー実行
  - ヘルスチェック設定

- [ ] [T11.2] [P2] [US11] .dockerignore作成 - `.dockerignore`
  - [`dockerignore`](design-deployment.md:172-179)実装

- [ ] [T11.3] [P2] [US11] ECSタスク定義作成 - `infra/ecs-task-definition.json`
  - [`task-definition.json`](design-deployment.md:183-243)実装
  - Secrets Manager連携

---

## US12: CI/CDパイプライン構築

### 依存関係
US11完了後

### タスク

- [ ] [T12.1] [P2] [US12] GitHub Actionsワークフロー作成 - `.github/workflows/deploy.yml`
  - [`deploy.yml`](design-deployment.md:677-765)実装
  - テスト実行
  - 静的解析（SpotBugs、Checkstyle、OWASP）
  - Dockerイメージビルド・プッシュ
  - ECSデプロイ

- [ ] [T12.2] [P3] [US12] GitHub Secrets設定
  - AWS認証情報設定
  - DB接続情報設定

---

## US13: 監視・アラート設定

### 依存関係
US10、US11完了後

### タスク

- [ ] [T13.1] [P2] [US13] CloudWatch Alarms設定 - `infra/modules/monitoring/`
  - [`ECS CPU高使用率`](design-observability.md:550-569)
  - [`ALB 5xxエラー率`](design-observability.md:574-590)
  - RDS CPU、接続数アラート

- [ ] [T13.2] [P3] [US13] CloudWatch Dashboard作成
  - [`インフラダッシュボード`](design-observability.md:741-761)
  - [`アプリケーションダッシュボード`](design-observability.md:764-781)
  - [`業務KPIダッシュボード`](design-observability.md:784-801)

- [ ] [T13.3] [P3] [US13] SNS通知設定
  - Slack連携
  - PagerDuty連携（緊急時）

---

## US14: ドキュメント整備

### 依存関係
全US完了後

### タスク

- [ ] [T14.1] [P3] [US14] README.md作成
  - プロジェクト概要
  - セットアップ手順
  - ビルド・実行方法
  - テスト実行方法

- [ ] [T14.2] [P3] [US14] API仕様書生成
  - OpenAPI (Swagger)設定
  - Springdoc使用

- [ ] [T14.3] [P3] [US14] 運用手順書作成
  - デプロイ手順
  - ロールバック手順
  - トラブルシューティング手順

- [ ] [T14.4] [P4] [US14] アーキテクチャ図作成
  - システム構成図
  - ER図
  - シーケンス図

---

## 依存関係グラフ

```
US1 (基盤構築)
 ↓
US2 (ドメインモデル)
 ↓
US3 (データアクセス層)
 ↓
US4 (ビジネスロジック層) ← US5 (観測性) [並行可能]
 ↓
US6 (REST API)
 ↓
US7 (セキュリティ)
 ↓
US8 (単体テスト) [各US完了時に並行実装可能]
 ↓
US9 (統合テスト)
 ↓
US10 (インフラ構築) [並行可能] → US11 (コンテナ化) → US12 (CI/CD)
                                                        ↓
                                                   US13 (監視・アラート)
                                                        ↓
                                                   US14 (ドキュメント)
```

---

## 並列実行可能なタスク

### フェーズ1: 基盤構築
- US1のみ（シーケンシャル）

### フェーズ2: コア実装
- US2 → US3 → US4（シーケンシャル）
- US5（US4と並行可能）

### フェーズ3: API・セキュリティ
- US6 → US7（シーケンシャル）

### フェーズ4: テスト
- US8（各US完了時に並行実装）
- US9（US8完了後）

### フェーズ5: インフラ・デプロイ
- US10（US1-9と並行可能）
- US11（US10完了後）
- US12（US11完了後）
- US13（US10、US11完了後）

### フェーズ6: 仕上げ
- US14（全US完了後）

---

## 見積もり（概算）

| ユーザーストーリー | タスク数 | 見積もり（人日） |
|------------------|---------|----------------|
| US1: 基盤構築 | 4 | 1.0 |
| US2: ドメインモデル | 4 | 2.0 |
| US3: データアクセス層 | 3 | 1.5 |
| US4: ビジネスロジック層 | 5 | 3.0 |
| US5: 観測性 | 4 | 2.0 |
| US6: REST API | 3 | 2.5 |
| US7: セキュリティ | 3 | 1.5 |
| US8: 単体テスト | 5 | 3.0 |
| US9: 統合テスト | 2 | 2.0 |
| US10: インフラ構築 | 6 | 3.0 |
| US11: コンテナ化 | 3 | 1.5 |
| US12: CI/CD | 2 | 1.5 |
| US13: 監視・アラート | 3 | 2.0 |
| US14: ドキュメント | 4 | 2.0 |
| **合計** | **51** | **28.5** |

---

## 注意事項

### テストについて
- テストは**オプション**ではなく、各実装タスクと並行して作成すること
- テストファーストではなく、実装後にテストを追加する方針
- カバレッジ目標: 80%以上

### 設定値の外部化
- 高額申請閾値は**必ず設定ファイル**で管理（ハードコーディング禁止）
- 環境依存の設定は環境変数またはSecrets Managerで管理

### ログ出力
- すべてのビジネスロジックに**構造化ログ**を出力
- requestIdによる追跡を必須とする
- 個人情報はログに出力しない

### コーディング規約
- [`Agents.md`](Agents.md:1)のJavaコーディング規約を遵守
- コメント、コミットメッセージ、テストメソッド名は**日本語**で記述

---

**作成日**: 2026-04-02  
**作成者**: AI（Tasks Mode）  
**バージョン**: 1.0  
**総タスク数**: 51  
**見積もり**: 28.5人日