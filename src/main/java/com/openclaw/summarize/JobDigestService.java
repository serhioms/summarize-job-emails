package com.openclaw.summarize;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JobDigestService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    public JobDigestService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void runWeeklyJobDigest() {
        List<JobListing> jobs = fetchJobAlertsFromGmail();
        List<JobListing> deduplicated = deduplicateJobs(jobs);
        List<JobListing> verified = verifyActive(deduplicated);
        List<JobListing> remoteOnly = filterFullyRemote(verified);
        List<JobListing> ranked = rankByRelevance(remoteOnly);

        if (!ranked.isEmpty()) {
            sendDigestEmail(ranked);
        }
    }

    private List<JobListing> fetchJobAlertsFromGmail() {
        // TODO: Replace with real Gmail API integration
        // For now returns empty list
        return new ArrayList<>();
    }

    private List<JobListing> deduplicateJobs(List<JobListing> jobs) {
        return jobs.stream()
                .distinct()
                .collect(Collectors.toList());
    }

    private List<JobListing> verifyActive(List<JobListing> jobs) {
        // TODO: Check if job links are still active
        return jobs;
    }

    private List<JobListing> filterFullyRemote(List<JobListing> jobs) {
        return jobs.stream()
                .filter(JobListing::isRemote)
                .collect(Collectors.toList());
    }

    private List<JobListing> rankByRelevance(List<JobListing> jobs) {
        // TODO: Rank traditional Java/Spring Boot roles higher
        return jobs;
    }

    private void sendDigestEmail(List<JobListing> jobs) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo("sergey.moskovskiy@gmail.com", "igorart7@gmail.com");
            helper.setSubject("Weekly fully remote Java jobs — " + java.time.LocalDate.now());

            StringBuilder body = new StringBuilder();
            body.append("<h2>Weekly Remote Java Jobs</h2>");
            body.append("<p>Found ").append(jobs.size()).append(" fully remote positions this week.</p>");

            for (JobListing job : jobs) {
                body.append("<p><b>").append(job.getTitle()).append("</b><br>")
                    .append(job.getCompany()).append(" — ").append(job.getLocation()).append("<br>")
                    .append("<a href='").append(job.getLink()).append("'>Apply</a></p>");
            }

            helper.setText(body.toString(), true);
            mailSender.send(message);

        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send job digest email", e);
        }
    }
}