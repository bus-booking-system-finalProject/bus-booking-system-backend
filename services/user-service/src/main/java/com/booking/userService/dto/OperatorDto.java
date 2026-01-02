package com.booking.userService.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class OperatorDto {
    private UUID id;
    private String name;
}