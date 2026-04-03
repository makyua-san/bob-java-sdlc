package com.example.purchaserequest.controller;

import com.example.purchaserequest.model.RequestStatus;
import com.example.purchaserequest.model.dto.ApiResponse;
import com.example.purchaserequest.model.dto.CreatePurchaseRequestDto;
import com.example.purchaserequest.model.dto.DeletedPurchaseRequestDto;
import com.example.purchaserequest.model.dto.PurchaseRequestDto;
import com.example.purchaserequest.model.dto.RejectRequestDto;
import com.example.purchaserequest.service.PurchaseRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class PurchaseRequestController {

    private final PurchaseRequestService purchaseRequestService;
    private final com.example.purchaserequest.repository.UserRepository userRepository;

    /** 申請登録（下書き） */
    @PostMapping
    public ResponseEntity<ApiResponse<PurchaseRequestDto>> createDraft(
            @Valid @RequestBody CreatePurchaseRequestDto dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        PurchaseRequestDto result = purchaseRequestService.createDraft(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(result, "申請を下書き保存しました"));
    }

    /** 申請提出 */
    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<PurchaseRequestDto>> submitRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        PurchaseRequestDto result = purchaseRequestService.submitRequest(id, userId);
        return ResponseEntity.ok(ApiResponse.success(result, "申請を提出しました"));
    }

    /** 申請一覧取得 */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PurchaseRequestDto>>> getRequests(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        boolean isApprover = userDetails.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_APPROVER"));
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PurchaseRequestDto> result = purchaseRequestService.getRequests(status, userId, isApprover, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** 申請詳細取得 */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseRequestDto>> getRequestById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        boolean isApprover = userDetails.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_APPROVER"));
        PurchaseRequestDto result = purchaseRequestService.getRequestById(id, userId, isApprover);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** 申請承認 */
    @PatchMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<PurchaseRequestDto>> approveRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        PurchaseRequestDto result = purchaseRequestService.approveRequest(id, userId);
        return ResponseEntity.ok(ApiResponse.success(result, "申請を承認しました"));
    }

    /** 下書き申請を削除（論理削除） */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<DeletedPurchaseRequestDto>> deleteDraftRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails.getUsername();
        DeletedPurchaseRequestDto result = purchaseRequestService.deleteDraftRequest(id, username);
        return ResponseEntity.ok(ApiResponse.success(result, "下書きを削除しました"));
    }

    /** 申請却下 */
    @PatchMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<PurchaseRequestDto>> rejectRequest(
            @PathVariable Long id,
            @Valid @RequestBody RejectRequestDto dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        PurchaseRequestDto result = purchaseRequestService.rejectRequest(id, userId, dto.getRejectionReason());
        return ResponseEntity.ok(ApiResponse.success(result, "申請を却下しました"));
    }

    private Long getUserId(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new com.example.purchaserequest.exception.RequestNotFoundException("ユーザーが見つかりません"))
            .getId();
    }
}
