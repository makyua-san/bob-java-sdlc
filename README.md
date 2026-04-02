# 備品購入申請システム（MVP）

社内の備品購入申請・承認プロセスを効率化するWebアプリケーションです。

## 技術スタック

| カテゴリ | 技術 |
|---------|------|
| 言語 | Java 21 |
| フレームワーク | Spring Boot 3.2.5 |
| ビルドツール | Maven |
| データベース | PostgreSQL 15+ |
| ORM | Spring Data JPA (Hibernate) |
| マイグレーション | Flyway |
| 認証 | Spring Security (HTTP Basic + BCrypt) |
| ログ | SLF4J + Logback (JSON構造化ログ) |
| メトリクス | Micrometer + Prometheus |
| コンテナ | Docker |
| インフラ | AWS ECS Fargate, RDS, ALB (Terraform) |
| CI/CD | GitHub Actions |

## 前提条件

- Java 21 以上
- Maven 3.9+ （または同梱の Maven Wrapper）
- PostgreSQL 15+ （ローカル開発用）
- Docker （コンテナビルド時）

## ローカル環境セットアップ

### 1. データベースの準備

PostgreSQL をインストールし、開発用データベースを作成します。

```bash
# PostgreSQL にログイン
psql -U postgres

# データベースとユーザーを作成
CREATE DATABASE purchase_request_dev;
CREATE USER dev_user WITH PASSWORD 'dev_password';
GRANT ALL PRIVILEGES ON DATABASE purchase_request_dev TO dev_user;
\q
```

### 2. アプリケーションのビルド

```bash
# プロジェクトのクリーンビルド（テストスキップ）
mvn clean package -DskipTests

# テスト付きビルド（H2インメモリDBを使用するためPostgreSQLは不要）
mvn clean package
```

### 3. アプリケーションの起動

```bash
# 開発プロファイルで起動
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# もしくはJARから直接起動
java -jar target/purchase-request-app-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

起動後、`http://localhost:8080` でアクセスできます。

### 4. 初期ユーザー

Flywayマイグレーション（`V4__insert_initial_data.sql`）で以下のユーザーが作成されます。

| ユーザー名 | パスワード | ロール | 部署 |
|-----------|-----------|--------|------|
| yamada | password | USER | 開発部 |
| sato | password | USER, APPROVER | 管理部 |
| suzuki | password | USER | 営業部 |

## テスト実行

```bash
# 全テスト実行（H2インメモリDB使用、PostgreSQL不要）
mvn test

# 単体テストのみ
mvn test -Dtest="*Test" -DfailIfNoTests=false

# 統合テストのみ
mvn test -Dtest="*IntegrationTest" -DfailIfNoTests=false

# テストカバレッジ付き
mvn test jacoco:report
```

## API エンドポイント

すべてのAPIは HTTP Basic 認証が必要です。

| メソッド | エンドポイント | 説明 | 権限 |
|---------|---------------|------|------|
| POST | `/api/v1/requests` | 申請登録（下書き） | USER |
| POST | `/api/v1/requests/{id}/submit` | 申請提出 | USER（自分の申請のみ） |
| GET | `/api/v1/requests` | 申請一覧取得 | USER / APPROVER |
| GET | `/api/v1/requests/{id}` | 申請詳細取得 | USER / APPROVER |
| PATCH | `/api/v1/requests/{id}/approve` | 申請承認 | APPROVER |
| PATCH | `/api/v1/requests/{id}/reject` | 申請却下 | APPROVER |

### API 使用例（curl）

```bash
# 申請登録（下書き）
curl -u yamada:password -X POST http://localhost:8080/api/v1/requests \
  -H "Content-Type: application/json" \
  -d '{
    "itemName": "ノートPC",
    "quantity": 1,
    "unitPrice": 150000,
    "purchaseReason": "開発業務用"
  }'

# 申請提出
curl -u yamada:password -X POST http://localhost:8080/api/v1/requests/1/submit

# 申請一覧取得（ステータスフィルター付き）
curl -u yamada:password http://localhost:8080/api/v1/requests?status=SUBMITTED

# 申請承認（承認者のみ）
curl -u sato:password -X PATCH http://localhost:8080/api/v1/requests/1/approve

# 申請却下（承認者のみ）
curl -u sato:password -X PATCH http://localhost:8080/api/v1/requests/1/reject \
  -H "Content-Type: application/json" \
  -d '{"rejectionReason": "予算超過のため"}'
```

## Docker でのビルド・実行

```bash
# イメージビルド
docker build -t purchase-request-app .

# コンテナ起動（PostgreSQLが別途必要）
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/purchase_request_dev \
  -e SPRING_DATASOURCE_USERNAME=dev_user \
  -e SPRING_DATASOURCE_PASSWORD=dev_password \
  purchase-request-app
```

### Docker Compose（簡易構成）

```yaml
# docker-compose.yml（参考）
version: '3.8'
services:
  db:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: purchase_request_dev
      POSTGRES_USER: dev_user
      POSTGRES_PASSWORD: dev_password
    ports:
      - "5432:5432"
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/purchase_request_dev
      SPRING_DATASOURCE_USERNAME: dev_user
      SPRING_DATASOURCE_PASSWORD: dev_password
    depends_on:
      - db
```

## ヘルスチェック・メトリクス

```bash
# ヘルスチェック
curl http://localhost:8080/actuator/health

# Prometheusメトリクス
curl http://localhost:8080/actuator/prometheus

# アプリケーション情報
curl http://localhost:8080/actuator/info
```

## プロジェクト構成

```
src/main/java/com/example/purchaserequest/
├── PurchaseRequestApplication.java   # エントリーポイント
├── config/                           # 設定クラス
│   ├── ApplicationConfig.java        # アプリケーション設定（閾値等）
│   ├── SecurityConfig.java           # Spring Security設定
│   └── RequestIdFilter.java          # MDCフィルター（リクエスト追跡）
├── controller/                       # REST API
│   ├── PurchaseRequestController.java
│   └── GlobalExceptionHandler.java
├── service/                          # ビジネスロジック
│   ├── PurchaseRequestService.java
│   ├── PurchaseRequestServiceImpl.java
│   └── UserDetailsServiceImpl.java
├── repository/                       # データアクセス
│   ├── PurchaseRequestRepository.java
│   └── UserRepository.java
├── model/                            # ドメインモデル
│   ├── RequestStatus.java            # ステータスEnum
│   ├── Role.java                     # ロールEnum
│   ├── entity/                       # JPAエンティティ
│   └── dto/                          # データ転送オブジェクト
├── exception/                        # カスタム例外
└── util/                             # ユーティリティ
    ├── BusinessLogger.java           # 業務ログ
    ├── BusinessMetrics.java          # 業務メトリクス
    └── PurchaseRequestValidator.java # バリデーター

infra/                                # Terraform（AWS インフラ）
├── modules/                          # 再利用可能モジュール
│   ├── vpc/                          # VPC・サブネット
│   ├── ecs/                          # ECS Fargate
│   ├── rds/                          # RDS PostgreSQL
│   ├── alb/                          # Application Load Balancer
│   └── monitoring/                   # CloudWatch Alarms
└── environments/                     # 環境別設定
    ├── dev/
    ├── stg/
    └── prod/
```

## 設定値

| 設定キー | デフォルト値 | 説明 |
|---------|-------------|------|
| `purchase.request.high-amount-threshold` | 50000 | 高額申請閾値（円） |
| `purchase.request.page-size` | 20 | 一覧ページサイズ |

開発環境（`application-dev.yml`）では閾値が 10,000 円に設定されています。

## 参照ドキュメント

- [機能仕様書](spec.md)
- [アプリケーション設計書](design-app.md)
- [観測設計書](design-observability.md)
- [配備設計書](design-deployment.md)
- [タスクリスト](tasks.md)
