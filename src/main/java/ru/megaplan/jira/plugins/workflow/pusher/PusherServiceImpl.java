package ru.megaplan.jira.plugins.workflow.pusher;

import com.atlassian.core.util.collection.EasyList;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.bc.workflow.WorkflowService;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.context.GlobalIssueContext;
import com.atlassian.jira.issue.customfields.CustomFieldType;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.jira.workflow.IssueWorkflowManager;
import com.atlassian.query.Query;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.atlassian.sal.api.scheduling.PluginScheduler;
import org.apache.log4j.Logger;
import org.ofbiz.core.entity.GenericEntityException;

import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created with IntelliJ IDEA.
 * User: i.loskutov
 * Date: 06.06.12
 * Time: 12:40
 * To change this template use File | Settings | File Templates.
 */
public class PusherServiceImpl implements PusherService, LifecycleAware {

    private static final Logger log = Logger.getLogger(PusherServiceImpl.class);

    static final String ME = "ME";
    static final String JOBID = "jobid";
    static final String SETTINGS = "settings";
    static final String PARAM = "param";
    static final String USER = "user";
    static final String QUERY = "query";
    static final String INTERVAL = "interval";
    static final String CUSTOMFIELD = "customfield";
    static final String CLOSEDBYBOT = "ClosedByBot";

    static final String SYSTEMREADONLYCF = "com.atlassian.jira.plugin.system.customfieldtypes:readonlyfield";


    private static final String JOB_NAME = PusherServiceImpl.class.getName() + ":job";

    private final PluginScheduler pluginScheduler;
    private final SearchService searchService;
    private final JiraAuthenticationContext jiraAuthenticationContext;
    private final IssueService issueService;
    private final IssueWorkflowManager issueWorkflowManager;
    private final PusherSettings pusherSettings;
    private final UserManager userManager;
    private final CustomFieldManager customFieldManager;
    private final IssueManager issueManager;
    private final User defaultUser;

    public PusherServiceImpl(PluginScheduler pluginScheduler,
                             SearchService searchService,
                             IssueManager issueManager,
                             JiraAuthenticationContext jiraAuthenticationContext,
                             IssueWorkflowManager issueWorkflowManager,
                             IssueService issueService,
                             PusherSettings pusherSettings,
                             UserManager userManager,
                             CustomFieldManager customFieldManager
                             ) {

         this.pluginScheduler = checkNotNull(pluginScheduler);
         this.searchService = checkNotNull(searchService);   // try use SearchProvider here instead oki?
        this.issueWorkflowManager = issueWorkflowManager;
        this.jiraAuthenticationContext = jiraAuthenticationContext;
        this.issueService = issueService;
        this.pusherSettings = pusherSettings;
        this.userManager = userManager;
        this.customFieldManager = customFieldManager;
        this.issueManager = issueManager;

        //set def user
        defaultUser = userManager.getUser("megaplan");

    }

    // jobId, Settings
    private Map<String, Settings> settings = new HashMap<String, Settings>();

    public Set<String> getJobIds() {
        String idsS = pusherSettings.getValue("WorkflowPusherJobs");
        if (idsS == null || idsS.isEmpty()) {
            return new HashSet<String>();
        } else {
            return new HashSet<String>(Arrays.asList(idsS.split("\\|")));
        }

    }

    public class SettingsImpl implements PusherService.Settings {

        private SettingsImpl(long interval, int param, String query, User user, CustomField customField) {
            this.interval = interval;
            this.param = param;
            this.query = query;
            this.user = user;
            this.customField = customField;

            if (!isLegal()) {
                throw new IllegalArgumentException();
            }
            log.warn("created settings object : " + this);
        }

        public long getInterval() {
            return interval;
        }

        public int getParam() {
            return param;
        }

        public String getQuery() {
            return query;
        }

        public User getUser() {
            return user;
        }

        public CustomField getCustomField() {
            return customField;
        }

        private final long interval;
        private final int param;
        private final String query;
        private final User user;
        private final CustomField customField;

        public boolean isLegal() {
            if (param == 0 || query == null || query.isEmpty() || user == null || interval == 0) {
                return false;
            } else {
                return true;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SettingsImpl settings = (SettingsImpl) o;

            if (interval != settings.interval) return false;
            if (param != settings.param) return false;
            if (customField != null ? !customField.equals(settings.customField) : settings.customField != null)
                return false;
            if (query != null ? !query.equals(settings.query) : settings.query != null) return false;
            if (user != null ? !user.equals(settings.user) : settings.user != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = (int) (interval ^ (interval >>> 32));
            result = 31 * result + param;
            result = 31 * result + (query != null ? query.hashCode() : 0);
            result = 31 * result + (user != null ? user.hashCode() : 0);
            result = 31 * result + (customField != null ? customField.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "SettingsImpl{" +
                    "interval=" + interval +
                    ", param=" + param +
                    ", query='" + query + '\'' +
                    ", user=" + user +
                    ", customField=" + customField +
                    '}';
        }
    }

    @Override
    public void onStart() {

        Set<String> jobIds = getJobIds();

        for (String jobId : jobIds) {
            try {
                Settings jobSettings = getSettings(jobId);
                settings.put(jobId, jobSettings);
                log.warn("rescheduling job " + jobId);
                reschedule(jobId, jobSettings, false, true);
            } catch (Exception e) {
                log.error("exception in job : " + jobId, e);
                settings.remove(jobId);
            }
        }
    }

    public Settings getSettings(String jobId) {

        JobSettingObtainer jso = new JobSettingObtainer(jobId);

        log.warn("test query : " + jso.get(QUERY));

        long interv = Long.parseLong(jso.get(INTERVAL));
        int param = Integer.parseInt(jso.get(PARAM));
        User user = userManager.getUser(jso.get(USER));
        String query = jso.get(QUERY);

        String cusf = jso.get(CUSTOMFIELD);
        CustomField customField = null;
        if (cusf != null) customField = customFieldManager.getCustomFieldObjectByName(cusf);

        return createSettingsObject(interv, param, query, user, customField);

    }

    public Settings createSettingsObject(long interv, int param, String query, User user, CustomField customField) {
        return new SettingsImpl(interv, param, query, user, customField);
    }


    // use this as context with jobId and pretty 'get' method
    private class JobSettingObtainer {
        public final String jobId;
        public JobSettingObtainer(String jobId) {
            this.jobId = jobId;
        }
        private String get(String settingName) {
            return pusherSettings.getValue(storeName(settingName));
        }
        private void set(String settingName, String settingValue) {
            pusherSettings.setValue(storeName(settingName), settingValue);
        }
        private String storeName(String settingName) {
            return JOB_NAME+":"+jobId+":"+settingName;
        }
    }

    @Override
    public void deleteJobs() {
        pusherSettings.setValue("WorkflowPusherJobs", null);
    }

    @Override
    public void deleteJob(String jobId) {
        Set<String> ids = getJobIds();
        ids.remove(jobId);
        pusherSettings.setValue("WorkflowPusherJobs", buildJobIdsString(ids));
        JobSettingObtainer jso = new JobSettingObtainer(jobId);
        jso.set(QUERY, null);
        jso.set(USER, null);
        jso.set(PARAM, null);
        jso.set(INTERVAL, null);
        jso.set(CUSTOMFIELD, null);
        pluginScheduler.unscheduleJob(JOB_NAME+":"+jobId);
    }

    @Override
    public void reschedule(String jobId, Settings s) {
        reschedule(jobId, s, false, false);
    }

    @Override
    public void schedule(String jobId, Settings s) {
        reschedule(jobId, s, true, false);
    }

    public void reschedule(String jobId, Settings s, boolean isNew, boolean init) {

        if (!s.isLegal()) {
            log.error("illegal arguments for job: " + jobId);
            return;
        }

        if (!isNew && !init) {
            Settings oldSettings = getSettings(jobId);
            if (oldSettings != null && oldSettings.equals(s)) {
                log.warn("settings wasn't changed");
                return;
            }
        }


        if (isNew) {
            addJobId(jobId);
        }

        storeSettings(jobId, s);
        log.warn("settings stored");

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("jiraAuthenticationContext", jiraAuthenticationContext);
        params.put("issueService", issueService);
        params.put("issueWorkflowManager", issueWorkflowManager);
        params.put("customFieldManager", customFieldManager);
        params.put("issueManager", issueManager);

        params.put(ME, this);

        params.put(JOBID, jobId);

        params.put(SETTINGS, s);

        pluginScheduler.scheduleJob(JOB_NAME+":"+jobId,
                SimplePushJob.class,
                params,
                new Date(),
                s.getInterval());
        log.warn("scheduled");
    }

    private void addJobId(String jobId) {
        Set<String> jobIds = getJobIds();
        jobIds.add(jobId);

        pusherSettings.setValue("WorkflowPusherJobs", buildJobIdsString(jobIds));

    }

    private String buildJobIdsString(Set<String> jobIds) {
        StringBuilder shitBuilder = new StringBuilder();
        for (String jid : jobIds) {
            if (shitBuilder.length() != 0) {
                shitBuilder.append("|");
            }
            shitBuilder.append(jid);
        }


        return shitBuilder.toString();
    }

    private void storeSettings(String jobId, Settings s) {
        JobSettingObtainer jso = new JobSettingObtainer(jobId);
        jso.set(QUERY, s.getQuery());
        jso.set(USER, s.getUser().getName());
        jso.set(PARAM, Integer.toString(s.getParam()));
        jso.set(INTERVAL, Long.toString(s.getInterval()));
        jso.set(CUSTOMFIELD, s.getCustomField()==null?"":s.getCustomField().getName());
    }


//    @Override
//    public Date getLastRun() {
//        return lastRun;
//    }
//
//    @Override
//    public void setLastRun(Date date) {
//        this.lastRun = date;
//    }
//
//    @Override
//    public String getQuery() {
//        return query;
//    }
//
//    @Override
//    public long getInterval() {
//        return interval;
//    }
//
//    @Override
//    public String getCustomField() {
//        return customField==null?"":customField.getName();
//    }
//
//    @Override
//    public void setCustomField(String name) {
//        customField = customFieldManager.getCustomFieldObject(name);
//    }
//
//    @Override
//    public int getParam() {
//        return param;
//    }
//
//    @Override
//    public User getUser() {
//        return user;
//    }

    public Collection<Issue> getIssuesFromQuery(User user, String jqlQuery) {
        Collection<Issue> issues = null;
        SearchService.ParseResult parseResult = searchService.
        parseQuery(user, jqlQuery);
        if (parseResult.isValid()) {
            Query query = parseResult.getQuery();
            try {
                SearchResults results = searchService.search(user, query,
                        PagerFilter.getUnlimitedFilter());
                issues = results.getIssues();
            } catch (SearchException e) {
                e.printStackTrace();
            }
        }
        return (issues == null)?new ArrayList<Issue>():issues;
    }
}
