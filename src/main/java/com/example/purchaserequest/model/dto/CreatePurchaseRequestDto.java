package com.example.purchaserequest.model.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

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
