package ru.megaplan.jira.plugins.workflow.pusher;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.fields.CustomField;

import java.util.Collection;
import java.util.Date;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: i.loskutov
 * Date: 06.06.12
 * Time: 12:40
 * To change this template use File | Settings | File Templates.
 */
public interface PusherService {
    //String getQuery();
    //long getInterval();
    //String getCustomField();
    //void setCustomField(String name);
    //int getParam();
    //User getUser();
    Collection<Issue> getIssuesFromQuery(User u, String query);
    Settings getSettings(String jobId);
    Settings createSettingsObject(long interv, int param, String query, User user, CustomField customField);
    Set<String> getJobIds();

    void reschedule(String jobId, PusherServiceImpl.Settings s);

    void schedule(String jobId, Settings s);

    void deleteJobs();

    void deleteJob(String jobId);

    public interface Settings {
        public long getInterval();

        public int getParam();

        public String getQuery();

        public User getUser();

        public CustomField getCustomField();

        public boolean isLegal();
    }
}
