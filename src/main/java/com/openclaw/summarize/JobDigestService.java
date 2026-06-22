package com.openclaw.summarize;

import java.util.*;

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
    private final GmailService gmailService;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Value("${job-digest.recipients:}")
    private List<String> recipients;

    public JobDigestService(JavaMailSender mailSender, GmailService gmailService) {
        this.mailSender = mailSender;
        this.gmailService = gmailService;
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
        try {
            List<com.google.api.services.gmail.model.Message> messages = gmailService.fetchJobAlerts();
            List<JobListing> jobs = new ArrayList<>();

            for (com.google.api.services.gmail.model.Message msg : messages) {
                com.google.api.services.gmail.model.Message fullMsg = gmailService.getGmailService().users().messages()
                        .get("me", msg.getId()).setFormat("full").execute();

                String subject = getHeader(fullMsg, "Subject");
                String body = getEmailBody(fullMsg);

                JobListing job = parseJobFromEmail(subject, body);
                if (job != null) {
                    jobs.add(job);
                }
            }
            return jobs;
        } catch (Exception e) {
            System.err.println("Failed to fetch emails from Gmail: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private String getHeader(com.google.api.services.gmail.model.Message message, String name) {
        for (com.google.api.services.gmail.model.MessagePartHeader header : message.getPayload().getHeaders()) {
            if (header.getName().equalsIgnoreCase(name)) {
                return header.getValue();
            }
        }
        return "";
    }

    private String getEmailBody(com.google.api.services.gmail.model.Message message) {
        // Simple body extraction
        if (message.getPayload().getBody().getData() != null) {
            return new String(java.util.Base64.getUrlDecoder().decode(message.getPayload().getBody().getData()));
        }
        return "";
    }

    private JobListing parseJobFromEmail(String subject, String body) {
        JobListing job = new JobListing();
        job.setTitle(subject);
        job.setRemote(body.toLowerCase().contains("remote"));

        // Try to extract company (common patterns)
        String company = extractCompany(subject, body);
        if (!company.isEmpty()) job.setCompany(company);

        // Try to extract location
        String location = extractLocation(body);
        if (!location.isEmpty()) job.setLocation(location);

        // Try to extract apply link
        String link = extractApplyLink(body);
        if (!link.isEmpty()) job.setLink(link);

        return job;
    }

    private String extractCompany(String subject, String body) {
        // Try subject first (e.g. "Software Engineer at Acme Corp")
        if (subject.contains(" at ")) {
            return subject.substring(subject.indexOf(" at ") + 4).trim();
        }
        // Fallback: look for common patterns in body
        if (body.toLowerCase().contains("company:")) {
            int idx = body.toLowerCase().indexOf("company:");
            return body.substring(idx + 8, Math.min(idx + 40, body.length())).trim();
        }
        return "";
    }

    private String extractLocation(String body) {
        String lower = body.toLowerCase();
        if (lower.contains("location:")) {
            int idx = lower.indexOf("location:");
            return body.substring(idx + 9, Math.min(idx + 35, body.length())).trim();
        }
        if (lower.contains("remote")) return "Remote";
        return "";
    }

    private String extractApplyLink(String body) {
        // Simple URL extraction
        if (body.contains("http")) {
            int start = body.indexOf("http");
            int end = body.indexOf(" ", start);
            if (end == -1) end = Math.min(start + 200, body.length());
            return body.substring(start, end).trim();
        }
        return "";
    }

    private List<JobListing> deduplicateJobs(List<JobListing> jobs) {
        return jobs.stream()
                .distinct()
                .collect(Collectors.toList());
    }

    private List<JobListing> verifyActive(List<JobListing> jobs) {
        // Simple verification - keep jobs that have a link
        return jobs.stream()
                .filter(j -> j.getLink() != null && !j.getLink().isEmpty())
                .collect(Collectors.toList());
    }

    private List<JobListing> filterFullyRemote(List<JobListing> jobs) {
        return jobs.stream()
                .filter(JobListing::isRemote)
                .collect(Collectors.toList());
    }

    private List<JobListing> rankByRelevance(List<JobListing> jobs) {
        // Simple ranking: jobs with "Java" or "Spring" in title come first
        return jobs.stream()
                .sorted((a, b) -> {
                    boolean aHasJava = a.getTitle().toLowerCase().contains("java") || a.getTitle().toLowerCase().contains("spring");
                    boolean bHasJava = b.getTitle().toLowerCase().contains("java") || b.getTitle().toLowerCase().contains("spring");
                    return Boolean.compare(bHasJava, aHasJava);
                })
                .collect(Collectors.toList());
    }

    private void sendDigestEmail(List<JobListing> jobs) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(recipients.toArray(new String[0]));
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