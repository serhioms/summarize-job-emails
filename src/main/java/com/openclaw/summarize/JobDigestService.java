package com.openclaw.summarize;

import io.micrometer.common.util.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class JobDigestService {

    private final JavaMailSender mailSender;
    private final GmailService gmailService;
    private final FileService fileService;


    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Value("${job-digest.file-prefix:}")
    String filePrefix;

    @Value("${job-digest.recipients:}")
    private List<String> recipients;

    public JobDigestService(JavaMailSender mailSender, GmailService gmailService, FileService fileService) {
        this.mailSender = mailSender;
        this.gmailService = gmailService;
        this.fileService = fileService;
    }

    public void runWeeklyJobDigest() {
        int lookbackDays = 15;
        List<JobListing> candidates = fetchJobAlertsFromGmail(lookbackDays);

        if (candidates.size() < 3) {
            lookbackDays = 30;
            candidates = fetchJobAlertsFromGmail(lookbackDays);
        }

        List<JobListing> deduplicated = deduplicateJobs(candidates);
        List<JobListing> verified = verifyLiveStatus(deduplicated);
        List<JobListing> remoteOnly = filterFullyRemote(verified);
        List<JobListing> ranked = rankJobs(remoteOnly);

        sendDigestEmail(ranked, lookbackDays, candidates.size());
    }

    private List<JobListing> fetchJobAlertsFromGmail(int days) {
        List<JobListing> jobs = new ArrayList<>();
        try {
            String query = String.format(
                "(subject:(Java OR \"Spring Boot\" OR \"Software Engineer\" OR \"Backend Engineer\") " +
                "(remote OR \"work from home\" OR WFH OR \"fully remote\")) newer_than:%dd",
                days
            );

            List<com.google.api.services.gmail.model.Message> messages = gmailService.fetchJobAlerts(query);

            for (com.google.api.services.gmail.model.Message msg : messages) {
                com.google.api.services.gmail.model.Message fullMsg = gmailService.getMessage(msg.getId());

                String subject = getHeader(fullMsg, "Subject");
                String body = getEmailBody(fullMsg);
                String date = getHeader(fullMsg, "Date");

                if (body == null ) {
                    continue;
                } else if( skipEmail("Here is the final shortlist", body) ){
                    continue;
                } else if( parseLinkedInJobs("Your job alert for", subject, body, date, jobs)
                    || parseLinkedInJobs("Jobs similar to", subject, body, date, jobs)
                    || parseLinkedInJobs("Top job picks for you:", subject, body, date, jobs)
                    ){
                    continue;
                } else {
                    JobListing job = parseJobFromEmail(subject, body, date);
                    if (job != null && isTargetRole(job)) {
                        jobs.add(job);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Failed to fetch Gmail: " + e.getMessage());
            e.printStackTrace();
        }
        return jobs;
    }

    private boolean skipEmail(String hereIsTheFinalShortlist, String body) {
        return body.contains(hereIsTheFinalShortlist);
    }

    private boolean parseLinkedInJobs(String mark, String subject, String body, String date, List<JobListing> jobs) {
        if( !body.contains(mark) ){
            return false;
        }
        String[] parse = body.split("\r\n");
        if( parse.length < 2 ){
            return false;
        }
        subject = parse[0];
        try {
            String[] parts = body.split("---------------------------------------------------------");
            for (String part : parts) {
                if (part.contains("----------------------------------------")) {
                    return true;
                }
                String[] details = part.split("\r\n");

                JobListing job = new JobListing();
                int index = StringUtils.isEmpty(details[1].trim())? 3: 1;
                job.setTitle(details[index++]); // 1,3
                job.setCompany(details[index++]); // 2,4
                job.setLocation(details[index++]); // 3,5
                job.setLink(details[details.length-1].split("View job: ")[1]);
                job.setSource(subject);
                job.setSourceDate(date);

                jobs.add(job);
            }
        } catch(Throwable t) {
            t.printStackTrace();
        }
        return true;
    }

    private boolean isTargetRole(JobListing job) {
        String t = (job.getTitle() + " " + job.getCompany()).toLowerCase();
        return t.contains("java") || t.contains("spring") || t.contains("backend") || t.contains("microservice");
    }

    private JobListing parseJobFromEmail(String subject, String body, String date) {
        JobListing job = new JobListing();
        job.setTitle(clean(subject));
        job.setSourceDate(date);

        // Extract company
        String company = extractCompany(subject, body);
        job.setCompany(company.isEmpty() ? "Unknown" : company);

        // Extract link
        String link = extractApplyLink(body);
        job.setLink(link);

        populateJobFields(job, body);

        return job;
    }

    private String extractCompany(String subject, String body) {
        if (subject.contains(" at ")) {
            return subject.substring(subject.indexOf(" at ") + 4).trim();
        }
        if (body.toLowerCase().contains("company:")) {
            int idx = body.toLowerCase().indexOf("company:");
            return body.substring(idx + 8, Math.min(idx + 50, body.length())).trim();
        }
        return "";
    }

    private String extractApplyLink(String body) {
        if (body.contains("http")) {
            int start = body.indexOf("http");
            int end = body.indexOf(" ", start);
            if (end == -1) end = Math.min(start + 250, body.length());
            return body.substring(start, end).trim().replaceAll("[<>]", "");
        }
        return "";
    }

    private String extractCompensation(String body) {
        String lower = body.toLowerCase();
        if (lower.contains("$") || lower.contains("cad") || lower.contains("usd")) {
            int idx = Math.max(lower.indexOf("$"), Math.max(lower.indexOf("cad"), lower.indexOf("usd")));
            return body.substring(idx, Math.min(idx + 30, body.length())).trim();
        }
        return "";
    }

    private String extractEmploymentType(String body) {
        String lower = body.toLowerCase();
        if (lower.contains("full-time") || lower.contains("permanent")) return "Full-time / Permanent";
        if (lower.contains("contract")) return "Contract";
        return "";
    }

    private String extractRemoteWording(String body) {
        String lower = body.toLowerCase();
        if (lower.contains("fully remote")) return "Fully remote";
        if (lower.contains("100% remote")) return "100% remote";
        if (lower.contains("work from anywhere")) return "Work from anywhere";
        if (lower.contains("permanent work from home")) return "Permanent WFH";
        if (lower.contains("remote within canada")) return "Remote within Canada";
        return "";
    }

    private String extractStack(String body) {
        String lower = body.toLowerCase();
        List<String> tech = new ArrayList<>();
        if (lower.contains("spring boot")) tech.add("Spring Boot");
        if (lower.contains("microservices")) tech.add("Microservices");
        if (lower.contains("aws") || lower.contains("azure") || lower.contains("gcp")) tech.add("Cloud");
        if (lower.contains("kubernetes")) tech.add("Kubernetes");
        return tech.isEmpty() ? "" : String.join(", ", tech);
    }

    private List<JobListing> deduplicateJobs(List<JobListing> jobs) {
        Map<String, JobListing> unique = new LinkedHashMap<>();
        for (JobListing job : jobs) {
            String key = (job.getTitle() + "|" + job.getCompany() + "|" + job.getLink()).toLowerCase();
            if (!unique.containsKey(key) || job.getSourceDate().compareTo(unique.get(key).getSourceDate()) > 0) {
                unique.put(key, job);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private List<JobListing> verifyLiveStatus(List<JobListing> jobs) {

        List<String> error = fileService.readRows(filePrefix +".error.txt");
        List<String> closed = fileService.readRows(filePrefix +".closed.txt");
        List<String> skip = fileService.readRows(filePrefix +".skip.txt");
        List<String> active = fileService.readRows(filePrefix +".active.txt");

        List<JobListing> result = new ArrayList<>();

        int errorcount = 0;
        int errorsleep = 30;
        int processed = 30;

        String jobid = "", link = "";

        System.out.println("Verifying jobs: "+jobs.size());


        for (JobListing job : jobs) {
            try {
                processed++;

                if (job.getLink() == null || job.getLink().isEmpty()) {
                    job.setStatus("Unverified");
                    continue;
                }

                jobid = null;
                link = job.getLink();

                jobid = extractJobId(link);
                if( jobid == null ){
                    System.out.println("Skip: " + link);
                    skip.add(link);
                    fileService.saveRows(skip, filePrefix +".skip.txt");
                    continue;
                }


                if( closed.contains(jobid) ){
                    job.setStatus("Closed");
                    System.out.println("Already processed: Closed: "+jobid+": "+link);
                    continue;
                } else if( active.contains(jobid) ){
                    job.setStatus("Active");
                    System.out.println("Already processed: Active: "+jobid+": "+link);
                    continue;
                } else if( error.contains(jobid) ){
                    job.setStatus("Unverified");
                    System.out.println("Already processed: Error: "+jobid+": "+link);
                    continue;
                }

                Document doc = Jsoup.connect("https://www.linkedin.com/jobs/view/"+jobid)
                        .userAgent("Mozilla/5.0")
                        .timeout(30000)
                        .get();

                String text = doc.text().toLowerCase();

                populateJobFields(job, text);

                if (text.contains("application closed") || text.contains("position filled") ||
                    text.contains("no longer accepting") || text.contains("job not found") ||
                        text.contains("No longer accepting applications")) {
                    job.setStatus("Closed");
                    closed.add(jobid);
                } else if (doc.select("form, button, a[href*=apply]").size() > 0 ||
                           text.contains("apply now") || text.contains("submit application")) {
                    job.setStatus("Active");
                    result.add(job);
                    active.add(jobid);
                } else {
                    job.setStatus("Unverified");
                }

                errorcount = 0;

                try {
                    TimeUnit.SECONDS.sleep(errorsleep);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Error: "+link);

                job.setStatus("Unverified");
                errorsleep += 30;

                error.add(jobid);

                fileService.saveRows(error, filePrefix +".error.txt");
                fileService.saveRows(closed, filePrefix +".closed.txt");
                fileService.saveRows(active, filePrefix +".active.txt");

                try {
                    TimeUnit.SECONDS.sleep(errorsleep);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
                if( errorcount++ > 2 ) {
                    break;
                }
            } finally {
                System.out.println("Processed jobs: "+processed+" out of "+jobs.size());
            }
        }

        fileService.saveRows(closed, filePrefix +".closed.txt");
        fileService.saveRows(active, filePrefix +".active.txt");

        return result;
    }

    private String extractJobId(String link) {
        String[] linksplit = link.split("jobid_|_ssid");
        if( linksplit.length < 2 ){
            linksplit = link.split("/view/|/\\?");
            if( linksplit.length < 2 ) {
                return null;
            }
        }
        return linksplit[1];
    }

    private void populateJobFields(JobListing job, String text) {
        // Extract body
        job.setRawBody(text);

        // Extract compensation
        String comp = extractCompensation(text);
        job.setCompensation(comp.isEmpty() ? "Not stated" : comp);

        // Extract employment type
        String empType = extractEmploymentType(text);
        job.setEmploymentType(empType.isEmpty() ? "Not stated" : empType);

        // Extract remote wording
        String remoteWording = extractRemoteWording(text);
        job.setRemoteWording(remoteWording);

        // Extract stack
        String stack = extractStack(text);
        job.setKeyStack(stack.isEmpty() ? "Not stated" : stack);
    }

    private List<JobListing> filterFullyRemote(List<JobListing> jobs) {
        return jobs.stream()
                .filter(j -> j.getRemoteWording() != null && !j.getRemoteWording().isEmpty())
                .filter(j -> !j.getRemoteWording().toLowerCase().contains("hybrid"))
                .collect(Collectors.toList());
    }

    private List<JobListing> rankJobs(List<JobListing> jobs) {
        List<JobListing> traditional = new ArrayList<>();
        List<JobListing> secondary = new ArrayList<>();

        for (JobListing job : jobs) {
            String t = (job.getTitle() + " " + job.getKeyStack()).toLowerCase();
            if (t.contains("ai") || t.contains("training") || t.contains("freelance") || t.contains("evaluation")) {
                secondary.add(job);
            } else {
                traditional.add(job);
            }
        }

        traditional.sort((a, b) -> compareRelevance(b, a));
        secondary.sort((a, b) -> compareRelevance(b, a));

        List<JobListing> ranked = new ArrayList<>(traditional);
        ranked.addAll(secondary);
        return ranked;
    }

    private int compareRelevance(JobListing a, JobListing b) {
        int scoreA = scoreJob(a);
        int scoreB = scoreJob(b);
        return Integer.compare(scoreA, scoreB);
    }

    private int scoreJob(JobListing job) {
        int score = 0;
        String t = job.getTitle().toLowerCase();
        if (t.contains("senior")) score += 3;
        if (job.getKeyStack().contains("Spring Boot")) score += 2;
        if (job.getKeyStack().contains("Microservices")) score += 2;
        if (!job.getCompensation().equals("Not stated")) score += 2;
        if (job.getEmploymentType().contains("Permanent")) score += 1;
        return score;
    }

    private void sendDigestEmail(List<JobListing> jobs, int lookbackDays, int totalReviewed) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject("Weekly fully remote Java jobs — " + LocalDate.now());

            StringBuilder body = new StringBuilder();
            body.append("Hi,\n\n");

            // Search summary
            body.append("**Search summary**\n");
            body.append("- Execution date: ").append(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)).append("\n");
            body.append("- Gmail lookback: ").append(lookbackDays).append(" days\n");
            body.append("- Candidate messages reviewed: ").append(totalReviewed).append("\n");
            body.append("- Active fully remote matches: ").append(jobs.size()).append("\n\n");

            if (jobs.isEmpty()) {
                body.append("No active unambiguously fully remote Java roles passed all filters this week.\n");
                body.append("Closest candidates were eliminated due to: hybrid wording, closed listings, or unverifiable status.\n\n");
            } else {
                // Best matches - Traditional
                body.append("**Best matches — Traditional Java roles**\n\n");
                int rank = 1;
                for (JobListing job : jobs) {
                    if (rank > 8) break; // keep concise
                    body.append(rank++).append(". **").append(job.getTitle()).append("**  \n");
                    body.append("   Company: ").append(job.getCompany()).append("  \n");
                    body.append("   Status: ").append(job.getStatus()).append("  \n");
                    body.append("   Compensation: ").append(job.getCompensation()).append("  \n");
                    body.append("   Employment: ").append(job.getEmploymentType()).append("  \n");
                    body.append("   Remote: ").append(job.getRemoteWording()).append("  \n");
                    body.append("   Stack: ").append(job.getKeyStack()).append("  \n");
                    body.append("   Source: ").append(job.getSourceDate()).append("  \n");
                    body.append("   Link: ").append(job.getLink()).append("\n\n");
                }
            }

            body.append("\n**Methodology note**: Roles verified against live employer/ATS pages. Only Active + unambiguous fully remote roles included. Traditional engineering roles ranked first.\n");
            body.append("Never apply automatically. No personal data shared.");

            helper.setText(body.toString(), false);
            mailSender.send(message);

        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send digest email", e);
        }
    }

    private String getHeader(com.google.api.services.gmail.model.Message message, String name) {
        for (var header : message.getPayload().getHeaders()) {
            if (header.getName().equalsIgnoreCase(name)) return header.getValue();
        }
        return "";
    }

    private String getEmailBody(com.google.api.services.gmail.model.Message message) {
        try {
            if (message.getPayload().getParts() != null) {
                for (var part : message.getPayload().getParts()) {
                    if (part.getBody().getData() != null) {
                        if( "text/plain".equals(part.getMimeType()) ){ // skip html
                            return new String(java.util.Base64.getUrlDecoder().decode(part.getBody().getData()));
                        }
                    }
                }
            } else if ( "text/plain".equals(message.getPayload().getMimeType()) ) {
                return new String(java.util.Base64.getUrlDecoder().decode(message.getPayload().getBody().getData()));
            } else if ( "text/html".equals(message.getPayload().getMimeType()) ) {
                return null;
                // return new String(java.util.Base64.getUrlDecoder().decode(message.getPayload().getBody().getData()));
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String clean(String s) {
        return s == null ? "" : s.replaceAll("[\\r\\n]+", " ").trim();
    }
}