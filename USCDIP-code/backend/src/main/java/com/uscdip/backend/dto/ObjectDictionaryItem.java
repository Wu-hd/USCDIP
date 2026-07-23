package com.uscdip.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ObjectDictionaryItem {

    private String objectType;
    private String primaryKey;
    private List<String> foreignKeys;
    private String statusField;
}
