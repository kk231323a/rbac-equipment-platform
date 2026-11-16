package com.kk.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EquipmentUpdateRequest {
    @NotBlank(message = "设备名称不能为空")
    @Size(max = 50, message = "设备名称不能超过50个字符")
    private String name;

    @Size(max = 30, message = "设备型号不能超过30个字符")
    private String model;


}

