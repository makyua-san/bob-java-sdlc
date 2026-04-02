# 配備設計書（Phase 1）

## 1. 設計概要

### 1.1 設計目的

備品購入申請システム（MVP）の配備アーキテクチャ、インフラ構成、環境設定、デプロイメント戦略を定義する。

### 1.2 設計原則

- **Infrastructure as Code（IaC）**: Terraformによるインフラ管理
- **環境分離**: 開発・ステージング・本番環境の明確な分離
- **設定外部化**: 環境依存の設定値はコードから分離
- **自動化**: CI/CDパイプラインによる自動デプロイ

### 1.3 参照ドキュメント

- [`spec.md`](spec.md): 機能仕様
- [`research.md`](research.md): 技術調査・前提条件
- [`design-app.md`](design-app.md): アプリケーション設計

## 2. 実行環境

### 2.1 環境構成

| 環境 | 用途 | アクセス制限 | データ保持期間 |
|------|------|------------|--------------|
| 開発（dev） | 開発者の動作確認 | 開発者のみ | 1週間 |
| ステージング（stg） | 本番前の検証 | 開発者・QA | 1ヶ月 |
| 本番（prod） | エンドユーザー利用 | 全社員 | 永続 |

### 2.2 技術スタック

| レイヤー | 技術 | バージョン | 備考 |
|---------|------|-----------|------|
| ランタイム | Java | 21 | Eclipse Temurin |
| アプリケーションサーバー | Spring Boot（組み込みTomcat） | 3.x | - |
| コンテナ | Docker | 24.x | - |
| オーケストレーション | AWS ECS Fargate | - | サーバーレスコンテナ |
| データベース | Amazon RDS（PostgreSQL） | 15.x | Multi-AZ構成 |
| ロードバランサー | AWS ALB | - | HTTPS終端 |
| DNS | Amazon Route 53 | - | - |
| 証明書 | AWS ACM | - | 自動更新 |

### 2.3 リソース要件

#### 2.3.1 アプリケーションコンテナ

| 環境 | CPU | メモリ | タスク数 | 備考 |
|------|-----|--------|---------|------|
| dev | 0.25 vCPU | 512 MB | 1 | 最小構成 |
| stg | 0.5 vCPU | 1 GB | 2 | 本番相当 |
| prod | 1 vCPU | 2 GB | 2〜4 | Auto Scaling |

#### 2.3.2 データベース

| 環境 | インスタンスタイプ | ストレージ | Multi-AZ | バックアップ |
|------|------------------|-----------|----------|------------|
| dev | db.t4g.micro | 20 GB | 無効 | 1日保持 |
| stg | db.t4g.small | 50 GB | 無効 | 7日保持 |
| prod | db.t4g.medium | 100 GB | 有効 | 30日保持 |

## 3. ネットワーク構成

### 3.1 VPC設計

```
┌─────────────────────────────────────────────────────────────┐
│ VPC (10.0.0.0/16)                                           │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Public Subnet (10.0.1.0/24, AZ-a)                   │   │
│  │  ┌──────────┐                                        │   │
│  │  │   ALB    │ ← インターネットゲートウェイ           │   │
│  │  └────┬─────┘                                        │   │
│  └───────┼──────────────────────────────────────────────┘   │
│          │                                                  │
│  ┌───────▼──────────────────────────────────────────────┐   │
│  │ Private Subnet (10.0.10.0/24, AZ-a)                  │   │
│  │  ┌──────────┐  ┌──────────┐                          │   │
│  │  │ ECS Task │  │ ECS Task │                          │   │
│  │  └────┬─────┘  └────┬─────┘                          │   │
│  └───────┼─────────────┼────────────────────────────────┘   │
│          │             │                                    │
│  ┌───────▼─────────────▼────────────────────────────────┐   │
│  │ Private Subnet (10.0.20.0/24, AZ-a)                  │   │
│  │  ┌──────────────────────────────────┐                │   │
│  │  │ RDS Primary (PostgreSQL)         │                │   │
│  │  └──────────────────────────────────┘                │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Private Subnet (10.0.21.0/24, AZ-c) ※本番のみ       │   │
│  │  ┌──────────────────────────────────┐                │   │
│  │  │ RDS Standby (PostgreSQL)         │                │   │
│  │  └──────────────────────────────────┘                │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 セキュリティグループ

#### 3.2.1 ALB セキュリティグループ

| 方向 | プロトコル | ポート | ソース | 説明 |
|------|-----------|--------|--------|------|
| Inbound | HTTPS | 443 | 0.0.0.0/0 | インターネットからのHTTPSアクセス |
| Inbound | HTTP | 80 | 0.0.0.0/0 | HTTPSへリダイレクト |
| Outbound | TCP | 8080 | ECS SG | ECSタスクへの転送 |

#### 3.2.2 ECS セキュリティグループ

| 方向 | プロトコル | ポート | ソース | 説明 |
|------|-----------|--------|--------|------|
| Inbound | TCP | 8080 | ALB SG | ALBからのトラフィック |
| Outbound | TCP | 5432 | RDS SG | PostgreSQLへの接続 |
| Outbound | HTTPS | 443 | 0.0.0.0/0 | 外部API・パッケージ取得 |

#### 3.2.3 RDS セキュリティグループ

| 方向 | プロトコル | ポート | ソース | 説明 |
|------|-----------|--------|--------|------|
| Inbound | TCP | 5432 | ECS SG | ECSからのDB接続 |

## 4. コンテナ設計

### 4.1 Dockerfile

```dockerfile
# ビルドステージ
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Mavenラッパーとpom.xmlをコピー
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# 依存関係をダウンロード（キャッシュ活用）
RUN ./mvnw dependency:go-offline

# ソースコードをコピーしてビルド
COPY src src
RUN ./mvnw clean package -DskipTests

# 実行ステージ
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 非rootユーザーで実行
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# ビルド成果物をコピー
COPY --from=builder /app/target/*.jar app.jar

# ヘルスチェック
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# アプリケーション起動
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

EXPOSE 8080
```

### 4.2 .dockerignore

```
target/
.git/
.gitignore
*.md
.mvn/wrapper/maven-wrapper.jar
```

### 4.3 ECS タスク定義

```json
{
  "family": "purchase-request-app",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "executionRoleArn": "arn:aws:iam::ACCOUNT_ID:role/ecsTaskExecutionRole",
  "taskRoleArn": "arn:aws:iam::ACCOUNT_ID:role/ecsTaskRole",
  "containerDefinitions": [
    {
      "name": "app",
      "image": "ACCOUNT_ID.dkr.ecr.REGION.amazonaws.com/purchase-request-app:latest",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "SPRING_PROFILES_ACTIVE",
          "value": "prod"
        }
      ],
      "secrets": [
        {
          "name": "SPRING_DATASOURCE_URL",
          "valueFrom": "arn:aws:secretsmanager:REGION:ACCOUNT_ID:secret:db-url"
        },
        {
          "name": "SPRING_DATASOURCE_USERNAME",
          "valueFrom": "arn:aws:secretsmanager:REGION:ACCOUNT_ID:secret:db-username"
        },
        {
          "name": "SPRING_DATASOURCE_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:REGION:ACCOUNT_ID:secret:db-password"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/purchase-request-app",
          "awslogs-region": "ap-northeast-1",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": [
          "CMD-SHELL",
          "wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1"
        ],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      }
    }
  ]
}
```

## 5. 設定値管理

### 5.1 設定ファイル構成

```
src/main/resources/
├── application.yml              # 共通設定
├── application-dev.yml          # 開発環境
├── application-stg.yml          # ステージング環境
├── application-prod.yml         # 本番環境
└── logback-spring.xml           # ログ設定
```

### 5.2 application.yml（共通設定）

```yaml
spring:
  application:
    name: purchase-request-app
  
  jpa:
    hibernate:
      ddl-auto: validate  # Flywayでマイグレーション管理
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
    open-in-view: false
  
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
  
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

# アプリケーション固有設定
purchase:
  request:
    high-amount-threshold: 50000  # 高額申請閾値（円）
    page-size: 20                 # ページサイズ

# Actuator設定
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

# ログ設定
logging:
  level:
    root: INFO
    com.example.purchaserequest: INFO
    org.springframework.web: INFO
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### 5.3 application-dev.yml（開発環境）

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/purchase_request_dev
    username: dev_user
    password: dev_password
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
  
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true

logging:
  level:
    com.example.purchaserequest: DEBUG
    org.springframework.security: DEBUG

# 開発用設定
purchase:
  request:
    high-amount-threshold: 10000  # 開発環境は閾値を低く設定
```

### 5.4 application-prod.yml（本番環境）

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}  # Secrets Managerから注入
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 10
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  
  jpa:
    show-sql: false

logging:
  level:
    com.example.purchaserequest: INFO
    org.springframework.security: WARN

# 本番用設定
server:
  port: 8080
  compression:
    enabled: true
  http2:
    enabled: true
```

### 5.5 環境変数

| 変数名 | 説明 | 設定方法 | 例 |
|--------|------|---------|-----|
| SPRING_PROFILES_ACTIVE | アクティブプロファイル | ECSタスク定義 | prod |
| SPRING_DATASOURCE_URL | DB接続URL | Secrets Manager | jdbc:postgresql://... |
| SPRING_DATASOURCE_USERNAME | DBユーザー名 | Secrets Manager | app_user |
| SPRING_DATASOURCE_PASSWORD | DBパスワード | Secrets Manager | ******** |
| JAVA_OPTS | JVMオプション | ECSタスク定義 | -Xmx1024m |

## 6. データベース設計

### 6.1 RDS構成

#### 6.1.1 本番環境（Multi-AZ）

```
┌─────────────────────────────────────────┐
│ Primary Instance (AZ-a)                 │
│  - 読み書き可能                          │
│  - 自動バックアップ                      │
│  - 同期レプリケーション ──────────────┐  │
└─────────────────────────────────────────┘  │
                                            │
                                            ▼
┌─────────────────────────────────────────┐
│ Standby Instance (AZ-c)                 │
│  - 読み取り不可（スタンバイ）            │
│  - 自動フェイルオーバー                  │
└─────────────────────────────────────────┘
```

#### 6.1.2 開発・ステージング環境（Single-AZ）

```
┌─────────────────────────────────────────┐
│ Single Instance (AZ-a)                  │
│  - 読み書き可能                          │
│  - 自動バックアップ                      │
└─────────────────────────────────────────┘
```

### 6.2 接続プール設定

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20        # 最大接続数
      minimum-idle: 10             # 最小アイドル接続数
      connection-timeout: 30000    # 接続タイムアウト（30秒）
      idle-timeout: 600000         # アイドルタイムアウト（10分）
      max-lifetime: 1800000        # 最大ライフタイム（30分）
      leak-detection-threshold: 60000  # リーク検出閾値（60秒）
```

### 6.3 DBマイグレーション（Flyway）

#### 6.3.1 マイグレーションファイル構成

```
src/main/resources/db/migration/
├── V1__create_users_table.sql
├── V2__create_purchase_requests_table.sql
├── V3__add_indexes.sql
└── V4__insert_initial_data.sql
```

#### 6.3.2 V1__create_users_table.sql

```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    department VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role)
);

COMMENT ON TABLE users IS 'ユーザー';
COMMENT ON COLUMN users.username IS 'ユーザー名（ログインID）';
COMMENT ON COLUMN users.password IS 'パスワード（BCryptハッシュ）';
COMMENT ON COLUMN users.name IS '氏名';
COMMENT ON COLUMN users.department IS '部署';
COMMENT ON COLUMN users.email IS 'メールアドレス';
```

#### 6.3.3 V2__create_purchase_requests_table.sql

```sql
CREATE TABLE purchase_requests (
    id BIGSERIAL PRIMARY KEY,
    requester_id BIGINT NOT NULL REFERENCES users(id),
    item_name VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(10, 2) NOT NULL CHECK (unit_price >= 0),
    purchase_reason VARCHAR(500) NOT NULL,
    desired_delivery_date DATE,
    remarks VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    approver_id BIGINT REFERENCES users(id),
    rejection_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50) NOT NULL
);

COMMENT ON TABLE purchase_requests IS '備品購入申請';
COMMENT ON COLUMN purchase_requests.requester_id IS '申請者ID';
COMMENT ON COLUMN purchase_requests.item_name IS '備品名';
COMMENT ON COLUMN purchase_requests.quantity IS '数量';
COMMENT ON COLUMN purchase_requests.unit_price IS '単価';
COMMENT ON COLUMN purchase_requests.purchase_reason IS '購入理由';
COMMENT ON COLUMN purchase_requests.desired_delivery_date IS '希望納期';
COMMENT ON COLUMN purchase_requests.remarks IS '備考';
COMMENT ON COLUMN purchase_requests.status IS 'ステータス（DRAFT/SUBMITTED/APPROVED/REJECTED）';
COMMENT ON COLUMN purchase_requests.approver_id IS '承認者ID';
COMMENT ON COLUMN purchase_requests.rejection_reason IS '却下理由';
```

#### 6.3.4 V3__add_indexes.sql

```sql
CREATE INDEX idx_purchase_requests_status ON purchase_requests(status);
CREATE INDEX idx_purchase_requests_requester_id ON purchase_requests(requester_id);
CREATE INDEX idx_purchase_requests_created_at ON purchase_requests(created_at);
CREATE INDEX idx_purchase_requests_status_requester ON purchase_requests(status, requester_id);
```

## 7. Terraform構成

### 7.1 ディレクトリ構造

```
infra/
├── modules/
│   ├── vpc/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   └── outputs.tf
│   ├── ecs/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   └── outputs.tf
│   ├── rds/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   └── outputs.tf
│   └── alb/
│       ├── main.tf
│       ├── variables.tf
│       └── outputs.tf
├── environments/
│   ├── dev/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   └── terraform.tfvars
│   ├── stg/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   └── terraform.tfvars
│   └── prod/
│       ├── main.tf
│       ├── variables.tf
│       └── terraform.tfvars
└── backend.tf
```

### 7.2 VPCモジュール（抜粋）

```hcl
# modules/vpc/main.tf
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name        = "${var.project_name}-${var.environment}-vpc"
    Environment = var.environment
  }
}

resource "aws_subnet" "public" {
  count             = length(var.public_subnet_cidrs)
  vpc_id            = aws_vpc.main.id
  cidr_block        = var.public_subnet_cidrs[count.index]
  availability_zone = var.availability_zones[count.index]

  tags = {
    Name        = "${var.project_name}-${var.environment}-public-${count.index + 1}"
    Environment = var.environment
  }
}

resource "aws_subnet" "private_app" {
  count             = length(var.private_app_subnet_cidrs)
  vpc_id            = aws_vpc.main.id
  cidr_block        = var.private_app_subnet_cidrs[count.index]
  availability_zone = var.availability_zones[count.index]

  tags = {
    Name        = "${var.project_name}-${var.environment}-private-app-${count.index + 1}"
    Environment = var.environment
  }
}

resource "aws_subnet" "private_db" {
  count             = length(var.private_db_subnet_cidrs)
  vpc_id            = aws_vpc.main.id
  cidr_block        = var.private_db_subnet_cidrs[count.index]
  availability_zone = var.availability_zones[count.index]

  tags = {
    Name        = "${var.project_name}-${var.environment}-private-db-${count.index + 1}"
    Environment = var.environment
  }
}
```

### 7.3 RDSモジュール（抜粋）

```hcl
# modules/rds/main.tf
resource "aws_db_instance" "main" {
  identifier     = "${var.project_name}-${var.environment}-db"
  engine         = "postgres"
  engine_version = "15.4"
  instance_class = var.instance_class

  allocated_storage     = var.allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true
  kms_key_id            = var.kms_key_id

  db_name  = var.database_name
  username = var.master_username
  password = var.master_password

  multi_az               = var.multi_az
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  backup_retention_period = var.backup_retention_period
  backup_window           = "03:00-04:00"
  maintenance_window      = "mon:04:00-mon:05:00"

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]

  deletion_protection = var.environment == "prod" ? true : false
  skip_final_snapshot = var.environment != "prod"

  tags = {
    Name        = "${var.project_name}-${var.environment}-db"
    Environment = var.environment
  }
}
```

### 7.4 環境別設定（本番）

```hcl
# environments/prod/terraform.tfvars
project_name = "purchase-request"
environment  = "prod"
region       = "ap-northeast-1"

# VPC
vpc_cidr                 = "10.0.0.0/16"
public_subnet_cidrs      = ["10.0.1.0/24", "10.0.2.0/24"]
private_app_subnet_cidrs = ["10.0.10.0/24", "10.0.11.0/24"]
private_db_subnet_cidrs  = ["10.0.20.0/24", "10.0.21.0/24"]
availability_zones       = ["ap-northeast-1a", "ap-northeast-1c"]

# ECS
ecs_task_cpu    = "1024"
ecs_task_memory = "2048"
ecs_desired_count = 2
ecs_min_capacity  = 2
ecs_max_capacity  = 4

# RDS
rds_instance_class        = "db.t4g.medium"
rds_allocated_storage     = 100
rds_multi_az              = true
rds_backup_retention_period = 30
```

## 8. CI/CDパイプライン

### 8.1 GitHub Actions ワークフロー

```yaml
# .github/workflows/deploy.yml
name: Deploy to AWS

on:
  push:
    branches:
      - main
      - develop

env:
  AWS_REGION: ap-northeast-1
  ECR_REPOSITORY: purchase-request-app

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Cache Maven packages
        uses: actions/cache@v3
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
      
      - name: Run tests
        run: ./mvnw clean test
      
      - name: Run SpotBugs
        run: ./mvnw spotbugs:check
      
      - name: Run Checkstyle
        run: ./mvnw checkstyle:check
      
      - name: OWASP Dependency Check
        run: ./mvnw dependency-check:check

  build-and-push:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v2
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}
      
      - name: Login to Amazon ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v1
      
      - name: Build and push Docker image
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          IMAGE_TAG: ${{ github.sha }}
        run: |
          docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG .
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
          docker tag $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG $ECR_REGISTRY/$ECR_REPOSITORY:latest
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:latest

  deploy:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v2
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}
      
      - name: Deploy to ECS
        run: |
          aws ecs update-service \
            --cluster purchase-request-prod \
            --service purchase-request-app \
            --force-new-deployment
```

### 8.2 デプロイフロー

```
コミット → ビルド → テスト → 静的解析 → Dockerイメージビルド → ECRプッシュ → ECSデプロイ
```

### 8.3 ロールバック手順

```bash
# 前のタスク定義リビジョンにロールバック
aws ecs update-service \
  --cluster purchase-request-prod \
  --service purchase-request-app \
  --task-definition purchase-request-app:PREVIOUS_REVISION
```

## 9. 監視・アラート

### 9.1 CloudWatch メトリクス

| メトリクス | 閾値 | アラート条件 |
|-----------|------|------------|
| ECS CPU使用率 | 80% | 5分間継続 |
| ECS メモリ使用率 | 80% | 5分間継続 |
| RDS CPU使用率 | 80% | 5分間継続 |
| RDS 接続数 | 最大接続数の80% | 5分間継続 |
| ALB ターゲット異常 | 1台以上 | 即時 |
| ALB 5xxエラー率 | 5% | 5分間継続 |

### 9.2 CloudWatch Logs

| ログソース | ロググループ | 保持期間 |
|-----------|------------|---------|
| ECSタスク | /ecs/purchase-request-app | 30日 |
| RDS | /aws/rds/instance/purchase-request-prod-db/postgresql | 30日 |
| ALB | /aws/elasticloadbalancing/app/purchase-request-prod | 7日 |

## 10. バックアップ・リカバリ

### 10.1 RDSバックアップ

| 環境 | 自動バックアップ | 保持期間 | バックアップウィンドウ |
|------|----------------|---------|---------------------|
| dev | 有効 | 1日 | 03:00-04:00 UTC |
| stg | 有効 | 7日 | 03:00-04:00 UTC |
| prod | 有効 | 30日 | 03:00-04:00 UTC |

### 10.2 リカバリ手順

#### 10.2.1 ポイントインタイムリカバリ

```bash
# 特定時点にリストア
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier purchase-request-prod-db \
  --target-db-instance-identifier purchase-request-prod-db-restored \
  --restore-time 2026-04-02T12:00:00Z
```

#### 10.2.2 スナップショットからのリストア

```bash
# スナップショットからリストア
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier purchase-request-prod-db-restored \
  --db-snapshot-identifier purchase-request-prod-db-snapshot-20260402
```

## 11. セキュリティ

### 11.1 AWS Secrets Manager

| シークレット名 | 説明 | ローテーション |
|--------------|------|--------------|
| purchase-request/prod/db-url | DB接続URL | 手動 |
| purchase-request/prod/db-username | DBユーザー名 | 手動 |
| purchase-request/prod/db-password | DBパスワード | 90日 |

### 11.2 IAMロール

#### 11.2.1 ECS Task Execution Role

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "secretsmanager:GetSecretValue"
      ],
      "Resource": "*"
    }
  ]
}
```

#### 11.2.2 ECS Task Role

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject"
      ],
      "Resource": "arn:aws:s3:::purchase-request-prod-bucket/*"
    }
  ]
}
```

## 12. コスト見積もり

### 12.1 月額コスト（本番環境）

| サービス | 構成 | 月額（USD） |
|---------|------|-----------|
| ECS Fargate | 1 vCPU × 2 GB × 2タスク × 730時間 | $70 |
| RDS（PostgreSQL） | db.t4g.medium × Multi-AZ | $120 |
| ALB | 1台 | $20 |
| データ転送 | 100 GB/月 | $9 |
| CloudWatch Logs | 10 GB/月 | $5 |
| **合計** | - | **$224** |

### 12.2 コスト最適化

- **開発環境**: 夜間・週末停止（50%削減）
- **ステージング環境**: Single-AZ構成（30%削減）
- **Auto Scaling**: 負荷に応じた自動スケーリング

## 13. 運用手順

### 13.1 デプロイ手順

1. GitHub mainブランチにマージ
2. GitHub Actionsが自動実行
3. テスト・静的解析通過
4. Dockerイメージビルド・ECRプッシュ
5. ECSサービス更新（ローリングアップデート）
6. ヘルスチェック確認
7. デプロイ完了

### 13.2 緊急時対応

#### 13.2.1 サービス停止時

```bash
# ECSタスク数を0にしてサービス停止
aws ecs update-service \
  --cluster purchase-request-prod \
  --service purchase-request-app \
  --desired-count 0
```

#### 13.2.2 ロールバック

```bash
# 前のタスク定義にロールバック
aws ecs update-service \
  --cluster purchase-request-prod \
  --service purchase-request-app \
  --task-definition purchase-request-app:PREVIOUS_REVISION
```

## 14. チェックリスト

### 14.1 デプロイ前チェック

- [ ] 全テスト通過
- [ ] 静的解析クリア
- [ ] DBマイグレーションスクリプト確認
- [ ] 環境変数・シークレット設定確認
- [ ] ロールバック手順確認

### 14.2 デプロイ後チェック

- [ ] ヘルスチェック正常
- [ ] ログ出力確認
- [ ] メトリクス正常
- [ ] 主要機能の動作確認
- [ ] パフォーマンス確認

---

**作成日**: 2026-04-02  
**作成者**: AI（Plan Mode）  
**バージョン**: 1.0  
**参照仕様**: [`spec.md`](spec.md) v1.0