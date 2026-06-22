package com.tars.spotai.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing the email-to-user mapping stored in the sharded email-index table.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserEmail {
    private Long id;
    private Long userId;
    private String email;
}
