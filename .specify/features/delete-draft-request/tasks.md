# 下書き削除機能 実装タスクリスト

## 📋 概要

このドキュメントは、下書き削除機能の実装タスクを定義します。各タスクはユーザーストーリー別に編成され、依存関係と並列実行の機会を明示しています。

## 🎯 ユーザーストーリー

### US-1: 申請者が下書き申請を削除できる
**受け入れ条件**: [`spec.md`](spec.md#5-受け入れ条件) 参照

### US-2: 削除済み申請が一覧に表示されない
**受け入れ条件**: [`spec.md`](spec.md#5-受け入れ条件) 参照

### US-3: 削除操作が監査ログに記録される
**受け入れ条件**: [`spec.md`](spec.md#5-受け入れ条件) 参照

---

## 📝 タスクリスト

### Phase 1: データベース層（優先度: P0）

- [ ] [T001] [P0] [US-1] データベースマイグレーションファイル作成 [`V5__add_deleted_flag_to_purchase_requests.sql`](../../../src/main/resources/db/migration/)
  - 削除フラグカラム追加（deleted, deleted_at, deleted_by）
  - インデックス追加（idx_deleted）
  - コメント追加
  - 参照: [`design-app.md#2.3`](design-app.md#23-データベースマイグレーション)

- [ ] [T002] [P0] [US-1] ロールバックSQL作成 [`V5_rollback.sql`](../../../src/main/resources/db/migration/)
  - インデックス削除
  - カラム削除
  - 参照: [`design-deployment.md#2.1.1`](design-deployment.md#211-phase-1-データベースマイグレーション)

### Phase 2: エンティティ層（優先度: P0、依存: T001完了後）

- [ ] [T003] [P0] [US-1] [`PurchaseRequest`](../../../src/main/java/com/example/purchaserequest/model/entity/PurchaseRequest.java) エンティティ拡張
  - 削除フラグフィールド追加（deleted, deletedAt, deletedBy）
  - インデックスアノテーション追加
  - 参照: [`design-app.md#2.1`](design-app.md#21-purchaserequest-エンティティの拡張)

- [ ] [T004] [P0] [US-1] [`PurchaseRequest`](../../../src/main/java/com/example/purchaserequest/model/entity/PurchaseRequest.java) に `delete()` メソッド実装
  - ステータスチェック（DRAFT以外は例外）
  - 削除済みチェック（既に削除済みは例外）
  - 削除フラグ設定
  - 参照: [`design-app.md#2.1`](design-app.md#21-purchaserequest-エンティティの拡張)

- [ ] [T005] [P0] [US-1] [`PurchaseRequest`](../../../src/main/java/com/example/purchaserequest/model/entity/PurchaseRequest.java) に `isDeleted()` メソッド実装
  - 削除済み判定ロジック
  - 参照: [`design-app.md#2.1`](design-app.md#21-purchaserequest-エンティティの拡張)

### Phase 3: DTO層（優先度: P0、並列実行可能）

- [ ] [T006] [P0] [US-1] [`DeletedPurchaseRequestDto`](../../../src/main/java/com/example/purchaserequest/model/dto/DeletedPurchaseRequestDto.java) 作成
  - id, deleted, deletedAt フィールド定義
  - Lombokアノテーション追加
  - 参照: [`design-app.md#3.2`](design-app.md#32-dtoクラス)

### Phase 4: Repository層（優先度: P0、依存: T003完了後）

- [ ] [T007] [P0] [US-2] [`PurchaseRequestRepository`](../../../src/main/java/com/example/purchaserequest/repository/PurchaseRequestRepository.java) にクエリメソッド追加
  - `findByRequesterIdAndDeletedFalse()`
  - `findByStatusAndDeletedFalse()`
  - `findByRequesterIdAndStatusAndDeletedFalse()`
  - `findByIdAndRequesterIdAndDeletedFalse()`
  - `findByIdAndDeletedFalse()`
  - 参照: [`design-app.md#4.3`](design-app.md#43-repository層)

### Phase 5: ユーティリティ層（優先度: P0、並列実行可能）

- [ ] [T008] [P0] [US-3] [`BusinessLogger`](../../../src/main/java/com/example/purchaserequest/util/BusinessLogger.java) にログメソッド追加
  - `logRequestReceived()` - 削除リクエスト受信
  - `logRequestDeleted()` - 削除成功
  - `logRequestNotFound()` - 申請が見つからない
  - `logAlreadyDeleted()` - 既に削除済み
  - `logUnauthorizedDelete()` - 権限不足
  - `logInvalidStatusForDelete()` - 不正なステータス
  - 参照: [`design-observability.md#2.3.1`](design-observability.md#231-businesslogger-への追加メソッド)

- [ ] [T009] [P0] [US-3] [`BusinessMetrics`](../../../src/main/java/com/example/purchaserequest/util/BusinessMetrics.java) にメトリクスメソッド追加
  - `incrementRequestDeleted()` - 削除カウンター
  - `incrementDeleteFailed()` - 削除失敗カウンター
  - `recordDeleteExecutionTime()` - 実行時間記録
  - `incrementDeletedByStatus()` - ステータス別削除数
  - 参照: [`design-observability.md#3.1.1`](design-observability.md#311-businessmetrics-への追加メソッド)

### Phase 6: 例外処理層（優先度: P0、並列実行可能）

- [ ] [T010] [P0] [US-1] [`GlobalExceptionHandler`](../../../src/main/java/com/example/purchaserequest/controller/GlobalExceptionHandler.java) に `IllegalStateException` ハンドリング追加
  - エラーコード: ALREADY_DELETED
  - HTTPステータス: 400 Bad Request
  - 参照: [`design-app.md#5`](design-app.md#5-例外設計)

### Phase 7: Service層（優先度: P0、依存: T004, T007, T008完了後）

- [ ] [T011] [P0] [US-1] [`PurchaseRequestService`](../../../src/main/java/com/example/purchaserequest/service/PurchaseRequestService.java) インターフェースに `deleteDraftRequest()` 追加
  - メソッドシグネチャ定義
  - Javadoc追加
  - 参照: [`design-app.md#4.2`](design-app.md#42-service層)

- [ ] [T012] [P0] [US-1] [`PurchaseRequestServiceImpl`](../../../src/main/java/com/example/purchaserequest/service/PurchaseRequestServiceImpl.java) に `deleteDraftRequest()` 実装
  - 申請取得（削除済み含む）
  - 削除済みチェック
  - 申請者本人チェック
  - エンティティの `delete()` 呼び出し
  - 保存
  - DTOに変換して返却
  - 参照: [`design-app.md#4.2`](design-app.md#42-service層)

- [ ] [T013] [P1] [US-2] [`PurchaseRequestServiceImpl`](../../../src/main/java/com/example/purchaserequest/service/PurchaseRequestServiceImpl.java) の既存メソッド修正
  - `getMyRequests()` - リポジトリ呼び出しを `findByRequesterIdAndDeletedFalse()` に変更
  - `getAllRequests()` - リポジトリ呼び出しを削除フラグフィルタ付きに変更
  - `getRequestById()` - リポジトリ呼び出しを `findByIdAndDeletedFalse()` に変更
  - `submitRequest()` - リポジトリ呼び出しを `findByIdAndDeletedFalse()` に変更
  - `approveRequest()` - リポジトリ呼び出しを `findByIdAndDeletedFalse()` に変更
  - `rejectRequest()` - リポジトリ呼び出しを `findByIdAndDeletedFalse()` に変更
  - 参照: [`design-app.md#7.1`](design-app.md#71-申請一覧取得の変更)

### Phase 8: Controller層（優先度: P0、依存: T011, T012完了後）

- [ ] [T014] [P0] [US-1] [`PurchaseRequestController`](../../../src/main/java/com/example/purchaserequest/controller/PurchaseRequestController.java) に `deleteDraftRequest()` エンドポイント追加
  - `DELETE /api/v1/requests/{id}` エンドポイント実装
  - パスパラメータバリデーション（@Min(1)）
  - 認証情報取得
  - ログ出力（リクエスト受信、削除成功）
  - メトリクス記録（実行時間、削除カウント）
  - OpenAPIアノテーション追加
  - 参照: [`design-app.md#4.1`](design-app.md#41-controller層)

### Phase 9: テスト層（優先度: P0、依存: 実装完了後）

#### 単体テスト

- [ ] [T015] [P0] [US-1] [`PurchaseRequestTest`](../../../src/test/java/com/example/purchaserequest/model/entity/PurchaseRequestTest.java) に削除メソッドのテスト追加
  - 正常系: 下書き状態の申請を削除できる
  - 異常系: ステータスがDRAFT以外の場合に例外
  - 異常系: 既に削除済みの場合に例外
  - 境界値: 削除フラグ、削除日時、削除者が正しく設定される

- [ ] [T016] [P0] [US-1] [`PurchaseRequestServiceImplTest`](../../../src/test/java/com/example/purchaserequest/service/PurchaseRequestServiceImplTest.java) に削除メソッドのテスト追加
  - 正常系: 下書き状態の申請を削除できる
  - 異常系: 申請が存在しない場合に `RequestNotFoundException`
  - 異常系: 削除済みの場合に `IllegalStateException`
  - 異常系: 申請者本人でない場合に `UnauthorizedOperationException`
  - 異常系: ステータスがDRAFT以外の場合に `InvalidStatusTransitionException`

- [ ] [T017] [P0] [US-1] [`PurchaseRequestControllerTest`](../../../src/test/java/com/example/purchaserequest/controller/PurchaseRequestControllerTest.java) に削除エンドポイントのテスト追加
  - 正常系: 200 OK、削除成功レスポンス
  - 異常系: 404 Not Found（申請が存在しない）
  - 異常系: 403 Forbidden（申請者本人でない）
  - 異常系: 400 Bad Request（ステータスがDRAFT以外）
  - 異常系: 400 Bad Request（既に削除済み）

#### 統合テスト

- [ ] [T018] [P0] [US-1, US-2] [`PurchaseRequestApiIntegrationTest`](../../../src/test/java/com/example/purchaserequest/integration/PurchaseRequestApiIntegrationTest.java) に削除機能の統合テスト追加
  - シナリオ1: 下書き作成 → 削除 → 一覧取得（削除済みが表示されない）
  - シナリオ2: 下書き作成 → 削除 → 詳細取得（404エラー）
  - シナリオ3: 下書き作成 → 提出 → 削除（400エラー）
  - シナリオ4: 他人の下書き削除（403エラー）

### Phase 10: インフラ層（優先度: P1、並列実行可能）

- [ ] [T019] [P1] [US-3] CloudWatchダッシュボード作成 [`infra/modules/monitoring/`](../../../infra/modules/monitoring/)
  - 削除操作数ウィジェット
  - 削除失敗数ウィジェット
  - 削除実行時間ウィジェット
  - 削除成功率ウィジェット
  - 参照: [`design-observability.md#3.3`](design-observability.md#33-メトリクスダッシュボード)

- [ ] [T020] [P1] [US-3] CloudWatchアラート設定 [`infra/modules/monitoring/`](../../../infra/modules/monitoring/)
  - 削除失敗率上昇アラート
  - 削除操作遅延アラート
  - 権限不足エラー急増アラート
  - SNSトピック設定
  - 参照: [`design-observability.md#4.1`](design-observability.md#41-アラート定義)

### Phase 11: ドキュメント層（優先度: P2、並列実行可能）

- [ ] [T021] [P2] OpenAPI仕様更新
  - 削除エンドポイントの仕様追加
  - リクエスト/レスポンス例追加
  - エラーレスポンス例追加

- [ ] [T022] [P2] README更新
  - 削除機能の説明追加
  - API使用例追加

---

## 🔗 依存関係グラフ

```
T001 (DBマイグレーション)
  ↓
T003 (エンティティ拡張) ─┬→ T004 (delete()メソッド) ─┐
  ↓                    │                           │
T007 (Repositoryメソッド)│                           │
  ↓                    │                           │
T012 (Service実装) ←────┴───────────────────────────┘
  ↓                    ↑
T014 (Controller実装) ←─┤
  ↓                    │
T015-T018 (テスト) ←────┘

並列実行可能:
- T002 (ロールバックSQL)
- T006 (DTO作成)
- T008 (BusinessLogger)
- T009 (BusinessMetrics)
- T010 (例外処理)
- T019-T020 (インフラ)
- T021-T022 (ドキュメント)
```

---

## 📊 進捗管理

### 完了基準

各タスクは以下の基準を満たした場合に完了とする：

1. **実装完了**: コードが実装され、コンパイルエラーがない
2. **テスト完了**: 単体テストが実装され、すべて成功
3. **レビュー完了**: コードレビューが完了し、承認された
4. **ドキュメント更新**: 必要に応じてドキュメントが更新された

### 見積もり

| Phase | タスク数 | 見積もり工数 | 依存関係 |
|-------|---------|------------|---------|
| Phase 1: データベース層 | 2 | 1時間 | なし |
| Phase 2: エンティティ層 | 3 | 2時間 | Phase 1 |
| Phase 3: DTO層 | 1 | 0.5時間 | なし |
| Phase 4: Repository層 | 1 | 1時間 | Phase 2 |
| Phase 5: ユーティリティ層 | 2 | 2時間 | なし |
| Phase 6: 例外処理層 | 1 | 0.5時間 | なし |
| Phase 7: Service層 | 3 | 3時間 | Phase 2, 4, 5 |
| Phase 8: Controller層 | 1 | 1時間 | Phase 7 |
| Phase 9: テスト層 | 4 | 4時間 | Phase 8 |
| Phase 10: インフラ層 | 2 | 2時間 | なし |
| Phase 11: ドキュメント層 | 2 | 1時間 | なし |
| **合計** | **22** | **18時間** | - |

---

## 🎯 実装戦略

### 推奨実装順序

1. **Phase 1-2**: データベースとエンティティ層（基盤）
2. **Phase 3-6**: DTO、Repository、ユーティリティ、例外処理（並列実行）
3. **Phase 7-8**: Service、Controller層（ビジネスロジック）
4. **Phase 9**: テスト層（品質保証）
5. **Phase 10-11**: インフラ、ドキュメント（運用準備）

### 並列実行の機会

以下のタスクは並列実行可能：

- **グループ1**: T002, T006, T008, T009, T010（Phase 1完了後）
- **グループ2**: T019, T020, T021, T022（実装完了後）

---

## 📝 注意事項

### 実装時の注意点

1. **既存機能への影響**: T013（既存メソッド修正）は慎重に実施し、回帰テストを必ず実行
2. **データベースマイグレーション**: T001は本番環境で実行前に必ずステージング環境で検証
3. **ログ出力**: T008は個人情報をログに出力しないよう注意
4. **メトリクス**: T009はパフォーマンスへの影響を最小化
5. **テスト**: T015-T018はカバレッジ80%以上を目標

### レビュー観点

- **第1層（開発者ローカル）**: 仕様・設計との整合性、命名規約、責務分離
- **第3層（PR）**: 既存機能への影響、テストカバレッジ、ロジックの重複

---

## 🔄 更新履歴

| 日付 | バージョン | 変更内容 | 作成者 |
|------|----------|---------|--------|
| 2026-04-03 | 1.0 | 初版作成 | AI（Tasks Mode） |

---

**作成日**: 2026-04-03  
**作成者**: AI（Tasks Mode）  
**バージョン**: 1.0  
**関連ドキュメント**: [`spec.md`](spec.md), [`design-app.md`](design-app.md), [`design-deployment.md`](design-deployment.md), [`design-observability.md`](design-observability.md)