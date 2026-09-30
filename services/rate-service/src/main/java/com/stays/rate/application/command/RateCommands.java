package com.stays.rate.application.command;

import com.stays.rate.NightlyRate;
import com.stays.rate.RateDraft;
import com.stays.rate.RateSchedule;

public interface RateCommands {
    NightlyRate setRate(RateDraft draft);

    void createSchedule(RateSchedule schedule);
}
