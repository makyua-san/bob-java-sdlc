# 下書き削除機能 設計ドキュメント

## 📋 概要

このディレクトリには、下書き削除API機能の設計ドキュメントが含まれています。設計は3つの観点（アプリケーション・配備・観測）に分割されており、それぞれ独立して参照・更新できます。

## 📚 ドキュメント構成

### 1. 仕様書
- **[`spec.md`](spec.md)** - 機能仕様書
  - 機能要件、非機能要件、受け入れ条件を定義
  - すべての設計の基礎となるドキュメント

### 2. 設計書（3観点）

#### 2.1 アプリケーション設計
- **[`design-app.md`](design-app.md)** - アプリケーション設計書
  - ドメインモデル変更（PurchaseRequestエンティティ拡張）
  - API設計（DELETE /api/v1/requests/{id}）
  - レイヤー設計（Controller / Service / Repository）
  - 例外設計、バリデーション設計
  - 既存機能への影響分析
  - セキュリティ設計、パフォーマンス設計

#### 2.2 配備設計
- **[`design-deployment.md`](design-deployment.md)** - 配備設計書
  - デプロイメント戦略（ブルーグリーンデプロイ）
  - データベースマイグレーション（V5）
  - 環境設定（dev / stg / prod）
  - リソース要件
  - CI/CDパイプライン
  - ロールバック手順

#### 2.3 観測設計
- **[`design-observability.md`](design-observability.md)** - 観測設計書
  - ログ設計（構造化ログ、ログレベル）
  - メトリクス設計（Counter / Timer）
  - アラート設計（削除失敗率、実行時間、権限エラー）
  - トラブルシューティング手順
  - 監査ログ、運用手順書

### 3. 品質チェックリスト
- **[`quality-checklist.md`](quality-checklist.md)** - 品質チェックリスト
  - 仕様の品質確認項目

## 🔄 設計の読み方

### 初めて読む場合
1. **[`spec.md`](spec.md)** で機能要件を理解
2. **[`design-app.md`](design-app.md)** でアプリケーション構造を理解
3. **[`design-deployment.md`](design-deployment.md)** でデプロイ方法を理解
4. **[`design-observability.md`](design-observability.md)** で監視方法を理解

### 実装時
- **開発者**: [`design-app.md`](design-app.md) を中心に参照
- **インフラ担当**: [`design-deployment.md`](design-deployment.md) を中心に参照
- **運用担当**: [`design-observability.md`](design-observability.md) を中心に参照

### レビュー時
- **PO**: [`spec.md`](spec.md) で要件を確認
- **開発者**: [`design-app.md`](design-app.md) でコード設計を確認
- **QA**: すべてのドキュメントでテスト観点を確認

## 🎯 設計の特徴

### 論理削除の採用
- 物理削除ではなく論理削除（削除フラグ）を採用
- データの復元可能性と監査証跡を確保
- 既存データへの影響を最小化

### 既存設計との整合性
- 既存の設計書（[`design-app.md`](../../../design-app.md)、[`design-deployment.md`](../../../design-deployment.md)、[`design-observability.md`](../../../design-observability.md)）との一貫性を保つ
- 既存のアーキテクチャパターンを踏襲
- 既存の例外クラス、ユーティリティクラスを活用

### ゼロダウンタイムデプロイ
- ブルーグリーンデプロイメントによる無停止デプロイ
- データベースマイグレーションの自動実行
- ロールバック手順の明確化

## 📊 主要な設計判断

| 項目 | 設計判断 | 理由 |
|------|---------|------|
| 削除方式 | 論理削除 | データ復元可能性、監査証跡確保 |
| 削除対象 | DRAFT状態のみ | 業務フローへの影響を最小化 |
| 削除権限 | 申請者本人のみ | セキュリティ、データ保護 |
| インデックス | deletedカラムに追加 | 一覧取得性能の維持 |
| ログレベル | INFO（成功）、WARN（エラー） | 運用監視の容易性 |
| メトリクス | Counter + Timer | 削除傾向と性能の両方を監視 |

## 🔗 関連ドキュメント

### プロジェクト全体
- [`spec.md`](../../../spec.md) - 備品購入申請システム（MVP）基本仕様
- [`design-app.md`](../../../design-app.md) - 既存アプリケーション設計
- [`design-deployment.md`](../../../design-deployment.md) - 既存配備設計
- [`design-observability.md`](../../../design-observability.md) - 既存観測設計
- [`Agents.md`](../../../Agents.md) - プロジェクト開発ルール

### 実装
- [`PurchaseRequest.java`](../../../src/main/java/com/example/purchaserequest/model/entity/PurchaseRequest.java) - エンティティ
- [`PurchaseRequestRepository.java`](../../../src/main/java/com/example/purchaserequest/repository/PurchaseRequestRepository.java) - リポジトリ
- [`PurchaseRequestService.java`](../../../src/main/java/com/example/purchaserequest/service/PurchaseRequestService.java) - サービス
- [`PurchaseRequestController.java`](../../../src/main/java/com/example/purchaserequest/controller/PurchaseRequestController.java) - コントローラー

## 📝 更新履歴

| 日付 | バージョン | 変更内容 | 作成者 |
|------|----------|---------|--------|
| 2026-04-03 | 1.0 | 初版作成（3観点に分割） | AI（Plan Mode） |

## 🤝 貢献

設計書の更新時は、以下のルールに従ってください：

1. **仕様変更**: まず [`spec.md`](spec.md) を更新
2. **設計変更**: 該当する設計書（app / deployment / observability）を更新
3. **整合性確認**: 他の設計書との整合性を確認
4. **バージョン更新**: 更新履歴に記録

---

**作成日**: 2026-04-03  
**作成者**: AI（Plan Mode）  
**バージョン**: 1.0