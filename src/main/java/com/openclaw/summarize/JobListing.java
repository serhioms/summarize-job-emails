package com.openclaw.summarize;

public class JobListing {

    private String title;
    private String company;
    private String location;
    private String link;
    private boolean isRemote;
    private String source;
    private String sourceDate;
    private String rawBody;
    private String compensation = "Not stated";
    private String employmentType = "Not stated";
    private String remoteWording;
    private String keyStack;
    private String status = "Unverified";

    // Getters and setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public boolean isRemote() { return isRemote; }
    public void setRemote(boolean remote) { isRemote = remote; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getSourceDate() { return sourceDate; }
    public void setSourceDate(String sourceDate) { this.sourceDate = sourceDate; }

    public String getRawBody() { return rawBody; }
    public void setRawBody(String rawBody) { this.rawBody = rawBody; }

    public String getCompensation() { return compensation; }
    public void setCompensation(String compensation) { this.compensation = compensation; }

    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }

    public String getRemoteWording() { return remoteWording; }
    public void setRemoteWording(String remoteWording) { this.remoteWording = remoteWording; }

    public String getKeyStack() { return keyStack; }
    public void setKeyStack(String keyStack) { this.keyStack = keyStack; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}