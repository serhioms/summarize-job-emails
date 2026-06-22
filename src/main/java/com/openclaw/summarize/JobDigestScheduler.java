package com.openclaw.summarize;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class JobDigestScheduler {

    private final JobDigestService jobDigestService;

    public JobDigestScheduler(JobDigestService jobDigestService) {
        this.jobDigestService = jobDigestService;
    }

    // Run every Wednesday at 9:00 AM Toronto time
    @Scheduled(cron = "0 0 9 * * 3", zone = "America/Toronto")
    public void runWeeklyDigest() {
        jobDigestService.runWeeklyJobDigest();
    }
}