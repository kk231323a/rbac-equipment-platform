package com.kk.rbac.vo;

import lombok.Data;


import java.time.LocalDateTime;

@Data
public class BorrowRequestVO {

    private Integer requestId;
    private Integer deviceId;
    private String deviceNo;
    private String deviceName;
    private String deviceStatus;
    private Integer borrowerId;
    private String borrowerName;
    private LocalDateTime createTime;
    private LocalDateTime dueTime;
    private LocalDateTime borrowTime;
}