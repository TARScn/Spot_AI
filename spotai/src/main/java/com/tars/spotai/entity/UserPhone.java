package com.tars.spotai.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing the phone-to-user mapping stored in the sharded phone-index table.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserPhone {
    private Long id;
    private Long userId;
    private String phone;
}
