package com.openclaw.summarize;

public class JobListing {

    private String title;
    private String company;
    private String location;
    private String link;
    private boolean isRemote;
    private String source;

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
}