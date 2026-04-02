package com.example.purchaserequest.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RejectRequestDto {

    @NotBlank(message = "却下理由は必須です")
    @Size(max = 500, message = "却下理由は500文字以内で入力してください")
    private String rejectionReason;
}
