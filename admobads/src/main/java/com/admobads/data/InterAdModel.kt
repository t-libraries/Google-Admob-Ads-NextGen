package com.admobads.data

import androidx.annotation.Keep

@Keep
data class InterAdModel(
    val inter_type: String = "timer",
    var loading_type : String = "manual",
    var inter_timer_reset_after_app_open : Boolean = false,
    var clicks_between_inter_timer_start : Int = 1,
    var clicks_between_inter_timer_gap : Int = 1,
    val inter_counter_start: Int = 0,
    val inter_counter_gap: Int = 0,
    val inter_start_after_seconds: Long = 0,
    val inter_start_load_before_seconds: Long = 0,
    val inter_gap_after_seconds: Long = 0,
    val inter_gap_load_before_seconds: Long = 0
)
