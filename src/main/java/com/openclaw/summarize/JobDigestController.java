package com.openclaw.summarize;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jobs")
public class JobDigestController {

    private final JobDigestService jobDigestService;

    public JobDigestController(JobDigestService jobDigestService) {
        this.jobDigestService = jobDigestService;
    }

    @PostMapping("/run-digest")
    public String runDigest() {
        jobDigestService.runWeeklyJobDigest();
        return "Weekly job digest triggered.";
    }
}