package com.example.purchaserequest.service;

import com.example.purchaserequest.config.ApplicationConfig;
import com.example.purchaserequest.exception.RequestNotFoundException;
import com.example.purchaserequest.exception.UnauthorizedOperationException;
import com.example.purchaserequest.model.RequestStatus;
import com.example.purchaserequest.model.Role;
import com.example.purchaserequest.exception.InvalidStatusTransitionException;
import com.example.purchaserequest.model.dto.CreatePurchaseRequestDto;
import com.example.purchaserequest.model.dto.DeletedPurchaseRequestDto;
import com.example.purchaserequest.model.dto.PurchaseRequestDto;
import com.example.purchaserequest.model.entity.PurchaseRequest;
import com.example.purchaserequest.model.entity.User;
import com.example.purchaserequest.repository.PurchaseRequestRepository;
import com.example.purchaserequest.repository.UserRepository;
import com.example.purchaserequest.util.BusinessLogger;
import com.example.purchaserequest.util.BusinessMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseRequestServiceImplTest {

    @Mock
    private PurchaseRequestRepository purchaseRequestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationConfig applicationConfig;
    @Mock
    private BusinessLogger businessLogger;
    @Mock
    private BusinessMetrics businessMetrics;

    @InjectMocks
    private PurchaseRequestServiceImpl service;

    private User normalUser;
    private User approverUser;

    @BeforeEach
    void setUp() {
        normalUser = User.builder()
            .id(1L)
            .username("yamada")
            .password("password")
            .name("山田太郎")
            .department("開発部")
            .email("yamada@example.com")
            .roles(Set.of(Role.ROLE_USER))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        approverUser = User.builder()
            .id(2L)
            .username("sato")
            .password("password")
            .name("佐藤花子")
            .department("管理部")
            .email("sato@example.com")
            .roles(Set.of(Role.ROLE_USER, Role.ROLE_APPROVER))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    @Nested
    @DisplayName("申請登録テスト")
    class CreateDraftTest {

        @Test
        @DisplayName("正常に下書き申請が登録できること")
        void 正常に下書き登録できること() {
            CreatePurchaseRequestDto dto = new CreatePurchaseRequestDto(
                "ノートPC", 1, new BigDecimal("150000"), "開発業務用", null, null
            );

            when(userRepository.findById(1L)).thenReturn(Optional.of(normalUser));
            when(applicationConfig.getHighAmountThreshold()).thenReturn(new BigDecimal("50000"));
            when(purchaseRequestRepository.save(any(PurchaseRequest.class))).thenAnswer(invocation -> {
                PurchaseRequest req = invocation.getArgument(0);
                return PurchaseRequest.builder()
                    .id(1L)
                    .requesterId(req.getRequesterId())
                    .itemName(req.getItemName())
                    .quantity(req.getQuantity())
                    .unitPrice(req.getUnitPrice())
                    .purchaseReason(req.getPurchaseReason())
                    .status(RequestStatus.DRAFT)
                    .createdAt(req.getCreatedAt())
                    .updatedAt(req.getUpdatedAt())
                    .createdBy(req.getCreatedBy())
                    .updatedBy(req.getUpdatedBy())
                    .build();
            });

            PurchaseRequestDto result = service.createDraft(dto, 1L);

            assertThat(result.getItemName()).isEqualTo("ノートPC");
            assertThat(result.getStatus()).isEqualTo(RequestStatus.DRAFT);
            assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("150000"));
            verify(purchaseRequestRepository).save(any(PurchaseRequest.class));
        }

        @Test
        @DisplayName("存在しないユーザーで登録すると例外が発生すること")
        void 存在しないユーザーで登録すると例外が発生() {
            CreatePurchaseRequestDto dto = new CreatePurchaseRequestDto(
                "ノートPC", 1, new BigDecimal("150000"), "開発業務用", null, null
            );

            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createDraft(dto, 999L))
                .isInstanceOf(RequestNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("申請提出テスト")
    class SubmitRequestTest {

        @Test
        @DisplayName("自分の下書き申請を提出できること")
        void 自分の下書き申請を提出できること() {
            PurchaseRequest request = PurchaseRequest.builder()
                .id(1L)
                .requesterId(1L)
                .itemName("ノートPC")
                .quantity(1)
                .unitPrice(new BigDecimal("150000"))
                .purchaseReason("開発業務用")
                .status(RequestStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy("yamada")
                .updatedBy("yamada")
                .build();

            when(purchaseRequestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(purchaseRequestRepository.save(any(PurchaseRequest.class))).thenReturn(request);
            when(userRepository.findById(1L)).thenReturn(Optional.of(normalUser));
            when(applicationConfig.getHighAmountThreshold()).thenReturn(new BigDecimal("50000"));

            PurchaseRequestDto result = service.submitRequest(1L, 1L);

            assertThat(result.getStatus()).isEqualTo(RequestStatus.SUBMITTED);
        }

        @Test
        @DisplayName("他人の申請を提出しようとすると例外が発生すること")
        void 他人の申請を提出すると例外が発生() {
            PurchaseRequest request = PurchaseRequest.builder()
                .id(1L)
                .requesterId(1L)
                .itemName("ノートPC")
                .quantity(1)
                .unitPrice(new BigDecimal("150000"))
                .purchaseReason("開発業務用")
                .status(RequestStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy("yamada")
                .updatedBy("yamada")
                .build();

            when(purchaseRequestRepository.findById(1L)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.submitRequest(1L, 999L))
                .isInstanceOf(UnauthorizedOperationException.class)
                .hasMessage("自分の申請のみ操作できます");
        }
    }

    @Nested
    @DisplayName("承認テスト")
    class ApproveRequestTest {

        @Test
        @DisplayName("承認者が申請を承認できること")
        void 承認者が申請を承認できること() {
            PurchaseRequest request = PurchaseRequest.builder()
                .id(1L)
                .requesterId(1L)
                .itemName("ノートPC")
                .quantity(1)
                .unitPrice(new BigDecimal("150000"))
                .purchaseReason("開発業務用")
                .status(RequestStatus.SUBMITTED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy("yamada")
                .updatedBy("yamada")
                .build();

            when(userRepository.findById(2L)).thenReturn(Optional.of(approverUser));
            when(purchaseRequestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(purchaseRequestRepository.save(any(PurchaseRequest.class))).thenReturn(request);
            when(userRepository.findById(1L)).thenReturn(Optional.of(normalUser));
            when(applicationConfig.getHighAmountThreshold()).thenReturn(new BigDecimal("50000"));

            PurchaseRequestDto result = service.approveRequest(1L, 2L);

            assertThat(result.getStatus()).isEqualTo(RequestStatus.APPROVED);
        }

        @Test
        @DisplayName("承認権限のないユーザーが承認すると例外が発生すること")
        void 権限なしで承認すると例外が発生() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(normalUser));

            assertThatThrownBy(() -> service.approveRequest(1L, 1L))
                .isInstanceOf(UnauthorizedOperationException.class)
                .hasMessage("承認権限がありません");
        }
    }

    @Nested
    @DisplayName("下書き削除テスト")
    class DeleteDraftRequestTest {

        @Test
        @DisplayName("正常に下書き申請を削除できること")
        void 正常に下書き申請を削除できること() {
            PurchaseRequest request = PurchaseRequest.builder()
                .id(1L)
                .requesterId(1L)
                .itemName("ノートPC")
                .quantity(1)
                .unitPrice(new BigDecimal("150000"))
                .purchaseReason("開発業務用")
                .status(RequestStatus.DRAFT)
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy("yamada")
                .updatedBy("yamada")
                .build();

            when(purchaseRequestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(userRepository.findByUsername("yamada")).thenReturn(Optional.of(normalUser));
            when(purchaseRequestRepository.save(any(PurchaseRequest.class))).thenReturn(request);

            DeletedPurchaseRequestDto result = service.deleteDraftRequest(1L, "yamada");

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getDeleted()).isTrue();
            assertThat(result.getDeletedAt()).isNotNull();
            verify(purchaseRequestRepository).save(any(PurchaseRequest.class));
        }

        @Test
        @DisplayName("申請が存在しない場合に例外が発生すること")
        void 申請が存在しない場合に例外が発生() {
            when(purchaseRequestRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteDraftRequest(999L, "yamada"))
                .isInstanceOf(RequestNotFoundException.class);
        }

        @Test
        @DisplayName("削除済みの場合に例外が発生すること")
        void 削除済みの場合に例外が発生() {
            PurchaseRequest request = PurchaseRequest.builder()
                .id(1L)
                .requesterId(1L)
                .itemName("ノートPC")
                .quantity(1)
                .unitPrice(new BigDecimal("150000"))
                .purchaseReason("開発業務用")
                .status(RequestStatus.DRAFT)
                .deleted(true)
                .deletedAt(LocalDateTime.now())
                .deletedBy("yamada")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy("yamada")
                .updatedBy("yamada")
                .build();

            when(purchaseRequestRepository.findById(1L)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.deleteDraftRequest(1L, "yamada"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("この申請は既に削除されています");
        }

        @Test
        @DisplayName("申請者本人でない場合に例外が発生すること")
        void 申請者本人でない場合に例外が発生() {
            PurchaseRequest request = PurchaseRequest.builder()
                .id(1L)
                .requesterId(1L)
                .itemName("ノートPC")
                .quantity(1)
                .unitPrice(new BigDecimal("150000"))
                .purchaseReason("開発業務用")
                .status(RequestStatus.DRAFT)
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy("yamada")
                .updatedBy("yamada")
                .build();

            when(purchaseRequestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(userRepository.findByUsername("sato")).thenReturn(Optional.of(approverUser));

            assertThatThrownBy(() -> service.deleteDraftRequest(1L, "sato"))
                .isInstanceOf(UnauthorizedOperationException.class)
                .hasMessage("自分の申請のみ削除できます");
        }

        @Test
        @DisplayName("ステータスがDRAFT以外の場合に例外が発生すること")
        void ステータスがDRAFT以外の場合に例外が発生() {
            PurchaseRequest request = PurchaseRequest.builder()
                .id(1L)
                .requesterId(1L)
                .itemName("ノートPC")
                .quantity(1)
                .unitPrice(new BigDecimal("150000"))
                .purchaseReason("開発業務用")
                .status(RequestStatus.SUBMITTED)
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy("yamada")
                .updatedBy("yamada")
                .build();

            when(purchaseRequestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(userRepository.findByUsername("yamada")).thenReturn(Optional.of(normalUser));

            assertThatThrownBy(() -> service.deleteDraftRequest(1L, "yamada"))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("下書き状態の申請のみ削除できます");
        }
    }

    @Nested
    @DisplayName("却下テスト")
    class RejectRequestTest {

        @Test
        @DisplayName("承認者が申請を却下できること")
        void 承認者が申請を却下できること() {
            PurchaseRequest request = PurchaseRequest.builder()
                .id(1L)
                .requesterId(1L)
                .itemName("ノートPC")
                .quantity(1)
                .unitPrice(new BigDecimal("150000"))
                .purchaseReason("開発業務用")
                .status(RequestStatus.SUBMITTED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy("yamada")
                .updatedBy("yamada")
                .build();

            when(userRepository.findById(2L)).thenReturn(Optional.of(approverUser));
            when(purchaseRequestRepository.findById(1L)).thenReturn(Optional.of(request));
            when(purchaseRequestRepository.save(any(PurchaseRequest.class))).thenReturn(request);
            when(userRepository.findById(1L)).thenReturn(Optional.of(normalUser));
            when(applicationConfig.getHighAmountThreshold()).thenReturn(new BigDecimal("50000"));

            PurchaseRequestDto result = service.rejectRequest(1L, 2L, "予算超過のため");

            assertThat(result.getStatus()).isEqualTo(RequestStatus.REJECTED);
            assertThat(result.getRejectionReason()).isEqualTo("予算超過のため");
        }
    }
}
