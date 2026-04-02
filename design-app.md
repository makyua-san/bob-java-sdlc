# アプリケーション設計書（Phase 1）

## 1. 設計概要

### 1.1 設計目的

備品購入申請システム（MVP）のアプリケーション層の詳細設計を定義する。ドメインモデル、API設計、ステータス遷移、バリデーション設計を含む。

### 1.2 設計原則

- **仕様駆動**: [`spec.md`](spec.md) の要件を完全に満たす
- **レイヤード アーキテクチャ**: Controller / Service / Repository の責務分離
- **ドメイン駆動**: ビジネスルールはドメイン層に集約
- **テスタビリティ**: DIとインターフェースによる疎結合

### 1.3 参照ドキュメント

- [`spec.md`](spec.md): 機能仕様
- [`research.md`](research.md): 技術調査・前提条件
- [`Agents.md`](Agents.md): プロジェクト開発ルール

## 2. ドメインモデル

### 2.1 エンティティ関連図

```
┌─────────────────────┐
│   User              │
│  ─────────────────  │
│  - id: Long         │
│  - username: String │
│  - password: String │
│  - name: String     │
│  - department: String│
│  - email: String    │
│  - roles: Set<Role> │
└──────────┬──────────┘
           │ 1
           │
           │ *
┌──────────▼──────────────────────────┐
│   PurchaseRequest                   │
│  ─────────────────────────────────  │
│  - id: Long                         │
│  - requesterId: Long (FK)           │
│  - itemName: String                 │
│  - quantity: Integer                │
│  - unitPrice: BigDecimal            │
│  - totalAmount: BigDecimal (計算)   │
│  - purchaseReason: String           │
│  - desiredDeliveryDate: LocalDate   │
│  - remarks: String                  │
│  - status: RequestStatus (Enum)     │
│  - approverId: Long (FK, nullable)  │
│  - rejectionReason: String (nullable)│
│  - createdAt: LocalDateTime         │
│  - updatedAt: LocalDateTime         │
│  - createdBy: String                │
│  - updatedBy: String                │
└─────────────────────────────────────┘
```

### 2.2 エンティティ詳細

#### 2.2.1 User（ユーザー）

```java
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 50)
    private String username;
    
    @Column(nullable = false)
    private String password; // BCryptハッシュ化
    
    @Column(nullable = false, length = 100)
    private String name; // 氏名
    
    @Column(nullable = false, length = 100)
    private String department; // 部署
    
    @Column(nullable = false, length = 100)
    private String email; // 連絡先
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Set<Role> roles; // ロール（ROLE_USER, ROLE_APPROVER）
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * 承認権限を持つかチェック
     */
    public boolean hasApprovalAuthority() {
        return roles.contains(Role.ROLE_APPROVER);
    }
}
```

#### 2.2.2 PurchaseRequest（備品購入申請）

```java
@Entity
@Table(name = "purchase_requests", indexes = {
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_requester_id", columnList = "requester_id"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PurchaseRequest {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "requester_id", nullable = false)
    private Long requesterId; // 申請者ID
    
    @Column(nullable = false, length = 100)
    private String itemName; // 備品名
    
    @Column(nullable = false)
    private Integer quantity; // 数量
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice; // 単価
    
    @Column(nullable = false, length = 500)
    private String purchaseReason; // 購入理由
    
    @Column
    private LocalDate desiredDeliveryDate; // 希望納期（任意）
    
    @Column(length = 500)
    private String remarks; // 備考（任意）
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status; // ステータス
    
    @Column(name = "approver_id")
    private Long approverId; // 承認者ID（承認・却下後に設定）
    
    @Column(length = 500)
    private String rejectionReason; // 却下理由（却下時のみ）
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(nullable = false, updatable = false, length = 50)
    private String createdBy;
    
    @Column(nullable = false, length = 50)
    private String updatedBy;
    
    /**
     * 合計金額を計算
     */
    public BigDecimal getTotalAmount() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
    
    /**
     * 高額申請かどうかを判定
     * @param threshold 閾値
     */
    public boolean isHighAmount(BigDecimal threshold) {
        return getTotalAmount().compareTo(threshold) > 0;
    }
    
    /**
     * 申請を提出（下書き → 申請済み）
     */
    public void submit() {
        if (status != RequestStatus.DRAFT) {
            throw new IllegalStateException("下書き状態の申請のみ提出できます");
        }
        this.status = RequestStatus.SUBMITTED;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 申請を承認
     * @param approverId 承認者ID
     */
    public void approve(Long approverId) {
        if (status != RequestStatus.SUBMITTED) {
            throw new IllegalStateException("申請済み状態の申請のみ承認できます");
        }
        this.status = RequestStatus.APPROVED;
        this.approverId = approverId;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 申請を却下
     * @param approverId 承認者ID
     * @param rejectionReason 却下理由
     */
    public void reject(Long approverId, String rejectionReason) {
        if (status != RequestStatus.SUBMITTED) {
            throw new IllegalStateException("申請済み状態の申請のみ却下できます");
        }
        if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
            throw new IllegalArgumentException("却下理由は必須です");
        }
        this.status = RequestStatus.REJECTED;
        this.approverId = approverId;
        this.rejectionReason = rejectionReason;
        this.updatedAt = LocalDateTime.now();
    }
}
```

#### 2.2.3 RequestStatus（申請ステータス）

```java
public enum RequestStatus {
    DRAFT("下書き"),
    SUBMITTED("申請済み"),
    APPROVED("承認済み"),
    REJECTED("却下");
    
    private final String displayName;
    
    RequestStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}
```

#### 2.2.4 Role（ロール）

```java
public enum Role {
    ROLE_USER("一般ユーザー"),
    ROLE_APPROVER("承認者");
    
    private final String displayName;
    
    Role(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}
```

### 2.3 値オブジェクト（Value Object）

#### 2.3.1 Money（金額）

```java
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Money {
    
    @Column(precision = 10, scale = 2)
    private BigDecimal amount;
    
    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }
    
    public Money multiply(int quantity) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)));
    }
    
    public boolean isGreaterThan(Money other) {
        return this.amount.compareTo(other.amount) > 0;
    }
}
```

## 3. API設計

### 3.1 エンドポイント一覧

| HTTPメソッド | エンドポイント | 説明 | 権限 |
|-------------|---------------|------|------|
| POST | `/api/v1/requests` | 申請登録（下書き） | USER |
| POST | `/api/v1/requests/{id}/submit` | 申請提出 | USER（自分の申請のみ） |
| GET | `/api/v1/requests` | 申請一覧取得 | USER（自分の申請）、APPROVER（全申請） |
| GET | `/api/v1/requests/{id}` | 申請詳細取得 | USER（自分の申請）、APPROVER |
| PATCH | `/api/v1/requests/{id}/approve` | 申請承認 | APPROVER |
| PATCH | `/api/v1/requests/{id}/reject` | 申請却下 | APPROVER |

### 3.2 API詳細仕様

#### 3.2.1 申請登録（下書き）

**エンドポイント**: `POST /api/v1/requests`

**リクエスト**:
```json
{
  "itemName": "ノートPC",
  "quantity": 1,
  "unitPrice": 150000,
  "purchaseReason": "開発業務用",
  "desiredDeliveryDate": "2026-05-01",
  "remarks": "メモリ16GB以上"
}
```

**レスポンス（成功）**:
```json
{
  "status": "success",
  "data": {
    "id": 1,
    "itemName": "ノートPC",
    "quantity": 1,
    "unitPrice": 150000,
    "totalAmount": 150000,
    "purchaseReason": "開発業務用",
    "desiredDeliveryDate": "2026-05-01",
    "remarks": "メモリ16GB以上",
    "status": "DRAFT",
    "requester": {
      "id": 1,
      "name": "山田太郎",
      "department": "開発部"
    },
    "createdAt": "2026-04-02T14:20:00Z"
  },
  "message": "申請を下書き保存しました",
  "timestamp": "2026-04-02T14:20:00Z"
}
```

#### 3.2.2 申請提出

**エンドポイント**: `POST /api/v1/requests/{id}/submit`

**レスポンス（成功）**:
```json
{
  "status": "success",
  "data": {
    "id": 1,
    "status": "SUBMITTED",
    "updatedAt": "2026-04-02T14:21:00Z"
  },
  "message": "申請を提出しました",
  "timestamp": "2026-04-02T14:21:00Z"
}
```

**レスポンス（エラー: 下書き以外）**:
```json
{
  "status": "error",
  "error": {
    "code": "INVALID_STATUS_TRANSITION",
    "message": "下書き状態の申請のみ提出できます",
    "details": [
      {
        "field": "status",
        "currentValue": "SUBMITTED",
        "message": "既に提出済みです"
      }
    ]
  },
  "timestamp": "2026-04-02T14:21:00Z"
}
```

#### 3.2.3 申請一覧取得

**エンドポイント**: `GET /api/v1/requests?status=SUBMITTED&page=0&size=20`

**クエリパラメータ**:
- `status`: ステータス絞り込み（DRAFT, SUBMITTED, APPROVED, REJECTED）
- `page`: ページ番号（0始まり）
- `size`: ページサイズ（デフォルト: 20）

**レスポンス（成功）**:
```json
{
  "status": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "itemName": "ノートPC",
        "quantity": 1,
        "totalAmount": 150000,
        "status": "SUBMITTED",
        "isHighAmount": true,
        "requester": {
          "id": 1,
          "name": "山田太郎",
          "department": "開発部"
        },
        "createdAt": "2026-04-02T14:20:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  },
  "timestamp": "2026-04-02T14:22:00Z"
}
```

#### 3.2.4 申請承認

**エンドポイント**: `PATCH /api/v1/requests/{id}/approve`

**レスポンス（成功）**:
```json
{
  "status": "success",
  "data": {
    "id": 1,
    "status": "APPROVED",
    "approver": {
      "id": 2,
      "name": "佐藤花子"
    },
    "updatedAt": "2026-04-02T14:23:00Z"
  },
  "message": "申請を承認しました",
  "timestamp": "2026-04-02T14:23:00Z"
}
```

**レスポンス（エラー: 権限なし）**:
```json
{
  "status": "error",
  "error": {
    "code": "FORBIDDEN",
    "message": "承認権限がありません",
    "details": []
  },
  "timestamp": "2026-04-02T14:23:00Z"
}
```

#### 3.2.5 申請却下

**エンドポイント**: `PATCH /api/v1/requests/{id}/reject`

**リクエスト**:
```json
{
  "rejectionReason": "予算超過のため"
}
```

**レスポンス（成功）**:
```json
{
  "status": "success",
  "data": {
    "id": 1,
    "status": "REJECTED",
    "rejectionReason": "予算超過のため",
    "approver": {
      "id": 2,
      "name": "佐藤花子"
    },
    "updatedAt": "2026-04-02T14:24:00Z"
  },
  "message": "申請を却下しました",
  "timestamp": "2026-04-02T14:24:00Z"
}
```

**レスポンス（エラー: 却下理由なし）**:
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
  "timestamp": "2026-04-02T14:24:00Z"
}
```

## 4. ステータス遷移

### 4.1 遷移図

```
┌─────────┐
│  DRAFT  │ 下書き
└────┬────┘
     │ submit()
     ▼
┌──────────┐
│SUBMITTED │ 申請済み
└────┬─────┘
     │
     ├─ approve() ──► ┌──────────┐
     │                 │ APPROVED │ 承認済み
     │                 └──────────┘
     │
     └─ reject() ───► ┌──────────┐
                       │ REJECTED │ 却下
                       └──────────┘
```

### 4.2 遷移ルール

| 現在のステータス | 許可される操作 | 遷移後のステータス | 備考 |
|----------------|--------------|------------------|------|
| DRAFT | submit() | SUBMITTED | 申請者のみ実行可能 |
| SUBMITTED | approve() | APPROVED | 承認者のみ実行可能 |
| SUBMITTED | reject() | REJECTED | 承認者のみ実行可能、却下理由必須 |
| APPROVED | - | - | 終端状態 |
| REJECTED | - | - | 終端状態 |

### 4.3 不正遷移の検出

以下の遷移は例外をスローする：
- DRAFT → APPROVED（承認は申請済みからのみ）
- SUBMITTED → DRAFT（逆方向遷移不可）
- APPROVED → REJECTED（終端状態からの遷移不可）
- REJECTED → APPROVED（終端状態からの遷移不可）

## 5. バリデーション設計

### 5.1 入力検証ルール

#### 5.1.1 PurchaseRequest（申請登録時）

| フィールド | 検証ルール | エラーメッセージ |
|-----------|-----------|----------------|
| itemName | 必須、最大100文字 | "備品名は必須です"、"備品名は100文字以内で入力してください" |
| quantity | 必須、1以上 | "数量は必須です"、"数量は1以上で入力してください" |
| unitPrice | 必須、0以上 | "単価は必須です"、"単価は0以上で入力してください" |
| purchaseReason | 必須、最大500文字 | "購入理由は必須です"、"購入理由は500文字以内で入力してください" |
| desiredDeliveryDate | 任意、未来日付 | "希望納期は未来の日付を指定してください" |
| remarks | 任意、最大500文字 | "備考は500文字以内で入力してください" |

#### 5.1.2 RejectionRequest（却下時）

| フィールド | 検証ルール | エラーメッセージ |
|-----------|-----------|----------------|
| rejectionReason | 必須、最大500文字、空文字不可 | "却下理由は必須です"、"却下理由は500文字以内で入力してください" |

### 5.2 ビジネスルール検証

| ルール | 検証タイミング | エラーメッセージ |
|--------|--------------|----------------|
| ステータス遷移 | 操作実行時 | "下書き状態の申請のみ提出できます" |
| 承認権限 | 承認・却下時 | "承認権限がありません" |
| 申請者本人確認 | 提出時 | "自分の申請のみ提出できます" |
| 却下理由必須 | 却下時 | "却下理由は必須です" |

### 5.3 バリデーション実装

#### 5.3.1 Bean Validation

```java
public class CreatePurchaseRequestDto {
    
    @NotBlank(message = "備品名は必須です")
    @Size(max = 100, message = "備品名は100文字以内で入力してください")
    private String itemName;
    
    @NotNull(message = "数量は必須です")
    @Min(value = 1, message = "数量は1以上で入力してください")
    private Integer quantity;
    
    @NotNull(message = "単価は必須です")
    @DecimalMin(value = "0", message = "単価は0以上で入力してください")
    private BigDecimal unitPrice;
    
    @NotBlank(message = "購入理由は必須です")
    @Size(max = 500, message = "購入理由は500文字以内で入力してください")
    private String purchaseReason;
    
    @Future(message = "希望納期は未来の日付を指定してください")
    private LocalDate desiredDeliveryDate;
    
    @Size(max = 500, message = "備考は500文字以内で入力してください")
    private String remarks;
}
```

#### 5.3.2 カスタムバリデーター

```java
@Component
public class PurchaseRequestValidator {
    
    /**
     * ステータス遷移の妥当性を検証
     */
    public void validateStatusTransition(RequestStatus current, RequestStatus target) {
        if (!isValidTransition(current, target)) {
            throw new InvalidStatusTransitionException(
                String.format("ステータス遷移が不正です: %s → %s", current, target)
            );
        }
    }
    
    private boolean isValidTransition(RequestStatus current, RequestStatus target) {
        return switch (current) {
            case DRAFT -> target == RequestStatus.SUBMITTED;
            case SUBMITTED -> target == RequestStatus.APPROVED || target == RequestStatus.REJECTED;
            case APPROVED, REJECTED -> false;
        };
    }
}
```

## 6. 例外設計

### 6.1 カスタム例外階層

```
RuntimeException
  └─ PurchaseRequestException（基底例外）
       ├─ InvalidStatusTransitionException（不正なステータス遷移）
       ├─ UnauthorizedOperationException（権限不足）
       ├─ RequestNotFoundException（申請が見つからない）
       └─ ValidationException（バリデーションエラー）
```

### 6.2 例外ハンドリング

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStatusTransition(
            InvalidStatusTransitionException ex) {
        ErrorResponse error = ErrorResponse.builder()
            .status("error")
            .error(ErrorDetail.builder()
                .code("INVALID_STATUS_TRANSITION")
                .message(ex.getMessage())
                .build())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(UnauthorizedOperationException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedOperation(
            UnauthorizedOperationException ex) {
        ErrorResponse error = ErrorResponse.builder()
            .status("error")
            .error(ErrorDetail.builder()
                .code("FORBIDDEN")
                .message(ex.getMessage())
                .build())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        List<ErrorDetail.FieldError> details = fieldErrors.stream()
            .map(fe -> new ErrorDetail.FieldError(
                fe.getField(),
                fe.getDefaultMessage()
            ))
            .toList();
        
        ErrorResponse error = ErrorResponse.builder()
            .status("error")
            .error(ErrorDetail.builder()
                .code("VALIDATION_ERROR")
                .message("入力値が不正です")
                .details(details)
                .build())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
```

## 7. サービス層設計

### 7.1 PurchaseRequestService

```java
public interface PurchaseRequestService {
    
    /**
     * 申請を登録（下書き）
     */
    PurchaseRequestDto createDraft(CreatePurchaseRequestDto dto, Long requesterId);
    
    /**
     * 申請を提出
     */
    PurchaseRequestDto submitRequest(Long requestId, Long requesterId);
    
    /**
     * 申請一覧を取得
     */
    Page<PurchaseRequestDto> getRequests(RequestStatus status, Long userId, boolean isApprover, Pageable pageable);
    
    /**
     * 申請詳細を取得
     */
    PurchaseRequestDto getRequestById(Long requestId, Long userId, boolean isApprover);
    
    /**
     * 申請を承認
     */
    PurchaseRequestDto approveRequest(Long requestId, Long approverId);
    
    /**
     * 申請を却下
     */
    PurchaseRequestDto rejectRequest(Long requestId, Long approverId, String rejectionReason);
}
```

### 7.2 ビジネスロジック

#### 7.2.1 高額申請判定

```java
@Service
public class PurchaseRequestServiceImpl implements PurchaseRequestService {
    
    @Value("${purchase.request.high-amount-threshold:50000}")
    private BigDecimal highAmountThreshold;
    
    private boolean isHighAmount(PurchaseRequest request) {
        return request.isHighAmount(highAmountThreshold);
    }
}
```

#### 7.2.2 権限チェック

```java
private void validateApprovalAuthority(User user) {
    if (!user.hasApprovalAuthority()) {
        throw new UnauthorizedOperationException("承認権限がありません");
    }
}

private void validateOwnership(PurchaseRequest request, Long userId) {
    if (!request.getRequesterId().equals(userId)) {
        throw new UnauthorizedOperationException("自分の申請のみ操作できます");
    }
}
```

## 8. リポジトリ層設計

### 8.1 PurchaseRequestRepository

```java
public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long> {
    
    /**
     * 申請者IDとステータスで検索
     */
    Page<PurchaseRequest> findByRequesterIdAndStatus(
        Long requesterId, 
        RequestStatus status, 
        Pageable pageable
    );
    
    /**
     * 申請者IDで検索
     */
    Page<PurchaseRequest> findByRequesterId(Long requesterId, Pageable pageable);
    
    /**
     * ステータスで検索
     */
    Page<PurchaseRequest> findByStatus(RequestStatus status, Pageable pageable);
    
    /**
     * 申請者IDとIDで検索
     */
    Optional<PurchaseRequest> findByIdAndRequesterId(Long id, Long requesterId);
}
```

## 9. DTO設計

### 9.1 CreatePurchaseRequestDto（申請登録）

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreatePurchaseRequestDto {
    @NotBlank(message = "備品名は必須です")
    @Size(max = 100, message = "備品名は100文字以内で入力してください")
    private String itemName;
    
    @NotNull(message = "数量は必須です")
    @Min(value = 1, message = "数量は1以上で入力してください")
    private Integer quantity;
    
    @NotNull(message = "単価は必須です")
    @DecimalMin(value = "0", message = "単価は0以上で入力してください")
    private BigDecimal unitPrice;
    
    @NotBlank(message = "購入理由は必須です")
    @Size(max = 500, message = "購入理由は500文字以内で入力してください")
    private String purchaseReason;
    
    @Future(message = "希望納期は未来の日付を指定してください")
    private LocalDate desiredDeliveryDate;
    
    @Size(max = 500, message = "備考は500文字以内で入力してください")
    private String remarks;
}
```

### 9.2 PurchaseRequestDto（レスポンス）

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseRequestDto {
    private Long id;
    private String itemName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String purchaseReason;
    private LocalDate desiredDeliveryDate;
    private String remarks;
    private RequestStatus status;
    private boolean isHighAmount;
    private UserSummaryDto requester;
    private UserSummaryDto approver;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 9.3 RejectRequestDto（却下）

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RejectRequestDto {
    @NotBlank(message = "却下理由は必須です")
    @Size(max = 500, message = "却下理由は500文字以内で入力してください")
    private String rejectionReason;
}
```

## 10. 画面設計（概要）

### 10.1 申請登録画面

**URL**: `/requests/new`

**表示項目**:
- 備品名（テキスト入力）
- 数量（数値入力）
- 単価（数値入力）
- 合計金額（自動計算、読み取り専用）
- 購入理由（テキストエリア）
- 希望納期（日付選択）
- 備考（テキストエリア）
- 高額申請警告（閾値超過時に表示）

**ボタン**:
- 「下書き保存」: DRAFT状態で保存
- 「申請する」: SUBMITTED状態で保存

### 10.2 申請一覧画面

**URL**: `/requests`

**表示項目**:
- 申請日
- 備品名
- 合計金額
- ステータス
- 承認者名（承認・却下後）
- 高額申請アイコン（⚠️）

**フィルター**:
- ステータス絞り込み（全て / 下書き / 申請済み / 承認済み / 却下）

### 10.3 承認・却下画面

**URL**: `/requests/{id}/approve`

**表示項目**:
- 申請詳細（全項目）
- 高額申請警告（該当時）

**ボタン**:
- 「承認」: 承認処理実行
- 「却下」: 却下理由入力モーダル表示

## 11. セキュリティ設計

### 11.1 認証・認可

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/requests/*/approve", "/api/v1/requests/*/reject")
                    .hasRole("APPROVER")
                .requestMatchers("/api/v1/requests/**")
                    .hasAnyRole("USER", "APPROVER")
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            );
        return http.build();
    }
}
```

### 11.2 入力サニタイゼーション

- Thymeleafの自動エスケープ（XSS対策）
- PreparedStatementによるSQLインジェクション対策
- Bean Validationによる入力検証

## 12. パフォーマンス最適化

### 12.1 N+1問題対策

```java
@Query("SELECT r FROM PurchaseRequest r " +
       "LEFT JOIN FETCH r.requester " +
       "LEFT JOIN FETCH r.approver " +
       "WHERE r.status = :status")
Page<PurchaseRequest> findByStatusWithUsers(
    @Param("status") RequestStatus status, 
    Pageable pageable
);
```

### 12.2 インデックス設計

```sql
CREATE INDEX idx_status ON purchase_requests(status);
CREATE INDEX idx_requester_id ON purchase_requests(requester_id);
CREATE INDEX idx_created_at ON purchase_requests(created_at);
CREATE INDEX idx_status_requester ON purchase_requests(status, requester_id);
```

## 13. テスト観点

### 13.1 ユニットテスト

- ステータス遷移ロジック
- 高額申請判定
- バリデーション
- 権限チェック

### 13.2 統合テスト

- API エンドポイント
- トランザクション境界
- 例外ハンドリング

### 13.3 E2Eテスト

- 申請登録 → 提出 → 承認フロー
- 申請登録 → 提出 → 却下フロー

---

**作成日**: 2026-04-02  
**作成者**: AI（Plan Mode）  
**バージョン**: 1.0  
**参照仕様**: [`spec.md`](spec.md) v1.0