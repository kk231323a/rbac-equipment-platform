package com.kk.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EquipmentCreateRequest {

    @NotBlank(message = "设备编号不能为空")
    @Size(max = 32, message = "设备编号不能超过32个字符")
    private String deviceNo;

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 50, message = "设备名称不能超过50个字符")
    private String name;

    @Size(max = 30, message = "设备型号不能超过30个字符")
    private String model;
}