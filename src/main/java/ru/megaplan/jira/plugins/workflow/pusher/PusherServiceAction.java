package ru.megaplan.jira.plugins.workflow.pusher;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created with IntelliJ IDEA.
 * User: i.loskutov
 * Date: 06.06.12
 * Time: 13:14
 * To change this template use File | Settings | File Templates.
 */
public class PusherServiceAction extends JiraWebActionSupport {

    private static final Logger log = Logger.getLogger(PusherServiceAction.class);

    private final PusherService pusherService;
    private final UserManager userManager;
    private final CustomFieldManager customFieldManager;


    private String[] jobIds;
    private String[] queries;
    private String[] users;
    private String[] customFields;
    private long[] intervals;
    private int[] params;

    private String[] deletes;

    public String[] getDeletes() {
        return deletes;
    }

    public void setDeletes(String[] deletes) {
        this.deletes = deletes;
    }

// sranoe govno

    private List ljobIds;
    private List lqueries;
    private List lusers;
    private List lcustomFields;
    private List lintervals;
    private List lparams;



    public List getLjobIds() {
        return ljobIds;
    }

    public List getLqueries() {
        return lqueries;
    }

    public List getLusers() {
        return lusers;
    }

    public List getLcustomFields() {
        return lcustomFields;
    }

    public List getLintervals() {
        return lintervals;
    }

    public List getLparams() {
        return lparams;
    }

    private String newJobId;
    private String newQuery;
    private String newUser;
    private String newCustomField;
    private long newInterval;
    private int newParam;

    PusherServiceAction(PusherService pusherService, UserManager userManager, CustomFieldManager customFieldManager) {
        this.pusherService = checkNotNull(pusherService);
        this.userManager = checkNotNull(userManager);
        this.customFieldManager = checkNotNull(customFieldManager);
    }

    @Override
    public String doExecute() throws Exception {
        return doDefault();
    }

    @Override
    public String doDefault() throws Exception {
        try {
            Set<String> jids = pusherService.getJobIds();
            int size = jids.size();
            jobIds = pusherService.getJobIds().toArray(new String[size]);
            queries = new String[size];
            users = new String[size];
            params = new int[size];
            intervals = new long[size];
            customFields = new String[size];
            for (int i = 0; i < size; i++) {
                PusherService.Settings settings = pusherService.getSettings(jobIds[i]);
                queries[i] = settings.getQuery();
                users[i] = settings.getUser() == null?"":settings.getUser().getName();
                params[i] = settings.getParam();
                intervals[i] = settings.getInterval();
                customFields[i] = settings.getCustomField() == null?"":settings.getCustomField().getName();
            }
            lqueries = Arrays.asList(queries);
            lusers = Arrays.asList(users);
            List<String> sparams = new ArrayList<String>(); //  oh boy
            for (int p : params) {
                sparams.add(Integer.toString(p));
            }
            lparams = sparams;
            List<String> sintervals = new ArrayList<String>(); // oh my
            for (long i : intervals) {
                sintervals.add(Long.toString(i));
            }
            lintervals = sintervals;
            lcustomFields = Arrays.asList(customFields);
        } catch (Exception e) {
            log.warn(e);
            pusherService.deleteJobs();
        }

        return SUCCESS;
    }

    public String doReschedule() {
        log.warn("job ids : " + (jobIds == null));
        if (jobIds != null) log.warn(Arrays.toString(jobIds));
        if (jobIds != null) {
            for (int i = 0; i < jobIds.length; i++) {
                String jobId = jobIds[i];
                String delete = deletes[i];
                log.warn("delete : " + delete);
                if ("true".equals(delete)) {
                    pusherService.deleteJob(jobId);
                } else {
                    PusherService.Settings settings = getSettings(i);
                    if (!settings.isLegal()) {
                        log.warn("settings for : " +jobId + " is illegal");
                        continue;
                    }
                    pusherService.reschedule(jobId, settings);
                }
            }
        }

        if (newJobId != null && !newJobId.isEmpty()) {
            pusherService.schedule(newJobId, getNewSettings());
        }

        return getRedirect("PusherServiceAction!default.jspa");
    }

    private PusherService.Settings getSettings(int i) {
        long interval = intervals[i];
        int param = params[i];
        String query = queries[i];
        User user = userManager.getUser(users[i]);
        CustomField customField = null;
        if (customFields[i] != null)
            customField = customFieldManager.getCustomFieldObjectByName(customFields[i]);
        return pusherService.createSettingsObject(interval, param, query, user, customField);
    }

    private PusherService.Settings getNewSettings() {
        long interval = newInterval;
        int param = newParam;
        String query = newQuery;
        User user = userManager.getUser(newUser);
        CustomField customField = null;
        if (newCustomField != null)
            customField = customFieldManager.getCustomFieldObjectByName(newCustomField);
        return pusherService.createSettingsObject(interval, param, query, user, customField);
    }

    public String[] getJobIds() {
        return jobIds;
    }

    public void setJobIds(String[] jobIds) {
        this.jobIds = jobIds;
    }

    public String[] getQueries() {
        return queries;
    }

    public void setQueries(String[] queries) {
        this.queries = queries;
    }

    public String[] getUsers() {
        return users;
    }

    public void setUsers(String[] users) {
        this.users = users;
    }

    public String[] getCustomFields() {
        return customFields;
    }

    public void setCustomFields(String[] customFields) {
        this.customFields = customFields;
    }

    public long[] getIntervals() {
        return intervals;
    }

    public void setIntervals(long[] intervals) {
        this.intervals = intervals;
    }

    public int[] getParams() {
        return params;
    }

    public void setParams(int[] params) {
        this.params = params;
    }

    public String getNewJobId() {
        return newJobId;
    }

    public void setNewJobId(String newJobId) {
        this.newJobId = newJobId;
    }

    public String getNewQuery() {
        return newQuery;
    }

    public void setNewQuery(String newQuery) {
        this.newQuery = newQuery;
    }

    public String getNewUser() {
        return newUser;
    }

    public void setNewUser(String newUser) {
        this.newUser = newUser;
    }

    public String getNewCustomField() {
        return newCustomField;
    }

    public void setNewCustomField(String newCustomField) {
        this.newCustomField = newCustomField;
    }

    public long getNewInterval() {
        return newInterval;
    }

    public void setNewInterval(long newInterval) {
        this.newInterval = newInterval;
    }

    public int getNewParam() {
        return newParam;
    }

    public void setNewParam(int newParam) {
        this.newParam = newParam;
    }
}
