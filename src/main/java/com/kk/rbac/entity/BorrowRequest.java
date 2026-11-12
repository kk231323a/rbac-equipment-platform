package com.kk.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Data
@TableName("borrow_request")
public class BorrowRequest {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer deviceId;
    private Integer borrowerId;
    private Integer approverId;
    private String status;

    private LocalDateTime createTime;
    private LocalDateTime approveTime;
    private LocalDateTime dueTime;
    private LocalDateTime borrowTime;
    private LocalDateTime returnTime;
    private LocalDateTime updateTime;
}