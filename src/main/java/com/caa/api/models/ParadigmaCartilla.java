package com.caa.api.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ParadigmaCartilla {
    @JsonProperty("taxonomica")
    TAXONOMICA,

    @JsonProperty("esquematica")
    ESQUEMATICA
}
