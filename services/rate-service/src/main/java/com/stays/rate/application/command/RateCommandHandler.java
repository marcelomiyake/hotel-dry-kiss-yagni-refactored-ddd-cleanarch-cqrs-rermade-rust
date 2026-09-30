package com.stays.rate.application.command;

import com.stays.rate.NightlyRate;
import com.stays.rate.RateDraft;
import com.stays.rate.RateSchedule;
import com.stays.rate.application.port.RateWritePort;
import org.springframework.stereotype.Service;

@Service
public final class RateCommandHandler implements RateCommands {
    private final RateWritePort rates;

    public RateCommandHandler(RateWritePort rates) {
        this.rates = rates;
    }

    @Override
    public NightlyRate setRate(RateDraft draft) {
        return rates.saveRate(draft);
    }

    @Override
    public void createSchedule(RateSchedule schedule) {
        rates.saveSchedule(schedule);
    }
}
