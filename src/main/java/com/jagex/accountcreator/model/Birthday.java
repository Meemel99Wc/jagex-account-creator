package com.jagex.accountcreator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents account birthday
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Birthday {
    private int day;
    private int month;
    private int year;
}
