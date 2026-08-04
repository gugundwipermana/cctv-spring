package com.homeserver.cctv.dto;

/** Setara elemen array "matches" di response Laravel: {"name": "Gugun", "confidence": 0.62} */
public record FaceMatch(String name, double confidence) {}
