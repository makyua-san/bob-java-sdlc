# 下書き削除機能 アプリケーション設計書

## 1. 設計概要

### 1.1 設計目的

下書き削除API機能のアプリケーション層の詳細設計を定義する。ドメインモデル、API設計、レイヤー設計、バリデーション設計を含む。

### 1.2 設計原則

- **仕様駆動**: [`spec.md`](spec.md) の要件を完全に満たす
- **既存設計との整合性**: [`design-app.md`](../../../design-app.md) との一貫性を保つ
- **ドメイン駆動**: ビジネスルールはエンティティに集約
- **最小限の変更**: 既存機能への影響を最小化

### 1.3 参照ドキュメント

- [`spec.md`](spec.md): 下書き削除機能仕様
- [`design-app.md`](../../../design-app.md): 既存アプリケーション設計
- [`Agents.md`](../../../Agents.md): プロジェクト開発ルール

---

## 2. ドメインモデル変更

### 2.1 PurchaseRequest エンティティの拡張

既存の [`PurchaseRequest`](../../../src/main/java/com/example/purchaserequest/model/entity/PurchaseRequest.java) エンティティに論理削除フィールドを追加：

```java
@Entity
@Table(name = "purchase_requests", indexes = {
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_requester_id", columnList = "requester_id"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_deleted", columnList = "deleted")  // 新規追加
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PurchaseRequest {
    
    // 既存フィールド...
    
    /**
     * 削除フラグ（論理削除）
     * デフォルト: false（未削除）
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;
    
    /**
     * 削除日時
     * 削除時に設定される
     */
    @Column
    private LocalDateTime deletedAt;
    
    /**
     * 削除者のユーザー名
     * 削除時に設定される
     */
    @Column(length = 50)
    private String deletedBy;
    
    // 既存メソッド...
    
    /**
     * 申請を論理削除
     * 
     * @param username 削除者のユーザー名
     * @throws IllegalStateException ステータスがDRAFT以外、または既に削除済みの場合
     */
    public void delete(String username) {
        if (status != RequestStatus.DRAFT) {
            throw new IllegalStateException("下書き状態の申請のみ削除できます");
        }
        if (deleted) {
            throw new IllegalStateException("この申請は既に削除されています");
        }
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = username;
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = username;
    }
    
    /**
     * 削除済みかどうかを判定
     * 
     * @return 削除済みの場合true
     */
    public boolean isDeleted() {
        return deleted != null && deleted;
    }
}
```

### 2.2 エンティティ設計の考慮事項

| 項目 | 設計判断 | 理由 |
|------|---------|------|
| 削除フラグの型 | `Boolean`（nullable不可） | デフォルト値`false`で明示的に未削除を表現 |
| 削除日時の型 | `LocalDateTime`（nullable） | 削除時のみ設定、タイムゾーン情報は不要 |
| 削除者の型 | `String`（nullable） | ユーザー名を記録、外部キーは不要 |
| インデックス | `deleted`カラムに追加 | 一覧取得時のフィルタリング性能向上 |
| ビジネスロジック配置 | エンティティ内 | ドメイン駆動設計の原則に従う |

### 2.3 データベースマイグレーション

**ファイル名**: `V5__add_deleted_flag_to_purchase_requests.sql`

**配置場所**: `src/main/resources/db/migration/`

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

---

## 3. API設計

### 3.1 削除APIエンドポイント

**エンドポイント**: `DELETE /api/v1/requests/{id}`

**パスパラメータ**:
- `id`: 削除する申請のID（Long型、必須）

**リクエストヘッダー**:
- `Authorization`: Bearer トークン（認証必須）

**リクエストボディ**: なし

**レスポンス（成功: 200 OK）**:
```json
{
  "status": "success",
  "data": {
    "id": 1,
    "deleted": true,
    "deletedAt": "2026-04-03T09:15:00Z"
  },
  "message": "下書きを削除しました",
  "timestamp": "2026-04-03T09:15:00Z"
}
```

**レスポンス（エラー）**:

| HTTPステータス | エラーコード | メッセージ | 発生条件 |
|--------------|------------|-----------|---------|
| 404 Not Found | REQUEST_NOT_FOUND | "申請が見つかりません" | 申請IDが存在しない、または削除済み |
| 403 Forbidden | FORBIDDEN | "自分の申請のみ削除できます" | 申請者本人でない |
| 400 Bad Request | INVALID_STATUS | "下書き状態の申請のみ削除できます" | ステータスがDRAFT以外 |
| 400 Bad Request | ALREADY_DELETED | "この申請は既に削除されています" | 既に削除済み |

### 3.2 DTOクラス

**DeletedPurchaseRequestDto（新規作成）**:

**ファイルパス**: `src/main/java/com/example/purchaserequest/model/dto/DeletedPurchaseRequestDto.java`

```java
package com.example.purchaserequest.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 削除された申請のレスポンスDTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeletedPurchaseRequestDto {
    
    /**
     * 申請ID
     */
    private Long id;
    
    /**
     * 削除フラグ（常にtrue）
     */
    private Boolean deleted;
    
    /**
     * 削除日時
     */
    private LocalDateTime deletedAt;
}
```

---

## 4. レイヤー設計

### 4.1 Controller層

**PurchaseRequestController への追加**:

**ファイルパス**: `src/main/java/com/example/purchaserequest/controller/PurchaseRequestController.java`

```java
/**
 * 下書き申請を削除（論理削除）
 * 
 * @param id 削除する申請のID
 * @param authentication 認証情報
 * @return 削除結果
 */
@DeleteMapping("/{id}")
@Operation(summary = "下書き申請を削除", description = "下書き状態の申請を論理削除します。申請者本人のみ実行可能です。")
@ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "削除成功"),
    @ApiResponse(responseCode = "400", description = "不正なリクエスト（ステータスがDRAFT以外、または既に削除済み）"),
    @ApiResponse(responseCode = "403", description = "権限不足（申請者本人でない）"),
    @ApiResponse(responseCode = "404", description = "申請が見つかりません")
})
public ResponseEntity<ApiResponse<DeletedPurchaseRequestDto>> deleteDraftRequest(
        @PathVariable @Min(1) Long id,
        Authentication authentication) {
    
    String username = authentication.getName();
    
    // ログ出力: 削除リクエスト受信
    BusinessLogger.logRequestReceived("delete_draft", id, username);
    
    long startTime = System.currentTimeMillis();
    try {
        DeletedPurchaseRequestDto result = purchaseRequestService.deleteDraftRequest(id, username);
        
        // ログ出力: 削除成功
        BusinessLogger.logRequestDeleted(id, username);
        
        // メトリクス記録
        long executionTime = System.currentTimeMillis() - startTime;
        BusinessMetrics.recordDeleteExecutionTime(executionTime);
        BusinessMetrics.incrementRequestDeleted();
        
        return ResponseEntity.ok(ApiResponse.success(result, "下書きを削除しました"));
    } catch (Exception e) {
        long executionTime = System.currentTimeMillis() - startTime;
        BusinessMetrics.recordDeleteExecutionTime(executionTime);
        throw e;
    }
}
```

### 4.2 Service層

**PurchaseRequestService インターフェースへの追加**:

**ファイルパス**: `src/main/java/com/example/purchaserequest/service/PurchaseRequestService.java`

```java
/**
 * 下書き申請を削除（論理削除）
 * 
 * @param id 削除する申請のID
 * @param username 削除者のユーザー名
 * @return 削除結果
 * @throws RequestNotFoundException 申請が見つからない場合
 * @throws UnauthorizedOperationException 申請者本人でない場合
 * @throws InvalidStatusTransitionException ステータスがDRAFT以外の場合
 * @throws IllegalStateException 既に削除済みの場合
 */
DeletedPurchaseRequestDto deleteDraftRequest(Long id, String username);
```

**PurchaseRequestServiceImpl への実装**:

**ファイルパス**: `src/main/java/com/example/purchaserequest/service/PurchaseRequestServiceImpl.java`

```java
@Override
@Transactional
public DeletedPurchaseRequestDto deleteDraftRequest(Long id, String username) {
    
    // 1. 申請を取得（削除済みを含む）
    PurchaseRequest request = purchaseRequestRepository.findById(id)
        .orElseThrow(() -> {
            BusinessLogger.logRequestNotFound(id, username);
            return new RequestNotFoundException("申請が見つかりません");
        });
    
    // 2. 削除済みチェック
    if (request.isDeleted()) {
        BusinessLogger.logAlreadyDeleted(id, username);
        throw new IllegalStateException("この申請は既に削除されています");
    }
    
    // 3. 申請者本人チェック
    User currentUser = userRepository.findByUsername(username)
        .orElseThrow(() -> new UnauthorizedOperationException("ユーザーが見つかりません"));
    
    if (!request.getRequesterId().equals(currentUser.getId())) {
        BusinessLogger.logUnauthorizedDelete(id, username, request.getRequesterId());
        throw new UnauthorizedOperationException("自分の申請のみ削除できます");
    }
    
    // 4. ステータスチェック（エンティティ内で実施）
    try {
        request.delete(username);
    } catch (IllegalStateException e) {
        BusinessLogger.logInvalidStatusForDelete(id, username, request.getStatus());
        throw new InvalidStatusTransitionException(e.getMessage());
    }
    
    // 5. 保存
    PurchaseRequest deletedRequest = purchaseRequestRepository.save(request);
    
    // 6. DTOに変換して返却
    return DeletedPurchaseRequestDto.builder()
        .id(deletedRequest.getId())
        .deleted(deletedRequest.getDeleted())
        .deletedAt(deletedRequest.getDeletedAt())
        .build();
}
```

### 4.3 Repository層

**PurchaseRequestRepository への追加**:

**ファイルパス**: `src/main/java/com/example/purchaserequest/repository/PurchaseRequestRepository.java`

```java
/**
 * 削除されていない申請を申請者IDで検索
 * 
 * @param requesterId 申請者ID
 * @param pageable ページング情報
 * @return 削除されていない申請のページ
 */
Page<PurchaseRequest> findByRequesterIdAndDeletedFalse(Long requesterId, Pageable pageable);

/**
 * 削除されていない申請をステータスで検索
 * 
 * @param status ステータス
 * @param pageable ページング情報
 * @return 削除されていない申請のページ
 */
Page<PurchaseRequest> findByStatusAndDeletedFalse(RequestStatus status, Pageable pageable);

/**
 * 削除されていない申請を申請者IDとステータスで検索
 * 
 * @param requesterId 申請者ID
 * @param status ステータス
 * @param pageable ページング情報
 * @return 削除されていない申請のページ
 */
Page<PurchaseRequest> findByRequesterIdAndStatusAndDeletedFalse(
    Long requesterId,
    RequestStatus status,
    Pageable pageable
);

/**
 * 削除されていない申請を申請者IDとIDで検索
 * 
 * @param id 申請ID
 * @param requesterId 申請者ID
 * @return 削除されていない申請
 */
Optional<PurchaseRequest> findByIdAndRequesterIdAndDeletedFalse(Long id, Long requesterId);

/**
 * 削除されていない申請をIDで検索
 * 
 * @param id 申請ID
 * @return 削除されていない申請
 */
Optional<PurchaseRequest> findByIdAndDeletedFalse(Long id);
```

---

## 5. 例外設計

既存の例外クラスを活用し、新規例外は不要：

| 例外クラス | 使用箇所 | HTTPステータス | ファイルパス |
|-----------|---------|--------------|------------|
| `RequestNotFoundException` | 申請が存在しない | 404 | [`RequestNotFoundException.java`](../../../src/main/java/com/example/purchaserequest/exception/RequestNotFoundException.java) |
| `UnauthorizedOperationException` | 申請者本人でない | 403 | [`UnauthorizedOperationException.java`](../../../src/main/java/com/example/purchaserequest/exception/UnauthorizedOperationException.java) |
| `InvalidStatusTransitionException` | ステータスがDRAFT以外 | 400 | [`InvalidStatusTransitionException.java`](../../../src/main/java/com/example/purchaserequest/exception/InvalidStatusTransitionException.java) |
| `IllegalStateException` | 既に削除済み | 400 | Java標準例外 |

**GlobalExceptionHandler での処理**:

既存の [`GlobalExceptionHandler`](../../../src/main/java/com/example/purchaserequest/controller/GlobalExceptionHandler.java) で `IllegalStateException` のハンドリングを追加：

```java
/**
 * IllegalStateException のハンドリング
 */
@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(IllegalStateException e) {
    ErrorResponse error = ErrorResponse.builder()
        .code("ALREADY_DELETED")
        .message(e.getMessage())
        .details(Collections.emptyList())
        .build();
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(error));
}
```

---

## 6. バリデーション設計

### 6.1 削除前バリデーション

| 検証項目 | 検証内容 | エラー時の処理 | 実装場所 |
|---------|---------|--------------|---------|
| 申請の存在 | IDで申請を取得できるか | `RequestNotFoundException` をスロー | Service層 |
| 削除済みチェック | `deleted = true` でないか | `IllegalStateException` をスロー | Service層 |
| 申請者本人チェック | `requesterId` が現在のユーザーIDと一致するか | `UnauthorizedOperationException` をスロー | Service層 |
| ステータスチェック | `status = DRAFT` であるか | `InvalidStatusTransitionException` をスロー | Entity層 |

### 6.2 バリデーション順序

1. **申請の存在確認** → 404エラー
2. **削除済みチェック** → 400エラー
3. **申請者本人チェック** → 403エラー（セキュリティ重要）
4. **ステータスチェック** → 400エラー（エンティティ内）

この順序により、セキュリティ上重要な検証（本人確認）を先に実施し、不正アクセスを早期に検出する。

### 6.3 入力検証

**パスパラメータ検証**:

```java
@DeleteMapping("/{id}")
public ResponseEntity<ApiResponse<DeletedPurchaseRequestDto>> deleteDraftRequest(
        @PathVariable @Min(1) Long id,  // 正の整数のみ許可
        Authentication authentication) {
    // ...
}
```

---

## 7. 既存機能への影響と対応

### 7.1 申請一覧取得の変更

**変更対象メソッド**:
- `PurchaseRequestServiceImpl.getMyRequests()`
- `PurchaseRequestServiceImpl.getAllRequests()`

**変更前**:
```java
Page<PurchaseRequest> findByRequesterId(Long requesterId, Pageable pageable);
```

**変更後**:
```java
Page<PurchaseRequest> findByRequesterIdAndDeletedFalse(Long requesterId, Pageable pageable);
```

**影響範囲**: 削除済み申請が一覧に表示されなくなる（期待される動作）

### 7.2 申請詳細取得の変更

**変更対象メソッド**:
- `PurchaseRequestServiceImpl.getRequestById()`
- `PurchaseRequestServiceImpl.submitRequest()`
- `PurchaseRequestServiceImpl.approveRequest()`
- `PurchaseRequestServiceImpl.rejectRequest()`

**変更前**:
```java
Optional<PurchaseRequest> findById(Long id);
```

**変更後**:
```java
Optional<PurchaseRequest> findByIdAndDeletedFalse(Long id);
```

**影響範囲**: 削除済み申請へのアクセスは404エラーとして扱われる（期待される動作）

### 7.3 ステータス遷移への影響

削除は独立した操作であり、既存のステータス遷移には影響しない：

```
DRAFT → SUBMITTED → APPROVED
              ↓
           REJECTED

削除: DRAFT のみ（論理削除フラグを立てる）
```

### 7.4 変更影響マトリクス

| 既存機能 | 影響の有無 | 変更内容 | テスト必要性 |
|---------|-----------|---------|------------|
| 申請登録 | なし | - | 回帰テスト |
| 申請一覧取得 | あり | 削除済み除外 | 必須 |
| 申請詳細取得 | あり | 削除済み404 | 必須 |
| 申請提出 | あり | 削除済み404 | 必須 |
| 申請承認 | あり | 削除済み404 | 必須 |
| 申請却下 | あり | 削除済み404 | 必須 |

---

## 8. セキュリティ設計

### 8.1 認証・認可

#### 8.1.1 認証要件

- **認証方式**: Spring Security + JWT（既存）
- **必須**: すべての削除操作で認証必須
- **トークン検証**: 有効期限、署名検証

#### 8.1.2 認可要件

| 操作 | 必要な権限 | 追加条件 |
|------|-----------|---------|
| 削除 | ROLE_USER | 申請者本人のみ |

**認可チェックロジック**:
```java
// 1. 認証済みユーザーの取得
String username = authentication.getName();
User currentUser = userRepository.findByUsername(username)
    .orElseThrow(() -> new UnauthorizedOperationException("ユーザーが見つかりません"));

// 2. 申請者本人チェック
if (!request.getRequesterId().equals(currentUser.getId())) {
    throw new UnauthorizedOperationException("自分の申請のみ削除できます");
}
```

### 8.2 監査証跡

削除操作の監査証跡は以下で確保：

1. **データベース**: `deleted`, `deletedAt`, `deletedBy` カラム
2. **構造化ログ**: CloudWatch Logs（90日保持）
3. **メトリクス**: CloudWatch Metrics（15ヶ月保持）

---

## 9. パフォーマンス設計

### 9.1 性能要件

| 操作 | 目標レスポンスタイム | 根拠 |
|------|-------------------|------|
| 削除API | 1秒以内 | 仕様書の非機能要件 |
| 削除後の一覧取得 | 1秒以内 | 既存の一覧取得と同等 |

### 9.2 インデックス戦略

**追加インデックス**:
```sql
CREATE INDEX idx_deleted ON purchase_requests(deleted);
```

**効果**:
- 削除済み除外フィルタ（`WHERE deleted = FALSE`）の高速化
- 一覧取得クエリの性能向上

**インデックス選択性**:
- 削除率が低い（< 10%）場合、インデックスは効果的
- 削除率が高い場合でも、複合インデックスで対応可能

### 9.3 クエリ最適化

**最適化前**:
```java
// 削除チェックなし
Page<PurchaseRequest> findByRequesterId(Long requesterId, Pageable pageable);
```

**最適化後**:
```java
// 削除済み除外
Page<PurchaseRequest> findByRequesterIdAndDeletedFalse(Long requesterId, Pageable pageable);
```

**実行計画**:
```sql
EXPLAIN ANALYZE
SELECT * FROM purchase_requests
WHERE requester_id = 1 AND deleted = FALSE
ORDER BY created_at DESC
LIMIT 20;

-- 期待される実行計画:
-- Index Scan using idx_requester_id on purchase_requests
-- Filter: (deleted = false)
```

---

## 10. 実装チェックリスト

### 10.1 エンティティ層

- [ ] `PurchaseRequest` に削除フラグフィールド追加
- [ ] `delete()` メソッド実装
- [ ] `isDeleted()` メソッド実装
- [ ] インデックスアノテーション追加

### 10.2 Repository層

- [ ] `findByRequesterIdAndDeletedFalse()` 追加
- [ ] `findByStatusAndDeletedFalse()` 追加
- [ ] `findByRequesterIdAndStatusAndDeletedFalse()` 追加
- [ ] `findByIdAndRequesterIdAndDeletedFalse()` 追加
- [ ] `findByIdAndDeletedFalse()` 追加

### 10.3 Service層

- [ ] `deleteDraftRequest()` インターフェース定義
- [ ] `deleteDraftRequest()` 実装
- [ ] 既存メソッドのリポジトリ呼び出し変更

### 10.4 Controller層

- [ ] `deleteDraftRequest()` エンドポイント追加
- [ ] OpenAPI アノテーション追加
- [ ] ログ出力追加
- [ ] メトリクス記録追加

### 10.5 DTO層

- [ ] `DeletedPurchaseRequestDto` 作成

### 10.6 例外処理

- [ ] `GlobalExceptionHandler` に `IllegalStateException` ハンドリング追加

### 10.7 データベース

- [ ] マイグレーションファイル作成（V5）
- [ ] マイグレーション実行確認

---

**作成日**: 2026-04-03  
**作成者**: AI（Plan Mode）  
**バージョン**: 1.0  
**関連ドキュメント**: [`spec.md`](spec.md), [`design-deployment.md`](design-deployment.md), [`design-observability.md`](design-observability.md)