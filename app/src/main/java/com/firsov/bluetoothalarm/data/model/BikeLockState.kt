package com.firsov.bluetoothalarm.data.model

enum class LockState {
    LOCKED,
    UNLOCKED,
    ALARM,
    UNKNOWN  // Добавьте это, чтобы не было ошибок с дефолтным состоянием
}