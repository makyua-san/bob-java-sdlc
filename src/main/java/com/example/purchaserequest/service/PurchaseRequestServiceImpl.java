package com.example.purchaserequest.service;

import com.example.purchaserequest.config.ApplicationConfig;
import com.example.purchaserequest.exception.RequestNotFoundException;
import com.example.purchaserequest.exception.UnauthorizedOperationException;
import com.example.purchaserequest.model.RequestStatus;
import com.example.purchaserequest.model.dto.CreatePurchaseRequestDto;
import com.example.purchaserequest.model.dto.PurchaseRequestDto;
import com.example.purchaserequest.model.dto.UserSummaryDto;
import com.example.purchaserequest.model.entity.PurchaseRequest;
import com.example.purchaserequest.model.entity.User;
import com.example.purchaserequest.repository.PurchaseRequestRepository;
import com.example.purchaserequest.repository.UserRepository;
import com.example.purchaserequest.util.BusinessLogger;
import com.example.purchaserequest.util.BusinessMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PurchaseRequestServiceImpl implements PurchaseRequestService {

    private final PurchaseRequestRepository purchaseRequestRepository;
    private final UserRepository userRepository;
    private final ApplicationConfig applicationConfig;
    private final BusinessLogger businessLogger;
    private final BusinessMetrics businessMetrics;

    @Override
    @Transactional
    public PurchaseRequestDto createDraft(CreatePurchaseRequestDto dto, Long requesterId) {
        long startTime = System.currentTimeMillis();

        try {
            User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new RequestNotFoundException("ユーザーが見つかりません"));

            PurchaseRequest request = PurchaseRequest.builder()
                .requesterId(requesterId)
                .itemName(dto.getItemName())
                .quantity(dto.getQuantity())
                .unitPrice(dto.getUnitPrice())
                .purchaseReason(dto.getPurchaseReason())
                .desiredDeliveryDate(dto.getDesiredDeliveryDate())
                .remarks(dto.getRemarks())
                .status(RequestStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(requester.getUsername())
                .updatedBy(requester.getUsername())
                .build();

            PurchaseRequest saved = purchaseRequestRepository.save(request);

            // 業務ログ・メトリクス
            Map<String, Object> context = Map.of(
                "requestId", saved.getId(),
                "itemName", saved.getItemName(),
                "totalAmount", saved.getTotalAmount(),
                "status", saved.getStatus().name(),
                "duration", System.currentTimeMillis() - startTime
            );
            businessLogger.logBusinessEvent("CREATE_REQUEST", "申請を登録しました", context);
            businessMetrics.recordBusinessEvent("CREATE_REQUEST", "success");
            businessMetrics.recordPurchaseRequest(RequestStatus.DRAFT, saved.getTotalAmount());

            if (saved.isHighAmount(applicationConfig.getHighAmountThreshold())) {
                businessMetrics.recordHighAmountRequest();
            }

            return toDto(saved, requester, null);
        } catch (Exception e) {
            businessLogger.logError("CREATE_REQUEST", "申請登録に失敗しました", e,
                Map.of("itemName", dto.getItemName(), "requesterId", requesterId,
                       "duration", System.currentTimeMillis() - startTime));
            throw e;
        }
    }

    @Override
    @Transactional
    public PurchaseRequestDto submitRequest(Long requestId, Long requesterId) {
        long startTime = System.currentTimeMillis();

        PurchaseRequest request = purchaseRequestRepository.findById(requestId)
            .orElseThrow(() -> new RequestNotFoundException("申請が見つかりません"));

        validateOwnership(request, requesterId);
        request.submit();

        PurchaseRequest saved = purchaseRequestRepository.save(request);
        User requester = userRepository.findById(saved.getRequesterId())
            .orElseThrow(() -> new RequestNotFoundException("ユーザーが見つかりません"));

        Map<String, Object> context = Map.of(
            "requestId", saved.getId(),
            "previousStatus", "DRAFT",
            "newStatus", "SUBMITTED",
            "duration", System.currentTimeMillis() - startTime
        );
        businessLogger.logBusinessEvent("SUBMIT_REQUEST", "申請を提出しました", context);
        businessMetrics.recordBusinessEvent("SUBMIT_REQUEST", "success");

        return toDto(saved, requester, null);
    }

    @Override
    public Page<PurchaseRequestDto> getRequests(RequestStatus status, Long userId, boolean isApprover, Pageable pageable) {
        Page<PurchaseRequest> requests;

        if (isApprover) {
            requests = (status != null)
                ? purchaseRequestRepository.findByStatus(status, pageable)
                : purchaseRequestRepository.findAll(pageable);
        } else {
            requests = (status != null)
                ? purchaseRequestRepository.findByRequesterIdAndStatus(userId, status, pageable)
                : purchaseRequestRepository.findByRequesterId(userId, pageable);
        }

        return requests.map(this::toDtoWithUsers);
    }

    @Override
    public PurchaseRequestDto getRequestById(Long requestId, Long userId, boolean isApprover) {
        PurchaseRequest request = purchaseRequestRepository.findById(requestId)
            .orElseThrow(() -> new RequestNotFoundException("申請が見つかりません"));

        if (!isApprover) {
            validateOwnership(request, userId);
        }

        return toDtoWithUsers(request);
    }

    @Override
    @Transactional
    public PurchaseRequestDto approveRequest(Long requestId, Long approverId) {
        long startTime = System.currentTimeMillis();

        User approver = userRepository.findById(approverId)
            .orElseThrow(() -> new RequestNotFoundException("ユーザーが見つかりません"));
        validateApprovalAuthority(approver);

        PurchaseRequest request = purchaseRequestRepository.findById(requestId)
            .orElseThrow(() -> new RequestNotFoundException("申請が見つかりません"));

        request.approve(approverId);
        PurchaseRequest saved = purchaseRequestRepository.save(request);

        User requester = userRepository.findById(saved.getRequesterId())
            .orElseThrow(() -> new RequestNotFoundException("ユーザーが見つかりません"));

        Map<String, Object> context = Map.of(
            "requestId", saved.getId(),
            "approverId", approverId,
            "previousStatus", "SUBMITTED",
            "newStatus", "APPROVED",
            "duration", System.currentTimeMillis() - startTime
        );
        businessLogger.logBusinessEvent("APPROVE_REQUEST", "申請を承認しました", context);
        businessMetrics.recordBusinessEvent("APPROVE_REQUEST", "success");
        businessMetrics.recordPurchaseRequest(RequestStatus.APPROVED, saved.getTotalAmount());

        return toDto(saved, requester, approver);
    }

    @Override
    @Transactional
    public PurchaseRequestDto rejectRequest(Long requestId, Long approverId, String rejectionReason) {
        long startTime = System.currentTimeMillis();

        User approver = userRepository.findById(approverId)
            .orElseThrow(() -> new RequestNotFoundException("ユーザーが見つかりません"));
        validateApprovalAuthority(approver);

        PurchaseRequest request = purchaseRequestRepository.findById(requestId)
            .orElseThrow(() -> new RequestNotFoundException("申請が見つかりません"));

        request.reject(approverId, rejectionReason);
        PurchaseRequest saved = purchaseRequestRepository.save(request);

        User requester = userRepository.findById(saved.getRequesterId())
            .orElseThrow(() -> new RequestNotFoundException("ユーザーが見つかりません"));

        Map<String, Object> context = Map.of(
            "requestId", saved.getId(),
            "approverId", approverId,
            "rejectionReason", rejectionReason,
            "previousStatus", "SUBMITTED",
            "newStatus", "REJECTED",
            "duration", System.currentTimeMillis() - startTime
        );
        businessLogger.logBusinessEvent("REJECT_REQUEST", "申請を却下しました", context);
        businessMetrics.recordBusinessEvent("REJECT_REQUEST", "success");
        businessMetrics.recordPurchaseRequest(RequestStatus.REJECTED, saved.getTotalAmount());

        return toDto(saved, requester, approver);
    }

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

    private PurchaseRequestDto toDtoWithUsers(PurchaseRequest request) {
        User requester = userRepository.findById(request.getRequesterId())
            .orElse(null);
        User approver = (request.getApproverId() != null)
            ? userRepository.findById(request.getApproverId()).orElse(null)
            : null;
        return toDto(request, requester, approver);
    }

    private PurchaseRequestDto toDto(PurchaseRequest request, User requester, User approver) {
        return PurchaseRequestDto.builder()
            .id(request.getId())
            .itemName(request.getItemName())
            .quantity(request.getQuantity())
            .unitPrice(request.getUnitPrice())
            .totalAmount(request.getTotalAmount())
            .purchaseReason(request.getPurchaseReason())
            .desiredDeliveryDate(request.getDesiredDeliveryDate())
            .remarks(request.getRemarks())
            .status(request.getStatus())
            .highAmount(request.isHighAmount(applicationConfig.getHighAmountThreshold()))
            .requester(toUserSummary(requester))
            .approver(toUserSummary(approver))
            .rejectionReason(request.getRejectionReason())
            .createdAt(request.getCreatedAt())
            .updatedAt(request.getUpdatedAt())
            .build();
    }

    private UserSummaryDto toUserSummary(User user) {
        if (user == null) return null;
        return UserSummaryDto.builder()
            .id(user.getId())
            .name(user.getName())
            .department(user.getDepartment())
            .build();
    }
}
