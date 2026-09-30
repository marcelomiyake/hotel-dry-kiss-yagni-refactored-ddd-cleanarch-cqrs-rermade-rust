package com.stays.rate.application.port;

import com.stays.rate.NightlyRate;
import com.stays.rate.RateDraft;
import com.stays.rate.RateSchedule;

/** Persistence boundary for rate changes. */
public interface RateWritePort {
    NightlyRate saveRate(RateDraft draft);

    void saveSchedule(RateSchedule schedule);
}
