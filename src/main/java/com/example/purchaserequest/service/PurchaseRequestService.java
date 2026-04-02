package com.example.purchaserequest.service;

import com.example.purchaserequest.model.RequestStatus;
import com.example.purchaserequest.model.dto.CreatePurchaseRequestDto;
import com.example.purchaserequest.model.dto.PurchaseRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PurchaseRequestService {

    /** 申請を登録（下書き） */
    PurchaseRequestDto createDraft(CreatePurchaseRequestDto dto, Long requesterId);

    /** 申請を提出 */
    PurchaseRequestDto submitRequest(Long requestId, Long requesterId);

    /** 申請一覧を取得 */
    Page<PurchaseRequestDto> getRequests(RequestStatus status, Long userId, boolean isApprover, Pageable pageable);

    /** 申請詳細を取得 */
    PurchaseRequestDto getRequestById(Long requestId, Long userId, boolean isApprover);

    /** 申請を承認 */
    PurchaseRequestDto approveRequest(Long requestId, Long approverId);

    /** 申請を却下 */
    PurchaseRequestDto rejectRequest(Long requestId, Long approverId, String rejectionReason);
}
