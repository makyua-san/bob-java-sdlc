package com.example.purchaserequest.repository;

import com.example.purchaserequest.model.RequestStatus;
import com.example.purchaserequest.model.entity.PurchaseRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
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

    /**
     * 削除されていない申請を申請者IDで検索
     */
    Page<PurchaseRequest> findByRequesterIdAndDeletedFalse(Long requesterId, Pageable pageable);

    /**
     * 削除されていない申請をステータスで検索
     */
    Page<PurchaseRequest> findByStatusAndDeletedFalse(RequestStatus status, Pageable pageable);

    /**
     * 削除されていない申請を申請者IDとステータスで検索
     */
    Page<PurchaseRequest> findByRequesterIdAndStatusAndDeletedFalse(
        Long requesterId,
        RequestStatus status,
        Pageable pageable
    );

    /**
     * 削除されていない申請をIDで検索
     */
    Optional<PurchaseRequest> findByIdAndDeletedFalse(Long id);

    /**
     * 削除されていない申請を全件検索
     */
    Page<PurchaseRequest> findByDeletedFalse(Pageable pageable);
}
